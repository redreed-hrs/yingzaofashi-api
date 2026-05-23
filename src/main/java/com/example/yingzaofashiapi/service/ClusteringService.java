package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ClusteringService {

    private final HeritageService heritageService;

    // 区域判定规则（基于质心经纬度）
    private static final List<RegionRule> REGION_RULES = Arrays.asList(
            new RegionRule("华北官式建筑群",
                    (lng, lat) -> lng > 112 && lng < 120 && lat > 34 && lat < 42),
            new RegionRule("江南水乡建筑群",
                    (lng, lat) -> lng > 115 && lng < 122 && lat > 28 && lat < 32),
            new RegionRule("西南少数民族建筑",
                    (lng, lat) -> lng > 97 && lng < 108 && lat > 22 && lat < 32),
            new RegionRule("中原关中古建群",
                    (lng, lat) -> lng > 108 && lng < 114 && lat > 33 && lat < 36),
            new RegionRule("边疆宗教建筑群",
                    (lng, lat) -> true)   // 兜底
    );

    /**
     * 获取所有文保点，并附上语义聚类名称（clusterName）
     * @param k 聚类个数（建议5）
     */
    public List<HeritagePointDTO> getHeritagePointsWithCluster(int k) {
        List<HeritagePointDTO> points = heritageService.getAllHeritagePointsForClustering();
        if (points == null || points.isEmpty()) return Collections.emptyList();

        // 1. 确定性 K-Means 聚类
        int[] labels = deterministicKMeans(points, Math.min(k, points.size()));

        // 2. 按簇分组并计算每个簇的质心
        Map<Integer, List<HeritagePointDTO>> clusterGroups = new HashMap<>();
        for (int i = 0; i < points.size(); i++) {
            clusterGroups.computeIfAbsent(labels[i], g -> new ArrayList<>()).add(points.get(i));
        }

        // 3. 为每个簇分配固定的语义名称（基于质心位置）
        Map<Integer, String> clusterSemanticMap = new HashMap<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : clusterGroups.entrySet()) {
            int clusterId = entry.getKey();
            List<HeritagePointDTO> group = entry.getValue();
            double avgLng = group.stream().mapToDouble(HeritagePointDTO::getLng).average().orElse(0);
            double avgLat = group.stream().mapToDouble(HeritagePointDTO::getLat).average().orElse(0);
            String semantic = findSemanticName(avgLng, avgLat);
            clusterSemanticMap.put(clusterId, semantic);
        }

        // 4. 为每个点设置 clusterName
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setClusterName(clusterSemanticMap.get(labels[i]));
        }
        return points;
    }

    // 确定性 K-Means（经度排序均匀采样初始化，确保每次运行结果一致）
    private int[] deterministicKMeans(List<HeritagePointDTO> points, int k) {
        int n = points.size();
        if (k >= n) {
            int[] labels = new int[n];
            for (int i = 0; i < n; i++) labels[i] = i;
            return labels;
        }

        double[][] data = points.stream()
                .map(p -> new double[]{p.getLng(), p.getLat()})
                .toArray(double[][]::new);

        // 按经度排序索引
        List<Integer> sortedIndices = IntStream.range(0, n)
                .boxed()
                .sorted(Comparator.comparingDouble(i -> data[i][0]))
                .collect(Collectors.toList());

        // 均匀采样 k 个初始质心
        double[][] centroids = new double[k][2];
        for (int i = 0; i < k; i++) {
            int idx = sortedIndices.get(i * (n - 1) / (k - 1));
            centroids[i] = data[idx].clone();
        }

        int[] labels = new int[n];
        boolean changed;
        int maxIter = 100;
        int iter = 0;
        do {
            changed = false;
            // 分配
            for (int i = 0; i < n; i++) {
                int bestCluster = 0;
                double bestDist = Double.MAX_VALUE;
                for (int c = 0; c < k; c++) {
                    double dist = euclideanDist(data[i], centroids[c]);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestCluster = c;
                    }
                }
                if (labels[i] != bestCluster) {
                    labels[i] = bestCluster;
                    changed = true;
                }
            }
            if (!changed) break;

            // 更新质心
            int[] counts = new int[k];
            double[][] sums = new double[k][2];
            for (int i = 0; i < n; i++) {
                int c = labels[i];
                counts[c]++;
                sums[c][0] += data[i][0];
                sums[c][1] += data[i][1];
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    centroids[c][0] = sums[c][0] / counts[c];
                    centroids[c][1] = sums[c][1] / counts[c];
                }
            }
            iter++;
        } while (changed && iter < maxIter);
        return labels;
    }

    private double euclideanDist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1];
        return Math.sqrt(dx*dx + dy*dy);
    }

    private String findSemanticName(double lng, double lat) {
        for (RegionRule rule : REGION_RULES) {
            if (rule.matches(lng, lat)) {
                return rule.name;
            }
        }
        return "边疆宗教建筑群";
    }

    static class RegionRule {
        String name;
        java.util.function.BiPredicate<Double, Double> predicate;
        RegionRule(String name, java.util.function.BiPredicate<Double, Double> predicate) {
            this.name = name;
            this.predicate = predicate;
        }
        boolean matches(double lng, double lat) { return predicate.test(lng, lat); }
    }
}