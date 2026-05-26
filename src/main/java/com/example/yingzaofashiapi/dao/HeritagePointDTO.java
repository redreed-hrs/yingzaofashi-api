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
    private String type;
    private String province;
    private Double lng;
    private Double lat;
    private Integer clusterId;
    private String clusterName;
    private String batch; // 新增
}