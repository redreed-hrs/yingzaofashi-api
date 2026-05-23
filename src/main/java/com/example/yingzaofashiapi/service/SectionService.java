package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.SectionRepository;
import com.example.yingzaofashiapi.entity.Section;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SectionService {

    private final SectionRepository sectionRepository;

    // 原有：根据章节ID查询正文
    public List<Section> getSectionsByChapterId(Long chapterId) {
        return sectionRepository.findByChapterIdOrderBySortAsc(chapterId);
    }

    // 新增：全文搜索（分页）
    public Page<Section> searchSections(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Page.empty(pageable);
        }
        return sectionRepository.searchByKeyword(keyword.trim(), pageable);
    }

    // 新增：随机获取一条正文
    @Cacheable(value = "randomSection", unless = "#result == null")
    public Section getRandomSection() {
        return sectionRepository.findRandomSection();
    }
}