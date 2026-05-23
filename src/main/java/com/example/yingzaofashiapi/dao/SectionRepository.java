package com.example.yingzaofashiapi.dao;

import com.example.yingzaofashiapi.entity.Section;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {

    // 原有：根据章节ID查询，按排序字段升序
    List<Section> findByChapterIdOrderBySortAsc(Long chapterId);

    // 新增：全文搜索（忽略大小写，匹配任意字段）
    @Query("SELECT s FROM Section s WHERE " +
            "LOWER(s.original) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.explanation) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(s.translation) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Section> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    // 新增：随机获取一条记录（使用原生SQL，效率较高）
    @Query(value = "SELECT * FROM section ORDER BY RAND() LIMIT 1", nativeQuery = true)
    Section findRandomSection();

    // 统计某章节下的 section 数量
    long countByChapterId(Long chapterId);
}