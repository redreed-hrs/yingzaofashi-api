package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.Repository.MessageRepository;
import com.example.yingzaofashiapi.dao.MessageDTO;
import com.example.yingzaofashiapi.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    // 保存留言
    public MessageDTO saveMessage(String nickname, String content) {
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "无名匠";
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("留言内容不能为空");
        }
        Message message = new Message();
        message.setNickname(nickname.trim());
        message.setContent(content.trim());
        Message saved = messageRepository.save(message);
        return convertToDTO(saved);
    }

    // 获取最近 N 条留言（默认50条）
    public List<MessageDTO> getRecentMessages(int limit) {
        Pageable pageable = PageRequest.of(0, Math.min(limit, 200), Sort.by("createTime").descending());
        List<Message> messages = messageRepository.findAllByOrderByCreateTimeDesc(pageable);
        return messages.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    // 实体转DTO，附加相对时间
    private MessageDTO convertToDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setNickname(message.getNickname());
        dto.setContent(message.getContent());
        dto.setCreateTime(message.getCreateTime());
        dto.setTimeAgo(formatTimeAgo(message.getCreateTime()));
        return dto;
    }

    private String formatTimeAgo(LocalDateTime time) {
        Duration duration = Duration.between(time, LocalDateTime.now());
        long seconds = duration.getSeconds();
        if (seconds < 60) return "刚刚";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        if (days < 30) return days + "天前";
        long months = days / 30;
        if (months < 12) return months + "个月前";
        return (months / 12) + "年前";
    }
}