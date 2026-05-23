package com.example.yingzaofashiapi.entity;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "section")
public class Section {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long chapterId;
    private String title;
    @Column(columnDefinition = "longtext")  // 支持大文本（古籍全文）
    private String original;
    @Column(columnDefinition = "longtext")
    private String explanation;
    @Column(columnDefinition = "longtext")
    private String translation;
    private Integer sort;
}