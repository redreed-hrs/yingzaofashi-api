package com.example.yingzaofashiapi.dao;

import lombok.Data;
import java.util.List;

@Data
public class DynastySnapshot {
    private String dynasty;
    private Integer memberCount;
    private Double centerLng;
    private Double centerLat;
    private List<Long> heritageIds;
}