package com.collect.spider.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpiderImportResult {

    private int imported;
    private int skipped;
    private int failed;
}
