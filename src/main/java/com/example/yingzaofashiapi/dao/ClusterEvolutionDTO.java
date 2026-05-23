package com.example.yingzaofashiapi.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterEvolutionDTO {
    private Integer clusterId;
    private String clusterName;
    private List<DynastySnapshot> dynastySnapshots;
    private Map<String, Double> avgFeature;
}