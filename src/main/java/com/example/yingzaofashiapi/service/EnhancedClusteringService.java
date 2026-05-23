package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import com.example.yingzaofashiapi.util.BuildingTypeExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

@Service
@RequiredArgsConstructor
public class EnhancedClusteringService {

    private final HeritageService heritageService;

    // ---------- 1. 朝代映射 ----------
    private static final List<String> DYNASTY_ORDER = Arrays.asList(
        "旧石器时代", "新石器时代", "夏", "商", "西周", "战国", "秦", "汉",
        "三国", "晋", "南北朝", "隋", "唐", "五代", "宋", "辽", "金", "元", "明", "清"
    );

    private static final Map<String, Integer> DYNASTY_ALIAS = new HashMap<>();
    static {
        DYNASTY_ALIAS.put("东汉", 7);    DYNASTY_ALIAS.put("西汉", 7);
        DYNASTY_ALIAS.put("高句丽", 7); DYNASTY_ALIAS.put("南诏", 11);
        DYNASTY_ALIAS.put("大理", 14);   DYNASTY_ALIAS.put("渤海", 7);
        DYNASTY_ALIAS.put("西夏", 16);   DYNASTY_ALIAS.put("金", 16);
        DYNASTY_ALIAS.put("后周", 5);    // 五代十国归为五代(14)？简化处理
    }

    // ---------- 2. 建筑主类型 ----------
    private static final List<String> MAIN_TYPES = Arrays.asList(
        "古遗址", "古墓葬", "古建筑", "石窟寺及石刻", "近现代重要史迹及代表性建筑", "其他"
    );

    // 细粒度关键词在运行时动态编码
    private static final Map<String, Integer> SUB_TYPE_CODE_MAP = new ConcurrentHashMap<>();

    // ---------- 3. 特征权重 ----------
    private static final double W_SPACE   = 0.4;
    private static final double W_DYNASTY = 0.3;
    private static final double W_TYPE    = 0.2;
    private static final double W_REGION  = 0.1;

    // ---------- 4. 地理区域判定 ----------
    private static final List<RegionRule> REGION_RULES = Arrays.asList(
        new RegionRule("华北", (lng, lat) -> lng > 112 && lng < 120 && lat > 34 && lat < 42),
        new RegionRule("江南", (lng, lat) -> lng > 115 && lng < 122 && lat > 28 && lat < 32),
        new RegionRule("西南", (lng, lat) -> lng > 97  && lng < 108 && lat > 22 && lat < 32),
        new RegionRule("中原", (lng, lat) -> lng > 108 && lng < 114 && lat > 33 && lat < 36),
        new RegionRule("边疆", (lng, lat) -> true)
    );

