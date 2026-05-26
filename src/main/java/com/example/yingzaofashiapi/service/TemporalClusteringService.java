package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.ClusterEvolutionDTO;
import com.example.yingzaofashiapi.dao.DynastySnapshot;
import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemporalClusteringService {

    private final HeritageService heritageService;
    // 未使用的字段已注释，消除警告
    // private final EnhancedClusteringService clusteringService;

    private static final List<String> DYNASTIES = Arrays.asList(
            "旧石器时代", "新石器时代", "夏", "商", "西周", "战国", "秦", "汉",
            "三国", "晋", "南北朝", "隋", "唐", "五代", "宋", "辽", "金", "元", "明", "清"
    );

    public List<ClusterEvolutionDTO> getClusterEvolution(int k) {
        List<ClusterEvolutionDTO> evolutions = new ArrayList<>();
        Map<String, List<ClusterInPeriod>> periodClusterMap = new LinkedHashMap<>();

        // 对每个朝代独立聚类
        for (String dynasty : DYNASTIES) {
            List<HeritagePointDTO> points = heritageService.findPointsByPeriod(dynasty);
            if (points.size() < 3) continue;
            int effectiveK = Math.max(1, Math.min(k > 0 ? k : 5, points.size() / 5 + 1));
            List<ClusterInPeriod> clusters = clusterPointsSimple(points, effectiveK);
            periodClusterMap.put(dynasty, clusters);
        }

        // 跨朝代匹配（贪心算法）
        int globalId = 0;
        List<ClusterInPeriod> prevClusters = null;
        for (String dynasty : DYNASTIES) {
            List<ClusterInPeriod> curClusters = periodClusterMap.get(dynasty);
            if (curClusters == null) continue;
            if (prevClusters == null) {
                for (ClusterInPeriod c : curClusters) {
                    ClusterEvolutionDTO evo = createEvolutionDTO(globalId++, c.name, dynasty, c);
                    evolutions.add(evo);
                    c.globalId = evo.getClusterId();
                }
            } else {
                for (ClusterInPeriod cur : curClusters) {
                    ClusterInPeriod bestPrev = findBestMatch(cur, prevClusters, 5.0);
                    if (bestPrev != null) {
                        // 将 bestPrev.globalId 赋值给 final 变量，解决 lambda 表达式变量必须为 final 的问题
                        final int targetGlobalId = bestPrev.globalId;
                        Optional<ClusterEvolutionDTO> optionalEvo = evolutions.stream()
                                .filter(e -> e.getClusterId() == targetGlobalId)
                                .findFirst();
                        if (optionalEvo.isPresent()) {
                            cur.globalId = targetGlobalId;
                            ClusterEvolutionDTO evo = optionalEvo.get();
                            evo.getDynastySnapshots().add(createSnapshot(dynasty, cur));
                        } else {
                            // 兜底：创建新簇
                            ClusterEvolutionDTO newEvo = createEvolutionDTO(globalId++, cur.name, dynasty, cur);
                            evolutions.add(newEvo);
                            cur.globalId = newEvo.getClusterId();
                        }
                    } else {
                        ClusterEvolutionDTO newEvo = createEvolutionDTO(globalId++, cur.name, dynasty, cur);
                        evolutions.add(newEvo);
                        cur.globalId = newEvo.getClusterId();
                    }
                }
            }
            prevClusters = curClusters;
        }
        return evolutions;
    }

    /**
     * 寻找最佳匹配的前一时期簇
     */
    private ClusterInPeriod findBestMatch(ClusterInPeriod cur, List<ClusterInPeriod> prevList, double threshold) {
        ClusterInPeriod best = null;
        double minDist = threshold;
        for (ClusterInPeriod prev : prevList) {
            double d = Math.hypot(cur.centerLng - prev.centerLng, cur.centerLat - prev.centerLat);
            if (d < minDist) {
                minDist = d;
                best = prev;
            }
        }
        return best;
    }

    /**
     * 创建演化DTO（首个朝代簇）
     */
    private ClusterEvolutionDTO createEvolutionDTO(int id, String name, String dynasty, ClusterInPeriod c) {
        ClusterEvolutionDTO evo = new ClusterEvolutionDTO();
        evo.setClusterId(id);
        evo.setClusterName(name);
        evo.setDynastySnapshots(new ArrayList<>());
        evo.getDynastySnapshots().add(createSnapshot(dynasty, c));
        return evo;
    }

    /**
     * 创建朝代快照
     */
    private DynastySnapshot createSnapshot(String dynasty, ClusterInPeriod c) {
        DynastySnapshot snap = new DynastySnapshot();
        snap.setDynasty(dynasty);
        snap.setMemberCount(c.memberCount);
        snap.setCenterLng(c.centerLng);
        snap.setCenterLat(c.centerLat);
        snap.setHeritageIds(c.heritageIds);
        return snap;
    }

    /**
     * 简单聚类（基于经纬度）
     */
    private List<ClusterInPeriod> clusterPointsSimple(List<HeritagePointDTO> points, int k) {
        if (k < 1) k = 1;
        int n = points.size();
        if (k >= n) {
            List<ClusterInPeriod> list = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                HeritagePointDTO p = points.get(i);
                ClusterInPeriod c = new ClusterInPeriod();
                c.memberCount = 1;
                c.centerLng = p.getLng();
                c.centerLat = p.getLat();
                c.heritageIds = Collections.singletonList(p.getId());
                c.name = p.getClusterName() != null ? p.getClusterName() : "簇";
                list.add(c);
            }
            return list;
        }

        double[][] data = points.stream()
                .map(p -> new double[]{p.getLng(), p.getLat()})
                .toArray(double[][]::new);
        int[] labels = simpleKMeans(data, k);
        Map<Integer, List<HeritagePointDTO>> groups = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            groups.computeIfAbsent(labels[i], g -> new ArrayList<>()).add(points.get(i));
        }
        List<ClusterInPeriod> clusters = new ArrayList<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : groups.entrySet()) {
            ClusterInPeriod c = new ClusterInPeriod();
            c.memberCount = entry.getValue().size();
            c.centerLng = entry.getValue().stream().mapToDouble(HeritagePointDTO::getLng).average().orElse(0);
            c.centerLat = entry.getValue().stream().mapToDouble(HeritagePointDTO::getLat).average().orElse(0);
            c.heritageIds = entry.getValue().stream().map(HeritagePointDTO::getId).collect(Collectors.toList());
            c.name = "簇" + entry.getKey();
            clusters.add(c);
        }
        return clusters;
    }

    /**
     * 简单 K-Means 算法实现
     */
    private int[] simpleKMeans(double[][] data, int k) {
        int n = data.length;
        Random rand = new Random(42);
        double[][] centroids = new double[k][2];
        for (int i = 0; i < k; i++) {
            centroids[i] = data[rand.nextInt(n)].clone();
        }
        int[] labels = new int[n];
        boolean changed;
        int iter = 0;
        do {
            changed = false;
            for (int i = 0; i < n; i++) {
                int best = 0;
                double bestDist = Math.hypot(data[i][0] - centroids[0][0], data[i][1] - centroids[0][1]);
                for (int c = 1; c < k; c++) {
                    double d = Math.hypot(data[i][0] - centroids[c][0], data[i][1] - centroids[c][1]);
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
            double[][] sums = new double[k][2];
            int[] counts = new int[k];
            for (int i = 0; i < n; i++) {
                int c = labels[i];
                sums[c][0] += data[i][0];
                sums[c][1] += data[i][1];
                counts[c]++;
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] > 0) {
                    centroids[c][0] = sums[c][0] / counts[c];
                    centroids[c][1] = sums[c][1] / counts[c];
                }
            }
            iter++;
        } while (changed && iter < 100);
        return labels;
    }

    /**
     * 内部类：表示一个时期内的簇
     */
    static class ClusterInPeriod {
        int globalId;
        String name;
        int memberCount;
        double centerLng, centerLat;
        List<Long> heritageIds;
    }
}