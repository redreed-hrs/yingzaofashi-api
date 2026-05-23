package com.example.yingzaofashiapi.dao;

import com.example.yingzaofashiapi.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, Long> {
    List<Chapter> findByBookIdOrderBySortAsc(Long bookId);
}