package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import com.example.yingzaofashiapi.util.BuildingTypeExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

@Service
@RequiredArgsConstructor
public class EnhancedClusteringService {

    private final HeritageService heritageService;
    private final BudgetService budgetService;

    // 朝代顺序
    private static final List<String> DYNASTY_ORDER = Arrays.asList(
            "旧石器时代", "新石器时代", "夏", "商", "西周", "战国", "秦", "汉",
            "三国", "晋", "南北朝", "隋", "唐", "五代", "宋", "辽", "金", "元", "明", "清"
    );
    private static final Map<String, Integer> DYNASTY_ALIAS = new HashMap<>();
    static {
        DYNASTY_ALIAS.put("东汉", 7); DYNASTY_ALIAS.put("西汉", 7);
        DYNASTY_ALIAS.put("高句丽", 7); DYNASTY_ALIAS.put("南诏", 11);
        DYNASTY_ALIAS.put("大理", 14); DYNASTY_ALIAS.put("渤海", 7);
        DYNASTY_ALIAS.put("西夏", 16); DYNASTY_ALIAS.put("金", 16);
    }

    private static final List<String> MAIN_TYPES = Arrays.asList(
            "古遗址", "古墓葬", "古建筑", "石窟寺及石刻", "近现代重要史迹及代表性建筑", "其他"
    );
    private final Map<String, Integer> fineCodeMap = new ConcurrentHashMap<>();

    // 区域判定规则
    private static final List<RegionRule> REGION_RULES = Arrays.asList(
            new RegionRule("华北", (lng, lat) -> lng > 112 && lng < 120 && lat > 34 && lat < 42),
            new RegionRule("江南", (lng, lat) -> lng > 115 && lng < 122 && lat > 28 && lat < 32),
            new RegionRule("西南", (lng, lat) -> lng > 97  && lng < 108 && lat > 22 && lat < 32),
            new RegionRule("中原", (lng, lat) -> lng > 108 && lng < 114 && lat > 33 && lat < 36),
            new RegionRule("边疆", (lng, lat) -> true)
    );

    // 聚类质量指标（供外部查询）
    private double lastSSE = 0;
    private double lastSilhouette = 0;
    private double lastDBIndex = 0;

