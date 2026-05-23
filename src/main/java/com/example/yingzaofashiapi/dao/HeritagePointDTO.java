package com.example.yingzaofashiapi.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeritagePointDTO {
    private Long id;
    private String name;
    private String period;
    private String type;          // 新增：建筑类型（古遗址/古墓葬/古建筑等）
    private String province;
    private Double lng;
    private Double lat;
    private Integer clusterId = -1;
    private String clusterName;    // 语义聚类标签

    // 保留原有构造器（便于兼容）
    public HeritagePointDTO(String name, String period, String province, Double lng, Double lat) {
        this.name = name;
        this.period = period;
        this.province = province;
        this.lng = lng;
        this.lat = lat;
    }
}