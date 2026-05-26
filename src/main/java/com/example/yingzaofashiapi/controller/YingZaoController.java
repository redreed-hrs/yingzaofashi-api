package com.example.yingzaofashiapi.controller;

import com.example.yingzaofashiapi.dao.ChapterWithCountDTO;
import com.example.yingzaofashiapi.dao.ClusterEvolutionDTO;
import com.example.yingzaofashiapi.dao.HeritageDetailDTO;
import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import com.example.yingzaofashiapi.entity.Section;
import com.example.yingzaofashiapi.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
@RequiredArgsConstructor
public class YingZaoController {

    private final ChapterService chapterService;
    private final SectionService sectionService;
    private final HeritageService heritageService;
    private final EnhancedClusteringService enhancedClusteringService;
    private final MessageService messageService;
    private final ClusteringService clusteringService;
    private final TemporalClusteringService temporalClusteringService;
    private final BudgetService budgetService;

    @GetMapping("/heritage/enhanced-clusters")
    public List<HeritagePointDTO> getEnhancedClusters(@RequestParam(defaultValue = "-1") int k) {
        return enhancedClusteringService.getHeritagePointsWithEnhancedCluster(k);
    }

    @GetMapping("/chapters")
    public List<ChapterWithCountDTO> getChapters() {
        return chapterService.getChaptersWithCountByBookId(1L);
    }

    @GetMapping("/sections/{chapterId}")
    public List<Section> getSections(@PathVariable Long chapterId) {
        return sectionService.getSectionsByChapterId(chapterId);
    }

    // 时空演化接口（统一使用参数 k，默认-1）
    @GetMapping("/heritage/cluster-evolution")
    public List<ClusterEvolutionDTO> getClusterEvolution(@RequestParam(defaultValue = "-1") int k) {
        return temporalClusteringService.getClusterEvolution(k);
    }

    // 获取最近留言
    @GetMapping("/messages/recent")
    public List<com.example.yingzaofashiapi.dao.MessageDTO> getRecentMessages(@RequestParam(defaultValue = "50") int limit) {
        return messageService.getRecentMessages(limit);
    }

    @PostMapping("/messages")
    public com.example.yingzaofashiapi.dao.MessageDTO postMessage(@RequestParam String nickname,
                                                                  @RequestParam String content) {
        return messageService.saveMessage(nickname, content);
    }

    @GetMapping("/search")
    public Page<Section> searchSections(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("sort").ascending());
        return sectionService.searchSections(keyword, pageable);
    }

    @GetMapping("/heritage/by-period")
    public List<HeritagePointDTO> getHeritageByPeriod(@RequestParam String period) {
        return heritageService.findPointsByPeriod(period);
    }

    @GetMapping("/heritage/by-period-range")
    public List<HeritagePointDTO> getHeritageByPeriodRange(
            @RequestParam int startCode,
            @RequestParam int endCode) {
        return heritageService.findPointsByPeriodRange(startCode, endCode);
    }

    @GetMapping("/heritage/clusters")
    public List<HeritagePointDTO> getHeritageClusters(@RequestParam(defaultValue = "5") int k) {
        return clusteringService.getHeritagePointsWithCluster(k);
    }

    @GetMapping("/heritage/all")
    public List<HeritagePointDTO> getAllHeritage() {
        return heritageService.getAllHeritagePoints();
    }

    @GetMapping("/heritage/detail/by-province")
    public List<HeritageDetailDTO> getHeritageDetailByProvince(@RequestParam String province) {
        return heritageService.findDetailByProvince(province);
    }

    @GetMapping("/clustering/quality")
    public Map<String, Double> getClusteringQuality(@RequestParam(defaultValue = "5") int k) {
        enhancedClusteringService.getHeritagePointsWithEnhancedCluster(k);
        return enhancedClusteringService.getLastQualityMetrics();
    }

    /**
     * 获取各省份遗产数量及对应预算（2025年）
     * 注意：数据库中 province 存储的是全称（如“山西省”），而预算服务使用简称（如“山西”），需要转换。
     */
    @GetMapping("/budget/heritage-stats")
    public Map<String, Object> getBudgetHeritageStats() {
        List<HeritagePointDTO> points = heritageService.getAllHeritagePoints();
        Map<String, Integer> provinceCount = new HashMap<>();
        Map<String, Double> provinceBudget = new HashMap<>();

        for (HeritagePointDTO p : points) {
            String fullName = p.getProvince();   // 全称，如“山西省”
            provinceCount.merge(fullName, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : provinceCount.entrySet()) {
            String fullName = entry.getKey();
            String shortName = normalizeProvinceName(fullName);
            int budget = budgetService.getBudgetForProvince(shortName, 2025);
            provinceBudget.put(fullName, (double) budget);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("provinceCount", provinceCount);
        result.put("provinceBudget", provinceBudget);
        return result;
    }

    @GetMapping("/random-section")
    public Section getRandomSection() {
        return sectionService.getRandomSection();
    }

    /**
     * 将省份全称转换为简称（用于匹配预算数据）
     * 例如：山西省 -> 山西，内蒙古自治区 -> 内蒙古
     */
    private String normalizeProvinceName(String fullName) {
        if (fullName == null) return "";
        String cleaned = fullName.replace("省", "")
                .replace("自治区", "")
                .replace("市", "");
        // 特殊处理
        switch (cleaned) {
            case "新疆维吾尔": return "新疆";
            case "广西壮族": return "广西";
            case "宁夏回族": return "宁夏";
            case "西藏": return "西藏";
            case "内蒙古": return "内蒙古";
            default: return cleaned;
        }
    }
}