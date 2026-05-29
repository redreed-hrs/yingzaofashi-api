package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import com.example.yingzaofashiapi.util.BuildingTypeExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 增强型聚类服务 —— 分层空间约束 WKmeans
 * <p>
 *     1. 按精确地理分区将遗产点分组，各区内独立聚类，杜绝跨区域混合。
 *     2. 使用加权 K-Means++ 算法（β=3）自动学习特征权重，抑制噪音维度。
 *     3. 标签以区域冠名，确保方位名词学术准确（东北、华北、华东、华中、华南、西南、西北、青藏）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EnhancedClusteringService {

    private final HeritageService heritageService;
    private final BudgetService budgetService;

    // 精确地理分区定义（基于省级行政区）
    private static final Map<String, String> PROVINCE_REGION = new HashMap<>();
    static {
        // 华北
        PROVINCE_REGION.put("北京市", "华北"); PROVINCE_REGION.put("天津市", "华北");
        PROVINCE_REGION.put("河北省", "华北"); PROVINCE_REGION.put("山西省", "华北");
        PROVINCE_REGION.put("内蒙古自治区", "华北");
        // 东北
        PROVINCE_REGION.put("辽宁省", "东北"); PROVINCE_REGION.put("吉林省", "东北");
        PROVINCE_REGION.put("黑龙江省", "东北");
        // 华东
        PROVINCE_REGION.put("上海市", "华东"); PROVINCE_REGION.put("江苏省", "华东");
        PROVINCE_REGION.put("浙江省", "华东"); PROVINCE_REGION.put("安徽省", "华东");
        PROVINCE_REGION.put("福建省", "华东"); PROVINCE_REGION.put("江西省", "华东");
        PROVINCE_REGION.put("山东省", "华东");
        // 华中
        PROVINCE_REGION.put("河南省", "华中"); PROVINCE_REGION.put("湖北省", "华中");
        PROVINCE_REGION.put("湖南省", "华中");
        // 华南
        PROVINCE_REGION.put("广东省", "华南"); PROVINCE_REGION.put("广西壮族自治区", "华南");
        PROVINCE_REGION.put("海南省", "华南");
        // 西南
        PROVINCE_REGION.put("重庆市", "西南"); PROVINCE_REGION.put("四川省", "西南");
        PROVINCE_REGION.put("贵州省", "西南"); PROVINCE_REGION.put("云南省", "西南");
        PROVINCE_REGION.put("西藏自治区", "青藏"); // 西藏归入青藏高原区
        // 西北
        PROVINCE_REGION.put("陕西省", "西北"); PROVINCE_REGION.put("甘肃省", "西北");
        PROVINCE_REGION.put("青海省", "青藏"); // 青海归入青藏
        PROVINCE_REGION.put("宁夏回族自治区", "西北"); PROVINCE_REGION.put("新疆维吾尔自治区", "西北");
        // 青藏高原单独成区（西藏+青海）
        PROVINCE_REGION.put("西藏自治区", "青藏");
        PROVINCE_REGION.put("青海省", "青藏");
        // 港澳台
        PROVINCE_REGION.put("香港特别行政区", "华南"); PROVINCE_REGION.put("澳门特别行政区", "华南");
        PROVINCE_REGION.put("台湾省", "华东");
    }

    // 区域间的就近合并关系（当某区域点数过少时合并到地理最近区域）
    private static final Map<String, String> REGION_MERGE_TARGET = new HashMap<>();
    static {
        REGION_MERGE_TARGET.put("青藏", "西北");
        REGION_MERGE_TARGET.put("东北", "华北");
        // 其他区域若点太少可直接合并到邻近大区，可按需扩展
    }

    // 辅助变量（仅用于日志和指标）
    private double lastSSE = 0;
    private double lastSilhouette = 0;
    private double lastDBIndex = 0;

    // ------------- 对外接口 -------------
    @Cacheable(value = "enhancedClusters", key = "#k", unless = "#result == null")
    public List<HeritagePointDTO> getHeritagePointsWithEnhancedCluster(int k) {
        List<HeritagePointDTO> allPoints = heritageService.getAllHeritagePoints();
        if (allPoints == null || allPoints.isEmpty()) return Collections.emptyList();

        // 1. 按地理区域分组
        Map<String, List<HeritagePointDTO>> regionGroups = groupByRegion(allPoints);

        // 2. 合并过小区域
        mergeSmallRegions(regionGroups, 5);

        // 3. 为每个区域分配簇数（若k>0）
        Map<String, Integer> regionKMap = distributeK(regionGroups, k);

        // 4. 各区域独立聚类并生成标签
        List<HeritagePointDTO> result = new ArrayList<>();
        double totalSSE = 0, totalSil = 0, totalDBI = 0;
        int regionCount = 0;

        for (Map.Entry<String, List<HeritagePointDTO>> entry : regionGroups.entrySet()) {
            String region = entry.getKey();
            List<HeritagePointDTO> points = entry.getValue();
            if (points.size() <= 1) {
                // 单点簇，直接标记为区域·综合
                for (HeritagePointDTO p : points) {
                    p.setClusterName(region + "-孤点遗产");
                }
                result.addAll(points);
                continue;
            }

            int bestK = regionKMap.getOrDefault(region, determineOptimalKInRegion(points));
            if (bestK > points.size()) bestK = points.size();
            if (bestK < 1) bestK = 1;

            // 构建特征向量并执行加权K-Means++
            List<double[]> vectors = buildFeatureVectors(points);
            int[] labels = weightedKMeansPlusPlus(vectors, bestK);

            // 计算局部质量指标
            double[][] centroids = computeCentroids(vectors, labels, bestK);
            double[] uniformWeights = new double[vectors.get(0).length];
            Arrays.fill(uniformWeights, 1.0 / uniformWeights.length);
            totalSSE += computeSSE(vectors, labels, centroids, uniformWeights);
            totalSil += silhouetteScore(vectors, labels, uniformWeights);
            totalDBI += daviesBouldinIndex(vectors, labels, centroids, uniformWeights);
            regionCount++;

            // 生成语义标签（区域+风格）
            Map<Integer, String> labelMap = generateSemanticLabels(points, labels, region);
            for (int i = 0; i < points.size(); i++) {
                points.get(i).setClusterName(labelMap.get(labels[i]));
            }
            result.addAll(points);
        }

        // 全局平均指标
        if (regionCount > 0) {
            lastSSE = totalSSE;
            lastSilhouette = totalSil / regionCount;
            lastDBIndex = totalDBI / regionCount;
        }
        return result;
    }

    @CacheEvict(value = "enhancedClusters", allEntries = true)
    public void clearEnhancedClusterCache() {}

    public Map<String, Double> getLastQualityMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("sse", lastSSE);
        metrics.put("silhouette", lastSilhouette);
        metrics.put("dbIndex", lastDBIndex);
        return metrics;
    }

    // ======================== 地理分组与合并 ========================
    private Map<String, List<HeritagePointDTO>> groupByRegion(List<HeritagePointDTO> points) {
        Map<String, List<HeritagePointDTO>> map = new LinkedHashMap<>();
        for (HeritagePointDTO p : points) {
            String province = p.getProvince();
            String region = PROVINCE_REGION.getOrDefault(province, "其他");
            map.computeIfAbsent(region, k -> new ArrayList<>()).add(p);
        }
        return map;
    }

    private void mergeSmallRegions(Map<String, List<HeritagePointDTO>> groups, int minSize) {
        List<String> toMerge = new ArrayList<>();
        for (Map.Entry<String, List<HeritagePointDTO>> e : groups.entrySet()) {
            if (e.getValue().size() < minSize) {
                toMerge.add(e.getKey());
            }
        }
        for (String small : toMerge) {
            List<HeritagePointDTO> pts = groups.remove(small);
            String target = REGION_MERGE_TARGET.get(small);
            if (target == null || !groups.containsKey(target)) {
                // 找最近的非空区域（简易版：选点数最多的区域）
                target = groups.entrySet().stream()
                        .max(Comparator.comparingInt(e -> e.getValue().size()))
                        .map(Map.Entry::getKey).orElse("华北");
            }
            groups.get(target).addAll(pts);
        }
    }

    // 按比例分配总簇数K
    private Map<String, Integer> distributeK(Map<String, List<HeritagePointDTO>> groups, int totalK) {
        Map<String, Integer> assign = new LinkedHashMap<>();
        if (totalK <= 0) return assign; // 返回空，由各区域自行确定最优K

        int sum = groups.values().stream().mapToInt(List::size).sum();
        int remaining = totalK;
        // 先按比例向下取整
        List<Map.Entry<String, List<HeritagePointDTO>>> sortedGroups = groups.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .collect(Collectors.toList());
        for (Map.Entry<String, List<HeritagePointDTO>> e : sortedGroups) {
            int count = e.getValue().size();
            int k = (int) Math.floor((double) count / sum * totalK);
            if (k < 1) k = 1;
            if (k > count) k = count;
            assign.put(e.getKey(), k);
            remaining -= k;
        }
        // 剩余名额给点数最多的区域
        if (remaining > 0) {
            String largest = sortedGroups.get(0).getKey();
            int newK = Math.min(assign.get(largest) + remaining, groups.get(largest).size());
            assign.put(largest, newK);
        }
        return assign;
    }

    // 区域内自动确定最优K（基于轮廓系数）
    private int determineOptimalKInRegion(List<HeritagePointDTO> points) {
        List<double[]> vectors = buildFeatureVectors(points);
        int maxK = Math.min(12, vectors.size() / 4);
        if (maxK < 2) return 1;
        double bestScore = -1;
        int bestK = 2;
        double[] uniformW = new double[vectors.get(0).length];
        Arrays.fill(uniformW, 1.0 / uniformW.length);
        for (int k = 2; k <= maxK; k++) {
            int[] labels = weightedKMeansPlusPlus(vectors, k);
            double sil = silhouetteScore(vectors, labels, uniformW);
            if (sil > bestScore) {
                bestScore = sil;
                bestK = k;
            }
        }
        return bestK;
    }

    // ======================== 特征构建（12维） ========================
    private List<double[]> buildFeatureVectors(List<HeritagePointDTO> points) {
        List<double[]> vectors = new ArrayList<>();
        for (HeritagePointDTO p : points) {
            double[] vec = new double[12];
            vec[0] = (p.getLng() - 70.0) / 70.0;          // 经度归一化
            vec[1] = (p.getLat() - 0.0) / 55.0;           // 纬度归一化
            vec[2] = getDynastyCode(p.getPeriod()) / 19.0;
            vec[3] = getMainTypeCode(p.getType()) / 6.0;
            String fine = BuildingTypeExtractor.extractFineType(p.getName());
            vec[4] = (fineCodeMap.computeIfAbsent(fine, k -> fineCodeMap.size()) % 100) / 100.0;
            vec[5] = getProtectionLevel(p.getBatch()) / 3.0;
            vec[6] = getCulturalCircle(p.getProvince(), p.getType()) / 8.0;
            vec[7] = isWorldHeritage(p.getName()) ? 1.0 : 0.0;
            vec[8] = calcLocalDensity(p.getLng(), p.getLat(), points);
            vec[9] = Math.abs(p.getProvince().hashCode() % 34) / 34.0;
            vec[10] = (p.getBatch() != null && p.getBatch().contains("全国重点")) ? 1.0 : 0.0;
            int budget = budgetService.getBudgetForProvince(p.getProvince(), 2025);
            vec[11] = Math.log(budget + 1) / Math.log(50000.0);
            vectors.add(vec);
        }
        return vectors;
    }

    // 原工具方法（保留签名，实现可自行补充）
    private boolean isWorldHeritage(String name) { /* 同上 */ return false; }
    private int getDynastyCode(String periodRaw) { /* 同上 */ return 0; }
    private int getMainTypeCode(String type) { /* 同上 */ return 0; }
    private int getProtectionLevel(String batch) { /* 同上 */ return 0; }
    private int getCulturalCircle(String province, String type) { /* 同上 */ return 0; }
    private double calcLocalDensity(double lng, double lat, List<HeritagePointDTO> pts) { /* 同上 */ return 0; }
    private Map<String, Integer> fineCodeMap = new ConcurrentHashMap<>();

    // ======================== 加权K-Means++ ========================
    /**
     * 加权K-Means++核心算法，权重更新严格遵循论文公式
     */
    private int[] weightedKMeansPlusPlus(List<double[]> data, int k) {
        int n = data.size();
        int dim = data.get(0).length;
        if (k >= n) {
            int[] labels = new int[n];
            for (int i = 0; i < n; i++) labels[i] = i;
            return labels;
        }

        double[] weights = new double[dim];
        Arrays.fill(weights, 1.0 / dim);
        final double beta = 3.0;

        double[][] centroids = initCentroidsPlusPlus(data, k);
        int[] labels = new int[n];
        int maxIter = 100;
        double prevSSE = Double.MAX_VALUE;

        for (int iter = 0; iter < maxIter; iter++) {
            boolean changed = false;

            // 分配：使用加权距离
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDist = weightedDistance(data.get(i), centroids[0], weights);
                for (int c = 1; c < k; c++) {
                    double d = weightedDistance(data.get(i), centroids[c], weights);
                    if (d < bestDist) {
                        bestDist = d;
                        best = c;
                    }
                }
                if (labels[i] != best) {
                    labels[i] = best;
                    changed = true;
                }
            }
            if (!changed) break;

            // 更新中心
            double[][] newCentroids = new double[k][dim];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                int c = labels[i];
                double[] vec = data.get(i);
                for (int d = 0; d < dim; d++) newCentroids[c][d] += vec[d];
                counts[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    for (int d = 0; d < dim; d++) newCentroids[c][d] /= counts[c];
                } else {
                    // 空簇中心重置为随机点
                    newCentroids[c] = data.get(new Random().nextInt(n)).clone();
                }
            }
            centroids = newCentroids;

            // 更新权重（公式14）
            double[] D = new double[dim];
            for (int i = 0; i < n; i++) {
                int c = labels[i];
                double[] vec = data.get(i);
                for (int d = 0; d < dim; d++) {
                    double diff = vec[d] - centroids[c][d];
                    D[d] += diff * diff;
                }
            }
            // 防止除零
            for (int d = 0; d < dim; d++) {
                D[d] = Math.max(D[d], 1e-12);
            }
            double[] newWeights = new double[dim];
            for (int j = 0; j < dim; j++) {
                double sum = 0.0;
                for (int t = 0; t < dim; t++) {
                    sum += Math.pow(D[j] / D[t], 1.0 / (beta - 1));
                }
                newWeights[j] = 1.0 / sum;
            }
            weights = newWeights;

            // 收敛检测
            double sse = 0.0;
            for (int i = 0; i < n; i++) {
                sse += weightedDistance(data.get(i), centroids[labels[i]], weights);
            }
            if (Math.abs(prevSSE - sse) < 1e-6) break;
            prevSSE = sse;
        }
        return labels;
    }

    private double[][] initCentroidsPlusPlus(List<double[]> data, int k) {
        int n = data.size();
        Random rand = new Random();
        double[][] centroids = new double[k][data.get(0).length];
        centroids[0] = data.get(rand.nextInt(n)).clone();
        double[] minDist = new double[n];
        for (int c = 1; c < k; c++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double d = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    double dist = euclideanDist(data.get(i), centroids[j]);
                    if (dist < d) d = dist;
                }
                minDist[i] = d;
                sum += d;
            }
            double target = rand.nextDouble() * sum;
            double acc = 0;
            int idx = 0;
            while (idx < n && acc < target) {
                acc += minDist[idx++];
            }
            centroids[c] = data.get(Math.max(0, idx - 1)).clone();
        }
        return centroids;
    }

    private double weightedDistance(double[] a, double[] b, double[] w) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += w[i] * diff * diff;
        }
        return Math.sqrt(sum);
    }

    private double euclideanDist(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    // ======================== 质量评估 ========================
    private double[][] computeCentroids(List<double[]> data, int[] labels, int k) {
        int dim = data.get(0).length;
        double[][] centroids = new double[k][dim];
        int[] counts = new int[k];
        for (int i = 0; i < data.size(); i++) {
            int c = labels[i];
            double[] vec = data.get(i);
            for (int d = 0; d < dim; d++) centroids[c][d] += vec[d];
            counts[c]++;
        }
        for (int c = 0; c < k; c++) {
            if (counts[c] > 0) {
                for (int d = 0; d < dim; d++) centroids[c][d] /= counts[c];
            }
        }
        return centroids;
    }

    private double computeSSE(List<double[]> data, int[] labels, double[][] centroids, double[] weights) {
        double sse = 0;
        for (int i = 0; i < data.size(); i++) {
            sse += weightedDistance(data.get(i), centroids[labels[i]], weights);
        }
        return sse;
    }

    private double silhouetteScore(List<double[]> data, int[] labels, double[] weights) {
        int n = data.size();
        int maxLabel = Arrays.stream(labels).max().orElse(0);
        double total = 0;
        for (int i = 0; i < n; i++) {
            double a = 0;
            int sameCnt = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                double d = weightedDistance(data.get(i), data.get(j), weights);
                if (labels[j] == labels[i]) {
                    a += d;
                    sameCnt++;
                }
            }
            a = sameCnt > 0 ? a / sameCnt : 0;
            double b = Double.MAX_VALUE;
            for (int c = 0; c <= maxLabel; c++) {
                if (c == labels[i]) continue;
                double sum = 0;
                int cnt = 0;
                for (int j = 0; j < n; j++) {
                    if (labels[j] == c) {
                        sum += weightedDistance(data.get(i), data.get(j), weights);
                        cnt++;
                    }
                }
                if (cnt > 0) b = Math.min(b, sum / cnt);
            }
            if (b == Double.MAX_VALUE) b = a;
            double s = (b - a) / Math.max(a, b);
            total += s;
        }
        return total / n;
    }

    private double daviesBouldinIndex(List<double[]> data, int[] labels, double[][] centroids, double[] weights) {
        int k = centroids.length;
        double[] si = new double[k];
        for (int i = 0; i < k; i++) {
            double sum = 0;
            int cnt = 0;
            for (int j = 0; j < data.size(); j++) {
                if (labels[j] == i) {
                    sum += weightedDistance(data.get(j), centroids[i], weights);
                    cnt++;
                }
            }
            si[i] = cnt > 0 ? sum / cnt : 0;
        }
        double db = 0;
        for (int i = 0; i < k; i++) {
            double maxVal = -1;
            for (int j = 0; j < k; j++) {
                if (i == j) continue;
                double distCent = weightedDistance(centroids[i], centroids[j], weights);
                double val = (si[i] + si[j]) / (distCent + 1e-9);
                if (val > maxVal) maxVal = val;
            }
            db += maxVal;
        }
        return db / k;
    }

    // ======================== 标签生成（区域+风格） ========================
    private Map<Integer, String> generateSemanticLabels(List<HeritagePointDTO> points, int[] labels, String region) {
        Map<Integer, List<HeritagePointDTO>> clusterMap = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            clusterMap.computeIfAbsent(labels[i], k -> new ArrayList<>()).add(points.get(i));
        }
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : clusterMap.entrySet()) {
            int cid = entry.getKey();
            List<HeritagePointDTO> group = entry.getValue();
            String suffix = determineStyleSuffix(group, region);
            result.put(cid, region + "-" + suffix);
        }
        return result;
    }

    private String determineStyleSuffix(List<HeritagePointDTO> group, String region) {
        Map<String, Integer> fineCnt = new HashMap<>();
        Map<String, Integer> mainCnt = new HashMap<>();
        Map<String, Integer> keywordCnt = new HashMap<>();
        Set<String> provinces = new HashSet<>();
        for (HeritagePointDTO p : group) {
            String fine = BuildingTypeExtractor.extractFineType(p.getName());
            fineCnt.merge(fine, 1, Integer::sum);
            String main = p.getType();
            if (main != null) mainCnt.merge(main, 1, Integer::sum);
            if (p.getProvince() != null) provinces.add(p.getProvince());
            // 关键词检测
            String name = p.getName();
            if (name != null) {
                if (name.contains("土司") || name.contains("司城")) keywordCnt.merge("土司", 1, Integer::sum);
                if (name.contains("窑址") || name.contains("瓷窑") || "窑".equals(fine)) keywordCnt.merge("古窑", 1, Integer::sum);
                if (name.contains("运河") || name.contains("闸") || name.contains("堰") || name.contains("堤")) keywordCnt.merge("水利", 1, Integer::sum);
                if (name.contains("长城") || name.contains("关") || name.contains("烽燧")) keywordCnt.merge("军事防御", 1, Integer::sum);
                if (name.contains("书院") || name.contains("文庙") || name.contains("贡院") || name.contains("藏书楼")) keywordCnt.merge("文教", 1, Integer::sum);
                if (name.contains("会馆") || name.contains("商会")) keywordCnt.merge("会馆", 1, Integer::sum);
            }
        }
        int total = group.size();
        String topFine = fineCnt.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        String topMain = mainCnt.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");

        // 优先特殊风格
        if (keywordCnt.getOrDefault("古窑", 0) > total * 0.3) return "古窑遗址";
        if (keywordCnt.getOrDefault("文教", 0) > total * 0.3) return "文教建筑";
        if (keywordCnt.getOrDefault("会馆", 0) > total * 0.3) return "商帮会馆";
        if (keywordCnt.getOrDefault("军事防御", 0) > total * 0.3) return "军事防御";
        if (keywordCnt.getOrDefault("水利", 0) > total * 0.3) return "水利工程";

        boolean isTibetan = provinces.stream().anyMatch(p -> p.contains("西藏") || p.contains("青海") ||
                (p.contains("四川") && (p.contains("甘孜") || p.contains("阿坝"))));
        if ("窟".equals(topFine) || (topMain != null && topMain.contains("石窟寺"))) {
            return "青藏".equals(region) ? "高原石窟" : "石窟艺术";
        }
        if (isTibetan && ("寺".equals(topFine) || "塔".equals(topFine))) return "藏式建筑";

        // 区域特定风格
        switch (region) {
            case "华北":
                if ("古建筑".equals(topMain) && (topFine.contains("宫") || topFine.contains("庙") || topFine.contains("陵") || topFine.contains("殿")))
                    return "官式建筑";
                if ("古遗址".equals(topMain)) return "史前遗址";
                if ("古墓葬".equals(topMain) && (topFine.contains("王陵") || topFine.contains("帝陵"))) return "帝陵王陵";
                break;
            case "华东":
                if ("古建筑".equals(topMain) && (topFine.contains("宅") || topFine.contains("园林") || topFine.contains("桥")))
                    return "江南水乡";
                break;
            case "华南":
                if ("古建筑".equals(topMain)) return "岭南建筑";
                break;
            case "华中":
                if ("古建筑".equals(topMain)) return "中原官式";
                break;
            case "西南":
                return "西南民族建筑";
            case "西北":
                if ("古遗址".equals(topMain)) return "西北古遗址";
                break;
            case "东北":
                return "东北古建";
            case "青藏":
                return "高原藏建";
        }

        // 默认按主类型
        if ("古遗址".equals(topMain)) return "古遗址";
        if ("古墓葬".equals(topMain)) return "古墓葬";
        if ("古建筑".equals(topMain)) return "古建";
        if ("石窟寺及石刻".equals(topMain)) return "石窟石刻";
        if ("近现代重要史迹及代表性建筑".equals(topMain)) return "近现代史迹";
        return "综合遗产";
    }
}