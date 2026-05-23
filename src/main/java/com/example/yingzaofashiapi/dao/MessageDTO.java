package com.example.yingzaofashiapi.dao;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDTO {
    private Long id;
    private String nickname;
    private String content;
    private LocalDateTime createTime;
    
    // 格式化的时间字符串，用于前端展示
    private String timeAgo;
}