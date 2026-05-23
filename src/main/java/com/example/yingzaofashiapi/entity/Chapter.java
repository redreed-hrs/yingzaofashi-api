package com.example.yingzaofashiapi.entity;

import lombok.Data;

import javax.persistence.*;

@Data
@Entity
@Table(name = "chapter")
public class Chapter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long bookId;
    private String title;
    private Integer sort;
}