package com.example.yingzaofashiapi.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "heritage")
public class Heritage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String period;
    private String type;
    @Column(name = "period_code")
    private Integer periodCode;
    private String province;
    private String city;
    private String county;
    private Double longitude;
    private Double latitude;

    @Column(name = "batch")
    private String batch;      // 新增：保护批次（如“第一批全国重点文物保护单位”）
}