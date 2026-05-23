package com.example.yingzaofashiapi.dao;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeritageDetailDTO {
    private String classNo;      // 分类号
    private String name;         // 单位名称（中文）
    private String period;       // 时代（中文）
    private String address;      // 地址（中文）
    private String type;         // 类型（中文）
    private String batch;        // 批次（中文）
    private String province;     // 省级政区
    private String city;         // 市级政区
    private String county;       // 县级政区
}