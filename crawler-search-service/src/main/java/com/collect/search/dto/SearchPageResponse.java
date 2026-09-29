package com.collect.search.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchPageResponse {

    private List<SearchResult> content;
    private long total;
    private String pitId;
}
