package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.dao.ChapterRepository;
import com.example.yingzaofashiapi.dao.SectionRepository;
import com.example.yingzaofashiapi.dao.ChapterWithCountDTO;
import com.example.yingzaofashiapi.entity.Chapter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterService {

    private final ChapterRepository chapterRepository;
    private final SectionRepository sectionRepository;

    // 原有方法：根据书籍ID查询章节
    public List<Chapter> getChaptersByBookId(Long bookId) {
        return chapterRepository.findByBookIdOrderBySortAsc(bookId);
    }

    // 新增：获取带 section 数量的章节列表（用于前端展示增强）
    @Cacheable(value = "chaptersWithCount", key = "#bookId")
    public List<ChapterWithCountDTO> getChaptersWithCountByBookId(Long bookId) {
        List<Chapter> chapters = chapterRepository.findByBookIdOrderBySortAsc(bookId);
        return chapters.stream().map(ch -> {
            long count = sectionRepository.countByChapterId(ch.getId());
            return new ChapterWithCountDTO(ch, count);
        }).collect(Collectors.toList());
    }
}