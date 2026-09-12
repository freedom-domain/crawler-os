package com.collect.search.controller;

import com.collect.common.result.R;
import com.collect.search.dto.SearchResult;
import com.collect.search.es.SpiderContentDoc;
import com.collect.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "数据搜索")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "搜索爬取的数据")
    @GetMapping
    public R<Page<SearchResult>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "spiderId", required = false) Long spiderId,
                                              @RequestParam(value = "current", defaultValue = "1") int current,
                                              @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(searchService.search(keyword, spiderId, current, size));
    }

    @Operation(summary = "数据详情")
    @GetMapping("/{id}")
    public R<SpiderContentDoc> detail(@PathVariable("id") String id) throws IOException {
        return R.ok(searchService.getById(id));
    }

    @Operation(summary = "删除数据")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") String id) throws IOException {
        searchService.delete(id);
        return R.ok();
    }
}
