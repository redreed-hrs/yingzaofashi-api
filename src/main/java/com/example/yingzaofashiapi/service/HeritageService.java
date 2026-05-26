package com.example.yingzaofashiapi.service;

import com.example.yingzaofashiapi.Repository.HeritageRepository;
import com.example.yingzaofashiapi.dao.HeritageDetailDTO;
import com.example.yingzaofashiapi.dao.HeritagePointDTO;
import com.example.yingzaofashiapi.entity.Heritage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeritageService {

    private final HeritageRepository heritageRepository;

    // 按朝代查询（前缀匹配，中华人民共和国特殊处理）
    public List<HeritagePointDTO> findPointsByPeriod(String period) {
        List<Heritage> list;
        if ("中华人民共和国".equals(period)) {
            list = heritageRepository.findModernChinaHeritage();
        } else {
            list = heritageRepository.findByPeriodStartingWith(period);
        }
        return list.stream()
                .filter(h -> h.getLongitude() != null && h.getLatitude() != null)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 按 period_code 范围查询
    public List<HeritagePointDTO> findPointsByPeriodRange(int startCode, int endCode) {
        List<Heritage> list = heritageRepository.findByPeriodCodeBetween(startCode, endCode);
        return list.stream()
                .filter(h -> h.getLongitude() != null && h.getLatitude() != null)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有文保点（包含有效经纬度），供全量地图展示或聚类使用
     */
    public List<HeritagePointDTO> getAllHeritagePoints() {
        List<Heritage> list = heritageRepository.findAll();
        return list.stream()
                .filter(h -> h.getLongitude() != null && h.getLatitude() != null)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 省份详情查询
    public List<HeritageDetailDTO> findDetailByProvince(String province) {
        List<Object[]> rows = heritageRepository.findDetailByProvince(province);
        return rows.stream().map(row -> new HeritageDetailDTO(
                (String) row[0],  // class_no
                (String) row[1],  // name
                (String) row[2],  // period
                (String) row[3],  // address
                (String) row[4],  // type
                (String) row[5],  // batch
                (String) row[6],  // province
                (String) row[7],  // city
                (String) row[8]   // county
        )).collect(Collectors.toList());
    }

    public List<HeritagePointDTO> getAllHeritagePointsForClustering() {
        return getAllHeritagePoints(); // 复用已有方法
    }

    // 将 Heritage 实体转换为 HeritagePointDTO（使用8参数构造器）
    private HeritagePointDTO convertToDTO(Heritage h) {
        return new HeritagePointDTO(
                h.getId(), h.getName(), h.getPeriod(), h.getType(), h.getProvince(),
                h.getLongitude(), h.getLatitude(), -1, null,
                h.getBatch()  // 新增，需要在 Heritage 实体中添加 batch 字段
        );
    }
}