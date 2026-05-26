package com.example.yingzaofashiapi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class BudgetService {

    // 存储结构：省份 -> 年份 -> 预算金额(万元)
    private final Map<String, Map<Integer, Integer>> provinceBudgetMap = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            ClassPathResource resource = new ClassPathResource("yearlyBudgetData.json");
            InputStream is = resource.getInputStream();
            // 显式指定类型参数，避免 Java 8 菱形运算符与匿名内部类的冲突
            Map<String, Map<String, Integer>> rawData = mapper.readValue(is,
                    new TypeReference<Map<String, Map<String, Integer>>>() {});

            for (Map.Entry<String, Map<String, Integer>> yearEntry : rawData.entrySet()) {
                int year = Integer.parseInt(yearEntry.getKey());
                for (Map.Entry<String, Integer> provEntry : yearEntry.getValue().entrySet()) {
                    String prov = normalizeProvince(provEntry.getKey());
                    provinceBudgetMap.computeIfAbsent(prov, k -> new HashMap<>()).put(year, provEntry.getValue());
                }
            }
            log.info("成功加载 {} 个省份的 {} 年预算数据", provinceBudgetMap.size(), rawData.size());
        } catch (Exception e) {
            log.error("加载预算数据失败", e);
        }
    }

    private String normalizeProvince(String raw) {
        if (raw.contains("（不含")) {
            return raw.substring(0, raw.indexOf("（"));
        }
        // 统一处理特殊名称
        switch (raw) {
            case "辽宁（不含大连）":
            case "辽宁":
                return "辽宁";
            case "浙江（不含宁波）":
            case "浙江":
                return "浙江";
            case "福建（不含厦门）":
            case "福建":
                return "福建";
            case "山东（不含青岛）":
            case "山东":
                return "山东";
            case "广东（不含深圳）":
            case "广东":
                return "广东";
            case "新疆生产建设兵团":
                return "新疆";
            default:
                return raw;
        }
    }


    public int getBudgetForProvince(String province, int year) {
        Map<Integer, Integer> yearMap = provinceBudgetMap.get(province);
        if (yearMap == null) {
            log.debug("未找到省份 {} 的预算数据", province);
            return 0;
        }
        return yearMap.getOrDefault(year, yearMap.getOrDefault(2025, 0));
    }


}