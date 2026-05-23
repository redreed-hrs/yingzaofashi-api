package com.example.yingzaofashiapi.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DynastySnapshot {
    private String dynasty;      // 朝代名
    private Integer count;       // 该簇在该朝代的文物数量
    private Double centroidLng;  // 质心经度
    private Double centroidLat;  // 质心纬度
    private String mainType;     // 主要建筑类型
}