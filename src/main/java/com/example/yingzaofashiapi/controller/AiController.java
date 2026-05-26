package com.example.yingzaofashiapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/ai")
public class AiController {

    // 腾讯混元 hy3-preview 接口地址
    private static final String API_URL = "https://tokenhub.tencentmaas.com/v1/chat/completions";
    private static final String MODEL_NAME = "hy3-preview";

    @Value("${hy3.api.key:}")
    private String apiKey;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, Object> payload) {
        Map<String, String> result = new HashMap<>();
        try {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("未配置腾讯混元 API Key");
            }

            String userMessage = (String) payload.get("message");
            @SuppressWarnings("unchecked")
            Map<String, Object> context = (Map<String, Object>) payload.get("context");

            String systemPrompt = buildSystemPrompt(context);

            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("messages", messages);
            requestBody.put("stream", false);

            String jsonBody = mapper.writeValueAsString(requestBody);
            String reply = sendHttpRequest(API_URL, jsonBody);
            result.put("reply", reply);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("reply", "抱歉，AI 服务暂时不可用：" + e.getMessage());
        }
        return result;
    }

    private String sendHttpRequest(String urlString, String jsonBody) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    errorResponse.append(line);
                }
            }
            throw new RuntimeException("HTTP 错误码: " + responseCode + ", 响应: " + errorResponse);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> respMap = mapper.readValue(response.toString(), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
        return "未获得有效回复";
    }

    private String buildSystemPrompt(Map<String, Object> ctx) {
        return "你是中国古建筑保护领域的专家助手，同时也是一位乐于助人的通用助手。\n" +
                "以下为本平台提供的 JSON 数据摘要（供你在回答古建筑相关问题时引用）：\n" +
                "- 各省古建筑数量：" + ctx.get("provinceCounts") + "\n" +
                "- 各朝代数量：" + ctx.get("dynastyCounts") + "\n" +
                "- 历年预算年份：" + ctx.get("budgetYears") + "\n" +
                "回答要求：\n" +
                "1. 如果用户问题与古建筑、文物保护、预算、朝代、各省数量相关，请优先基于以上数据回答，并尽量引用具体数字。\n" +
                "2. 如果用户问的是其他问题（例如自我介绍、天气、常识、历史、文化等），请使用你的通用知识友好回答，不要拒绝回答。\n" +
                "3. 回答要详细、专业、自然，使用中文。";
    }
}