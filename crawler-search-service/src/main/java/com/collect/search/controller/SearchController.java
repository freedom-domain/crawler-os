package com.collect.search.controller;

import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import com.collect.search.dto.SearchResult;
import com.collect.search.es.SpiderContentDoc;
import com.collect.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

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
                                              @RequestParam(value = "spiderGroup", required = false) String spiderGroup,
                                              @RequestParam(value = "tag", required = false) String tag,
                                              @RequestParam(value = "current", defaultValue = "1") int current,
                                              @RequestParam(value = "size", defaultValue = "20") int size) {
        requirePermission("search:query");
        return R.ok(searchService.search(keyword, spiderId, spiderGroup, tag, current, size));
    }

    @Operation(summary = "数据详情")
    @GetMapping("/{id}")
    public R<SpiderContentDoc> detail(@PathVariable("id") String id) throws IOException {
        requirePermission("search:query");
        return R.ok(searchService.getById(id));
    }

    @Operation(summary = "更新数据标签")
    @PutMapping("/{id}/tags")
    public R<Void> updateTags(@PathVariable("id") String id, @RequestBody List<String> tags) throws IOException {
        requirePermission("search:query");
        searchService.updateTags(id, tags);
        return R.ok();
    }

    @Operation(summary = "删除数据")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") String id) throws IOException {
        requirePermission("search:query");
        searchService.delete(id);
        return R.ok();
    }

    private void requirePermission(String code) {
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }
}
