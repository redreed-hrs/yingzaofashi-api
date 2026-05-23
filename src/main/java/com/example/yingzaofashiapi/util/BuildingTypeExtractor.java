package com.example.yingzaofashiapi.util;

import java.util.Arrays;
import java.util.List;

/**
 * 从文物名称中提取细粒度建筑类型（如寺、塔、陵等）
 */
public class BuildingTypeExtractor {

    private static final List<String> KEYWORDS = Arrays.asList(
        "寺", "塔", "陵", "墓", "桥", "窑", "城", "阙", "庙", "宫",
        "观", "祠", "堂", "阁", "楼", "台", "牌坊", "宅", "庄园", "窟"
    );

    public static String extractFineType(String name) {
        if (name == null || name.isEmpty()) return "其他";
        for (String kw : KEYWORDS) {
            if (name.contains(kw)) {
                return kw;
            }
        }
        return "其他";
    }
}