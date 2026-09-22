package com.collect.search.controller;

import com.alibaba.fastjson2.JSON;
import com.collect.common.exception.BizException;
import com.collect.common.result.R;
import com.collect.common.security.LoginUtils;
import com.collect.search.dto.SearchResult;
import com.collect.search.dto.SearchPageResponse;
import com.collect.search.entity.SearchHistory;
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

    @Operation(summary = "搜索历史")
    @GetMapping("/history")
    public R<List<SearchHistory>> history() {
        requirePermission("search:query");
        return R.ok(searchService.history());
    }

    @Operation(summary = "同步本地搜索历史")
    @PostMapping("/history/sync")
    public R<Void> syncHistory(@RequestBody List<String> keywords) {
        requirePermission("search:query");
        searchService.syncHistory(keywords);
        return R.ok();
    }

    @Operation(summary = "清空搜索历史")
    @DeleteMapping("/history")
    public R<Void> clearHistory() {
        requirePermission("search:query");
        searchService.clearHistory();
        return R.ok();
    }

    @Operation(summary = "删除单条搜索历史")
    @DeleteMapping("/history/item")
    public R<Void> deleteHistory(@RequestParam("keyword") String keyword) {
        requirePermission("search:query");
        searchService.deleteHistory(keyword);
        return R.ok();
    }

    @Operation(summary = "搜索爬取的数据（PIT + search_after 游标分页）")
    @GetMapping
    public R<SearchPageResponse> search(@RequestParam(value = "keyword", required = false) String keyword,
                                              @RequestParam(value = "spiderId", required = false) Long spiderId,
                                              @RequestParam(value = "spiderGroup", required = false) String spiderGroup,
                                              @RequestParam(value = "tag", required = false) String tag,
                                              @RequestParam(value = "favoriteOnly", defaultValue = "false") boolean favoriteOnly,
                                              @RequestParam(value = "hasImages", defaultValue = "false") boolean hasImages,
                                              @RequestParam(value = "size", defaultValue = "20") int size,
                                              @RequestParam(value = "pitId", required = false) String pitId,
                                              @RequestParam(value = "searchAfter", required = false) String searchAfter) {
        // 只读查询允许匿名访问（公开搜索页），登录用户仍需具备 search:query 权限
        requirePermissionIfLoggedIn("search:query");
        searchService.recordHistory(keyword);
        java.util.List<Object> after = null;
        if (searchAfter != null && !searchAfter.isBlank()) {
            if (searchAfter.trim().startsWith("[")) {
                try {
                    after = JSON.parseArray(searchAfter).stream()
                            .map(value -> value)
                            .collect(java.util.stream.Collectors.toList());
                } catch (RuntimeException ignored) {
                    // Fall back to the legacy comma-separated cursor below.
                }
            }
            if (after == null) {
                after = java.util.Arrays.stream(searchAfter.split(","))
                    .map(value -> value.trim())
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }
        }
        Page<SearchResult> result = searchService.search(keyword, spiderId, spiderGroup, tag, favoriteOnly, hasImages, size, pitId, after);
        String nextPitId = result instanceof SearchService.PagedSearchResult paged ? paged.getPitId() : null;
        return R.ok(new SearchPageResponse(result.getContent(), result.getTotalElements(), nextPitId));
    }

    @Operation(summary = "数据详情")
    @GetMapping("/{id}")
    public R<SpiderContentDoc> detail(@PathVariable("id") String id) throws IOException {
        // 只读查询允许匿名访问（公开搜索页），登录用户仍需具备 search:query 权限
        requirePermissionIfLoggedIn("search:query");
        return R.ok(searchService.getById(id));
    }

    @Operation(summary = "更新数据标签")
    @PutMapping("/{id}/tags")
    public R<Void> updateTags(@PathVariable("id") String id, @RequestBody List<String> tags) throws IOException {
        requirePermission("search:query");
        searchService.updateTags(id, tags);
        return R.ok();
    }

    @Operation(summary = "我的收藏")
    @GetMapping("/favorites")
    public R<Page<SearchResult>> favorites(@RequestParam(value = "current", defaultValue = "1") int current,
                                           @RequestParam(value = "size", defaultValue = "20") int size,
                                           @RequestParam(value = "title", required = false) String title,
                                           @RequestParam(value = "url", required = false) String url,
                                           @RequestParam(value = "spiderName", required = false) String spiderName,
                                           @RequestParam(value = "tag", required = false) String tag) throws IOException {
        requirePermission("search:query");
        return R.ok(searchService.favorites(current, size, title, url, spiderName, tag));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorites/{contentId}")
    public R<Void> deleteFavorite(@PathVariable String contentId) {
        requirePermission("search:query");
        searchService.deleteFavorite(contentId);
        return R.ok();
    }

    @Operation(summary = "收藏数据")
    @PostMapping("/favorites/{contentId}")
    public R<Void> favorite(@PathVariable String contentId) throws IOException {
        requirePermission("search:query");
        searchService.favorite(contentId);
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

    /**
     * 仅当存在登录用户时校验权限；匿名访问（无登录用户）直接放行。
     * 用于允许公开页面匿名读取，同时保证登录用户仍需具备相应权限。
     */
    private void requirePermissionIfLoggedIn(String code) {
        try {
            LoginUtils.getLoginUser();
        } catch (Exception e) {
            // 未登录（匿名访问），放行
            return;
        }
        if (!LoginUtils.hasPermission(code)) {
            throw new BizException("无权限执行该操作");
        }
    }
}