    // ---------- 对外接口 ----------
    /**
     * 获取增强聚类结果（带缓存，k 相同则直接返回缓存结果）
     * @param k 聚类个数，<=0时自动确定最优k
     */
    @Cacheable(value = "enhancedClusters", key = "#k", unless = "#result == null")
    public List<HeritagePointDTO> getHeritagePointsWithEnhancedCluster(int k) {
        List<HeritagePointDTO> points = heritageService.getAllHeritagePoints();
        if (points == null || points.isEmpty()) return Collections.emptyList();

        List<double[]> vectors = buildFeatureVectors(points);
        double[] weights = computeAdaptiveWeights(vectors);
        int bestK = (k > 0) ? Math.min(k, points.size()) : determineOptimalK(vectors, weights);
        int[] labels = kMeansPlusPlus(vectors, bestK, weights);

        // 计算质量指标
        double[][] centroids = computeCentroids(vectors, labels, bestK);
        lastSSE = computeSSE(vectors, labels, centroids, weights);
        lastSilhouette = silhouetteScore(vectors, labels, weights);
        lastDBIndex = daviesBouldinIndex(vectors, labels, centroids, weights);

        // 生成语义标签
        Map<Integer, String> semanticMap = generateSemanticLabels(points, labels);
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setClusterName(semanticMap.get(labels[i]));
        }
        return points;
    }

    /**
     * 手动清除增强聚类缓存（当文保数据更新时应调用）
     */
    @CacheEvict(value = "enhancedClusters", allEntries = true)
    public void clearEnhancedClusterCache() {
        // 仅用于触发缓存清除
    }

    // 供外部获取质量指标（不缓存，因为每次聚类后都会重新计算）
    public Map<String, Double> getLastQualityMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        metrics.put("sse", lastSSE);
        metrics.put("silhouette", lastSilhouette);
        metrics.put("dbIndex", lastDBIndex);
        return metrics;
    }

    // ---------- 特征构建（12维）----------
    private List<double[]> buildFeatureVectors(List<HeritagePointDTO> points) {
        List<double[]> vectors = new ArrayList<>();
        for (HeritagePointDTO p : points) {
            double[] vec = new double[12];
            // 0-1: 归一化经纬度
            vec[0] = (p.getLng() - 70.0) / 70.0;
            vec[1] = (p.getLat() - 0.0) / 55.0;
            // 2: 朝代编码
            vec[2] = getDynastyCode(p.getPeriod()) / 19.0;
            // 3: 主类型编码
            vec[3] = getMainTypeCode(p.getType()) / 6.0;
            // 4: 细粒度类型编码
            String fine = BuildingTypeExtractor.extractFineType(p.getName());
            vec[4] = (fineCodeMap.computeIfAbsent(fine, k -> fineCodeMap.size()) % 100) / 100.0;
            // 5: 保护等级（从批次推断）
            vec[5] = getProtectionLevel(p.getBatch()) / 3.0;
            // 6: 文化圈层
            vec[6] = getCulturalCircle(p.getProvince(), p.getType()) / 8.0;
            // 7: 世界遗产标志（暂默认为0）
            vec[7] = 0.0;
            // 8: 周边密度（简易）
            vec[8] = calcLocalDensity(p.getLng(), p.getLat(), points);
            // 9: 省份代码（哈希）
            vec[9] = Math.abs(p.getProvince().hashCode() % 34) / 34.0;
            // 10: 是否为全国重点文物保护单位（根据batch）
            vec[10] = (p.getBatch() != null && p.getBatch().contains("全国重点")) ? 1.0 : 0.0;
            // 11: 财政投入（对数归一化）
            int budget = budgetService.getBudgetForProvince(p.getProvince(), 2025);
            vec[11] = Math.log(budget + 1) / Math.log(50000.0);
            vectors.add(vec);
        }
        return vectors;
    }

    private int getDynastyCode(String periodRaw) {
        if (periodRaw == null) return 0;
        if (periodRaw.matches("\\d{4}年")) {
            int year = Integer.parseInt(periodRaw.replace("年", ""));
            if (year >= 1912) return 19;
            else if (year >= 1644) return 18;
            else if (year >= 1368) return 17;
            else if (year >= 1271) return 16;
            else if (year >= 960) return 14;
            else if (year >= 618) return 11;
            else return 7;
        }
        for (Map.Entry<String, Integer> e : DYNASTY_ALIAS.entrySet()) {
            if (periodRaw.contains(e.getKey())) return e.getValue();
        }
        for (int i = 0; i < DYNASTY_ORDER.size(); i++) {
            if (periodRaw.contains(DYNASTY_ORDER.get(i))) return i;
        }
        return 0;
    }

    private int getMainTypeCode(String type) {
        if (type == null) return MAIN_TYPES.size() - 1;
        for (int i = 0; i < MAIN_TYPES.size(); i++) {
            if (type.contains(MAIN_TYPES.get(i))) return i;
        }
        return MAIN_TYPES.size() - 1;
    }

    private int getProtectionLevel(String batch) {
        if (batch == null) return 0;
        if (batch.contains("全国重点")) return 3;
        if (batch.contains("省级")) return 2;
        if (batch.contains("市级")) return 1;
        return 0;
    }

    private int getCulturalCircle(String province, String type) {
        // 简化：按区域划分文化圈
        if (province.contains("西藏") || province.contains("青海") || province.contains("四川") && type.contains("藏传")) return 1;
        if (province.contains("北京") || province.contains("河北") || province.contains("山西")) return 2;
        if (province.contains("江苏") || province.contains("浙江") || province.contains("安徽")) return 3;
        return 4;
    }

    private double calcLocalDensity(double lng, double lat, List<HeritagePointDTO> points) {
        int count = 0;
        for (HeritagePointDTO p : points) {
            double d = Math.hypot(p.getLng() - lng, p.getLat() - lat);
            if (d < 2.0) count++;
        }
        return Math.min(1.0, count / 50.0);
    }

    // ---------- 自适应权重 ----------
    private double[] computeAdaptiveWeights(List<double[]> vectors) {
        int dim = vectors.get(0).length;
        double[] means = new double[dim];
        double[] vars = new double[dim];
        for (double[] v : vectors) {
            for (int i = 0; i < dim; i++) means[i] += v[i];
        }
        for (int i = 0; i < dim; i++) means[i] /= vectors.size();
        for (double[] v : vectors) {
            for (int i = 0; i < dim; i++) vars[i] += Math.pow(v[i] - means[i], 2);
        }
        double totalVar = Arrays.stream(vars).sum();
        double[] weights = new double[dim];
        if (totalVar == 0) {
            Arrays.fill(weights, 1.0 / dim);
        } else {
            for (int i = 0; i < dim; i++) weights[i] = vars[i] / totalVar;
        }
        return weights;
    }

    private double weightedDistance(double[] a, double[] b, double[] weights) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += weights[i] * diff * diff;
        }
        return Math.sqrt(sum);
    }

    // ---------- K-Means++ 多次尝试 ----------
    private int[] kMeansPlusPlus(List<double[]> data, int k, double[] weights) {
        int n = data.size();
        if (k >= n) {
            int[] labels = new int[n];
            for (int i = 0; i < n; i++) labels[i] = i;
            return labels;
        }

        double[][] bestCentroids = null;
        int[] bestLabels = null;
        double bestSSE = Double.MAX_VALUE;
        int attempts = 5; // 5次随机初始化

        for (int attempt = 0; attempt < attempts; attempt++) {
            double[][] centroids = initCentroids(data, k, weights);
            int[] labels = new int[n];
            boolean changed;
            int iter = 0;
            do {
                changed = false;
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
                updateCentroids(data, labels, centroids, k);
            } while (++iter < 100);
            double sse = computeSSE(data, labels, centroids, weights);
            if (sse < bestSSE) {
                bestSSE = sse;
                bestLabels = labels.clone();
                bestCentroids = deepCopyCentroids(centroids);
            }
        }
        return bestLabels;
    }

    private double[][] initCentroids(List<double[]> data, int k, double[] weights) {
        int n = data.size();
        Random rand = new Random(System.currentTimeMillis());
        double[][] centroids = new double[k][data.get(0).length];
        centroids[0] = data.get(rand.nextInt(n)).clone();
        double[] minDist = new double[n];
        for (int c = 1; c < k; c++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double d = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    d = Math.min(d, weightedDistance(data.get(i), centroids[j], weights));
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
            centroids[c] = data.get(idx - 1).clone();
        }
        return centroids;
    }

    private void updateCentroids(List<double[]> data, int[] labels, double[][] centroids, int k) {
        int dim = data.get(0).length;
        double[][] sums = new double[k][dim];
        int[] counts = new int[k];
        for (int i = 0; i < data.size(); i++) {
            int c = labels[i];
            double[] vec = data.get(i);
            for (int d = 0; d < dim; d++) sums[c][d] += vec[d];
            counts[c]++;
        }
        for (int c = 0; c < k; c++) {
            if (counts[c] > 0) {
                for (int d = 0; d < dim; d++) centroids[c][d] = sums[c][d] / counts[c];
            }
        }
    }

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

    private double[][] deepCopyCentroids(double[][] src) {
        double[][] dst = new double[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) {
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    // ---------- 评估指标 ----------
    private double computeSSE(List<double[]> data, int[] labels, double[][] centroids, double[] weights) {
        double sse = 0;
        for (int i = 0; i < data.size(); i++) {
            int c = labels[i];
            sse += weightedDistance(data.get(i), centroids[c], weights);
        }
        return sse;
    }

    private double silhouetteScore(List<double[]> data, int[] labels, double[] weights) {
        int n = data.size();
        int maxLabel = Arrays.stream(labels).max().orElse(0);
        int[] counts = new int[maxLabel + 1];
        for (int l : labels) counts[l]++;

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
                double val = (si[i] + si[j]) / distCent;
                if (val > maxVal) maxVal = val;
            }
            db += maxVal;
        }
        return db / k;
    }

    private int determineOptimalK(List<double[]> data, double[] weights) {
        int maxK = Math.min(12, data.size() / 5);
        if (maxK < 2) return 2;
        double bestScore = -1;
        int bestK = 2;
        for (int k = 2; k <= maxK; k++) {
            int[] labels = kMeansPlusPlus(data, k, weights);
            double[][] centroids = computeCentroids(data, labels, k);
            double sil = silhouetteScore(data, labels, weights);
            if (sil > bestScore) {
                bestScore = sil;
                bestK = k;
            }
        }
        return bestK;
    }

    // ---------- 语义标签生成 ----------
    private Map<Integer, String> generateSemanticLabels(List<HeritagePointDTO> points, int[] labels) {
        Map<Integer, List<HeritagePointDTO>> clusterMap = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            clusterMap.computeIfAbsent(labels[i], k -> new ArrayList<>()).add(points.get(i));
        }
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : clusterMap.entrySet()) {
            int cid = entry.getKey();
            List<HeritagePointDTO> group = entry.getValue();
            // 统计主要朝代
            Map<String, Integer> dynastyCnt = new HashMap<>();
            double sumLng = 0, sumLat = 0;
            for (HeritagePointDTO p : group) {
                String period = p.getPeriod();
                for (String d : DYNASTY_ORDER) {
                    if (period != null && period.contains(d)) {
                        dynastyCnt.merge(d, 1, Integer::sum);
                        break;
                    }
                }
                sumLng += p.getLng();
                sumLat += p.getLat();
            }
            String mainDynasty = dynastyCnt.entrySet().stream()
                    .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("未知");
            String region = getRegionName(sumLng / group.size(), sumLat / group.size());
            result.put(cid, String.format("%s-%s", mainDynasty, region));
        }
        return result;
    }

    private String getRegionName(double lng, double lat) {
        for (RegionRule rule : REGION_RULES) {
            if (rule.predicate.test(lng, lat)) return rule.name;
        }
        return "边疆";
    }

    static class RegionRule {
        String name;
        BiPredicate<Double, Double> predicate;
        RegionRule(String name, BiPredicate<Double, Double> predicate) {
            this.name = name;
            this.predicate = predicate;
        }
    }
}