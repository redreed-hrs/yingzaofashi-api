package com.example.yingzaofashiapi.dao;

import com.example.yingzaofashiapi.entity.Section;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class SearchResultDTO {
    private List<Section> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
}