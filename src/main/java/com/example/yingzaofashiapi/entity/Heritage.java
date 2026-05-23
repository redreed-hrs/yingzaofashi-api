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
    private String type;               // 新增：建筑类型（古遗址/古墓葬/古建筑/石窟寺及石刻等）
    @Column(name = "period_code")
    private Integer periodCode;
    private String province;
    private String city;
    private String county;
    private Double longitude;
    private Double latitude;
}