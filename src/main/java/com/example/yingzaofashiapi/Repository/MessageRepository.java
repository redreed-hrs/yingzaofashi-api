package com.example.yingzaofashiapi.Repository;

import com.example.yingzaofashiapi.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    
    // 获取最新的 N 条留言（按时间倒序）
    List<Message> findAllByOrderByCreateTimeDesc(Pageable pageable);
    
    // 统计总数
    long count();
}