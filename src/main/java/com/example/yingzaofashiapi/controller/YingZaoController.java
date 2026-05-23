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

import java.util.List;

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
    private final TemporalClusteringService temporalClusteringService;   // 新增注入

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

    // 时空演化接口
    @GetMapping("/heritage/cluster-evolution")
    public List<ClusterEvolutionDTO> getClusterEvolution(@RequestParam(defaultValue = "-1") int k) {
        return temporalClusteringService.getClusterEvolution(k);
    }

    // 获取最近留言（默认50条）
    @GetMapping("/messages/recent")
    public List<com.example.yingzaofashiapi.dao.MessageDTO> getRecentMessages(@RequestParam(defaultValue = "50") int limit) {
        return messageService.getRecentMessages(limit);
    }

    // 提交留言
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

    // 获取全量文保数据
    @GetMapping("/heritage/all")
    public List<HeritagePointDTO> getAllHeritage() {
        return heritageService.getAllHeritagePoints();
    }

    @GetMapping("/heritage/detail/by-province")
    public List<HeritageDetailDTO> getHeritageDetailByProvince(@RequestParam String province) {
        return heritageService.findDetailByProvince(province);
    }

    @GetMapping("/random-section")
    public Section getRandomSection() {
        return sectionService.getRandomSection();
    }
}