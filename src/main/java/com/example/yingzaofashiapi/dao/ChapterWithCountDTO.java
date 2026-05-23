package com.example.yingzaofashiapi.dao;

import com.example.yingzaofashiapi.entity.Chapter;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChapterWithCountDTO {
    private Long id;
    private String title;
    private Integer sort;
    private Long sectionCount;   // 该章节下的正文条目数

    public ChapterWithCountDTO(Chapter chapter, Long sectionCount) {
        this.id = chapter.getId();
        this.title = chapter.getTitle();
        this.sort = chapter.getSort();
        this.sectionCount = sectionCount;
    }
}