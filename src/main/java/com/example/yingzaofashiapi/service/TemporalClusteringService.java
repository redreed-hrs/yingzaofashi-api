package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TemporalClusteringService {

    private final EnhancedClusteringService clusteringService;

    private static final List<String> DYNASTY_ORDER = Arrays.asList(
            "旧石器时代", "新石器时代", "夏", "商", "西周", "战国", "秦", "汉",
            "三国", "晋", "南北朝", "隋", "唐", "五代", "宋", "辽", "金", "元", "明", "清"
    );

    /**
     * 获取每个簇在不同朝代的演化轨迹
     */
    public List<ClusterEvolutionDTO> getClusterEvolution(int k) {
        // 1. 获得所有文物点及其聚类标签（使用增强聚类）
        Map<HeritagePointDTO, Integer> pointLabelMap = clusteringService.getPointsWithClusterLabels(k);
        if (pointLabelMap.isEmpty()) return Collections.emptyList();

        // 2. 按簇分组
        Map<Integer, List<HeritagePointDTO>> clusterGroups = new HashMap<>();
        for (Map.Entry<HeritagePointDTO, Integer> entry : pointLabelMap.entrySet()) {
            clusterGroups.computeIfAbsent(entry.getValue(), c -> new ArrayList<>()).add(entry.getKey());
        }

        // 3. 计算每个簇的演化数据
        List<ClusterEvolutionDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<HeritagePointDTO>> entry : clusterGroups.entrySet()) {
            int cid = entry.getKey();
            List<HeritagePointDTO> members = entry.getValue();
            String clusterName = members.get(0).getClusterName(); // 同簇名称相同

            // 按朝代统计
            Map<String, List<HeritagePointDTO>> dynastyMap = new HashMap<>();
            for (HeritagePointDTO p : members) {
                String dynasty = extractMainDynasty(p.getPeriod());
                dynastyMap.computeIfAbsent(dynasty, d -> new ArrayList<>()).add(p);
            }

            List<DynastySnapshot> snapshots = new ArrayList<>();
            for (String dyn : DYNASTY_ORDER) {
                List<HeritagePointDTO> list = dynastyMap.getOrDefault(dyn, Collections.emptyList());
                int count = list.size();
                double avgLng = list.stream().mapToDouble(HeritagePointDTO::getLng).average().orElse(0);
                double avgLat = list.stream().mapToDouble(HeritagePointDTO::getLat).average().orElse(0);
                String mainType = list.stream()
                        .collect(Collectors.groupingBy(p -> p.getType() == null ? "其他" : p.getType(),
                                Collectors.counting()))
                        .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey)
                        .orElse("未知");
                snapshots.add(new DynastySnapshot(dyn, count, avgLng, avgLat, mainType));
            }
            result.add(new ClusterEvolutionDTO(cid, clusterName, snapshots, null));
        }
        return result;
    }

    private String extractMainDynasty(String period) {
        if (period == null) return "未知";
        for (String dyn : DYNASTY_ORDER) {
            if (period.contains(dyn)) return dyn;
        }
        return "未知";
    }
}