    // ------------------------- 对外接口 -------------------------
    /**
     * 获取所有文保点，并附上增强聚类语义标签（clusterName）
     * @param k 期望簇数，<=0 时自动确定最佳簇数
     */
    public List<HeritagePointDTO> getHeritagePointsWithEnhancedCluster(int k) {
        List<HeritagePointDTO> points = heritageService.getAllHeritagePoints();
        if (points == null || points.isEmpty()) return Collections.emptyList();

        // 1. 构建特征向量（5维）
        List<double[]> featureVectors = buildFeatureVectors(points);

        // 2. 确定最佳簇数
        int bestK = (k > 0) ? Math.min(k, points.size()) : determineOptimalK(featureVectors);

        // 3. K-Means++ 聚类
        int[] labels = kMeansPlusPlus(featureVectors, bestK);

        // 4. 生成语义标签
        Map<Integer, String> semanticMap = generateSemanticLabels(points, labels);

        // 5. 赋值给 DTO
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setClusterName(semanticMap.get(labels[i]));
        }
        return points;
    }

    // ------------------------- 特征构建 -------------------------
    private List<double[]> buildFeatureVectors(List<HeritagePointDTO> points) {
        List<double[]> vectors = new ArrayList<>(points.size());
        for (HeritagePointDTO p : points) {
            double[] vec = new double[5]; // [normLng, normLat, normDynasty, normType, normRegion]
            // 空间归一化：中国经度70~140，纬度0~55
            vec[0] = (p.getLng() - 70.0) / 70.0;
            vec[1] = (p.getLat() - 0.0) / 55.0;

            // 朝代归一化 (0~19)
            int dynastyCode = getDynastyCode(p.getPeriod());
            vec[2] = dynastyCode / 19.0;

            // 类型组合编码：主类型*20 + 子类型编码，再归一化至[0,1]
            int mainCode = getMainTypeCode(p.getType());
            int subCode = getSubTypeCode(p.getName());
            int combined = mainCode * 20 + subCode;
            vec[3] = combined / 400.0; // 假设最大组合 <400

            // 省级分区编码（哈希取模，实际可维护全映射表）
            int provinceCode = Math.abs(p.getProvince().hashCode() % 34);
            vec[4] = provinceCode / 34.0;

            vectors.add(vec);
        }
        return vectors;
    }

    private int getDynastyCode(String periodRaw) {
        if (periodRaw == null) return 0;
        // 年份处理
        if (periodRaw.matches("\\d{4}年")) {
            int year = Integer.parseInt(periodRaw.replace("年", ""));
            if (year >= 1912) return 19;      // 民国（近似清）
            else if (year >= 1644) return 18; // 清
            else if (year >= 1368) return 17; // 明
            else if (year >= 1271) return 16; // 元
            else if (year >= 960)  return 14; // 宋
            else if (year >= 618)  return 11; // 唐
            else if (year >= 581)  return 10; // 隋
            else if (year >= 420)  return 8;  // 南北朝
            else if (year >= 265)  return 7;  // 晋
            else return 7;                    // 汉及更早
        }
        // 别名匹配
        for (Map.Entry<String, Integer> e : DYNASTY_ALIAS.entrySet()) {
            if (periodRaw.contains(e.getKey())) return e.getValue();
        }
        // 顺序匹配
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

    private int getSubTypeCode(String name) {
        String fine = BuildingTypeExtractor.extractFineType(name);
        return SUB_TYPE_CODE_MAP.computeIfAbsent(fine, k -> SUB_TYPE_CODE_MAP.size());
    }

    // ------------------------- 加权距离 -------------------------
    private double weightedDistance(double[] a, double[] b) {
        double dSpace = Math.hypot(a[0] - b[0], a[1] - b[1]);
        double dDynasty = Math.abs(a[2] - b[2]);
        double dType = Math.abs(a[3] - b[3]);
        double dRegion = Math.abs(a[4] - b[4]);
        return W_SPACE * dSpace + W_DYNASTY * dDynasty + W_TYPE * dType + W_REGION * dRegion;
    }

    // ------------------------- K-Means++ 实现 -------------------------
    private int[] kMeansPlusPlus(List<double[]> data, int k) {
        int n = data.size();
        if (k >= n) {
            int[] labels = new int[n];
            for (int i = 0; i < n; i++) labels[i] = i;
            return labels;
        }

        double[][] centroids = new double[k][5];
        Random rand = new Random(42); // 固定种子，保证可复现

        // 1. 随机选第一个质心
        centroids[0] = data.get(rand.nextInt(n)).clone();

        double[] minDist = new double[n];
        for (int c = 1; c < k; c++) {
            double sum = 0;
            for (int i = 0; i < n; i++) {
                double d = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) {
                    d = Math.min(d, weightedDistance(data.get(i), centroids[j]));
                }
                minDist[i] = d;
                sum += d;
            }
            double target = rand.nextDouble() * sum;
            int idx = 0;
            double acc = 0;
            while (idx < n && acc < target) {
                acc += minDist[idx++];
            }
            centroids[c] = data.get(idx - 1).clone();
        }

        int[] labels = new int[n];
        boolean changed;
        int iter = 0;
        do {
            changed = false;
            // 分配
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDist = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double d = weightedDistance(data.get(i), centroids[c]);
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

            // 更新质心
            double[][] sums = new double[k][5];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                int c = labels[i];
                double[] vec = data.get(i);
                for (int d = 0; d < 5; d++) sums[c][d] += vec[d];
                counts[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    for (int d = 0; d < 5; d++) centroids[c][d] = sums[c][d] / counts[c];
                }
            }
            iter++;
        } while (changed && iter < 100);

        return labels;
    }

    // ------------------------- 自动确定最佳 k（轮廓系数）-------------------------
    private int determineOptimalK(List<double[]> data) {
        int maxK = Math.min(15, data.size() / 3);
        if (maxK < 2) return 2;
        double bestScore = -1;
        int bestK = 2;
        for (int k = 2; k <= maxK; k++) {
            int[] labels = kMeansPlusPlus(data, k);
            double score = silhouetteScore(data, labels);
            if (score > bestScore) {
                bestScore = score;
                bestK = k;
            }
        }
        return bestK;
    }
    public Map<HeritagePointDTO, Integer> getPointsWithClusterLabels(int k) {
        List<HeritagePointDTO> points = heritageService.getAllHeritagePoints();
        if (points.isEmpty()) return Collections.emptyMap();
        List<double[]> vectors = buildFeatureVectors(points);
        int bestK = (k > 0) ? Math.min(k, points.size()) : determineOptimalK(vectors);
        int[] labels = kMeansPlusPlus(vectors, bestK);
        Map<HeritagePointDTO, Integer> result = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            result.put(points.get(i), labels[i]);
        }
        // 同时生成语义标签并赋值给 points
        Map<Integer, String> semanticMap = generateSemanticLabels(points, labels);
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setClusterName(semanticMap.get(labels[i]));
        }
        return result;
    }
    private double silhouetteScore(List<double[]> data, int[] labels) {
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
                double d = weightedDistance(data.get(i), data.get(j));
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
                        sum += weightedDistance(data.get(i), data.get(j));
                        cnt++;
                    }
                }
                if (cnt > 0) b = Math.min(b, sum / cnt);
            }
            if (b == Double.MAX_VALUE) b = a; // 只有一个簇的情况
            double s = (b - a) / Math.max(a, b);
            total += s;
        }
        return total / n;
    }

    // ------------------------- 语义标签生成 -------------------------
    private Map<Integer, String> generateSemanticLabels(List<HeritagePointDTO> points, int[] labels) {
        Map<Integer, List<HeritagePointDTO>> clusterMap = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            clusterMap.computeIfAbsent(labels[i], k -> new ArrayList<>()).add(points.get(i));
        }

        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : clusterMap.entrySet()) {
            int cid = entry.getKey();
            List<HeritagePointDTO> group = entry.getValue();

            // 统计主要朝代、主类型、子类型
            Map<String, Integer> dynastyCnt = new HashMap<>();
            Map<String, Integer> mainTypeCnt = new HashMap<>();
            Map<String, Integer> subTypeCnt = new HashMap<>();
            double sumLng = 0, sumLat = 0;

            for (HeritagePointDTO p : group) {
                // 朝代
                String period = p.getPeriod();
                for (String d : DYNASTY_ORDER) {
                    if (period != null && period.contains(d)) {
                        dynastyCnt.merge(d, 1, Integer::sum);
                        break;
                    }
                }
                // 主类型
                String main = (p.getType() == null) ? "其他" : p.getType();
                mainTypeCnt.merge(main, 1, Integer::sum);
                // 子类型
                String sub = BuildingTypeExtractor.extractFineType(p.getName());
                subTypeCnt.merge(sub, 1, Integer::sum);

                sumLng += p.getLng();
                sumLat += p.getLat();
            }

            String mainDynasty = dynastyCnt.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("未知");
            String mainType = mainTypeCnt.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("古建筑");
            String subType = subTypeCnt.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");

            // 语义化类型名称
            String semanticType = semanticTypeName(mainType, subType);
            // 地理区域
            double avgLng = sumLng / group.size();
            double avgLat = sumLat / group.size();
            String region = getRegionName(avgLng, avgLat);

            String label = String.format("%s-%s(%s)", mainDynasty, semanticType, region);
            result.put(cid, label);
        }
        return result;
    }

    private String semanticTypeName(String mainType, String subType) {
        switch (mainType) {
            case "古建筑":
                if ("寺".equals(subType)) return "佛寺";
                if ("塔".equals(subType)) return "佛塔";
                if ("陵".equals(subType) || "墓".equals(subType)) return "陵寝";
                if ("桥".equals(subType)) return "古桥";
                if ("宫".equals(subType)) return "宫殿";
                if ("园".equals(subType)) return "园林";
                return "官式建筑";
            case "古墓葬":
                return "古墓群";
            case "古遗址":
                return "古城址";
            case "石窟寺及石刻":
                return "石刻造像";
            case "近现代重要史迹及代表性建筑":
                return "近现代史迹";
            default:
                return "文化遗产";
        }
    }

    private String getRegionName(double lng, double lat) {
        for (RegionRule rule : REGION_RULES) {
            if (rule.predicate.test(lng, lat)) return rule.name;
        }
        return "边疆";
    }

    private static class RegionRule {
        String name;
        BiPredicate<Double, Double> predicate;
        RegionRule(String name, BiPredicate<Double, Double> predicate) {
            this.name = name;
            this.predicate = predicate;
        }
    }
}