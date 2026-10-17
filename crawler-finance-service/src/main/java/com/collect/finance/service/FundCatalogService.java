package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.FundCatalogItem;
import com.collect.finance.dto.FundCatalogPage;
import com.collect.finance.dto.FundCatalogSyncResult;
import com.collect.finance.entity.FundCatalog;
import com.collect.finance.mapper.FundCatalogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class FundCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FundCatalogMapper catalogMapper;
    private final FundCatalogSyncClient syncClient;
    private final FundCatalogPersistenceService persistenceService;
    private final EastmoneyFundProfileClient profileClient;

    public FundCatalogService(FundCatalogMapper catalogMapper, FundCatalogSyncClient syncClient,
                              FundCatalogPersistenceService persistenceService,
                              EastmoneyFundProfileClient profileClient) {
        this.catalogMapper = catalogMapper;
        this.syncClient = syncClient;
        this.persistenceService = persistenceService;
        this.profileClient = profileClient;
    }

    public FundCatalogPage page(String rawKeyword, String rawFundType, int rawPage, int rawPageSize) {
        String keyword = rawKeyword == null || rawKeyword.isBlank() ? null : rawKeyword.trim();
        String fundType = normalizeFundType(rawFundType, true);
        int page = Math.max(rawPage, 1);
        int pageSize = Math.min(Math.max(rawPageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (page - 1) * pageSize;
        FundCatalogPage result = new FundCatalogPage();
        result.setItems(catalogMapper.selectCatalogPage(keyword, fundType, pageSize, offset));
        result.setTotal(catalogMapper.countCatalog(keyword, fundType));
        return result;
    }

    public FundCatalogItem detail(String rawFundType, String rawCode) {
        String fundType = normalizeFundType(rawFundType, false);
        String code = normalizeCode(rawCode);
        FundCatalogItem item = catalogMapper.selectCatalogItem(fundType, code);
        if (item == null) {
            throw new BizException("基金目录中没有该基金，请先同步基金列表");
        }
        if (item.getProfileUpdatedAt() == null) {
            profileClient.enrich(code, item);
            catalogMapper.upsertFundProfile(item);
            item = catalogMapper.selectCatalogItem(fundType, code);
        }
        return item;
    }

    public FundCatalogItem refreshProfile(String rawFundType, String rawCode) {
        String fundType = normalizeFundType(rawFundType, false);
        String code = normalizeCode(rawCode);
        FundCatalogItem item = catalogMapper.selectCatalogItem(fundType, code);
        if (item == null) {
            throw new BizException("基金目录中没有该基金，请先同步基金列表");
        }
        profileClient.enrich(code, item);
        catalogMapper.upsertFundProfile(item);
        return catalogMapper.selectCatalogItem(fundType, code);
    }

    public FundCatalogSyncResult sync() {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<FundCatalog> items = syncClient.fetchCatalog(syncedAt);
        persistenceService.replaceCatalog(items);

        FundCatalogSyncResult result = new FundCatalogSyncResult();
        result.setTotal(items.size());
        result.setOnshore((int) items.stream().filter(item -> "ONSHORE".equals(item.getFundType())).count());
        result.setOffshore(items.size() - result.getOnshore());
        result.setSyncedAt(syncedAt);
        return result;
    }

    private String normalizeFundType(String rawFundType, boolean optional) {
        if (rawFundType == null || rawFundType.isBlank()) {
            if (optional) {
                return null;
            }
            throw new BizException("基金类型不能为空");
        }
        String fundType = rawFundType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ONSHORE", "OFFSHORE").contains(fundType)) {
            throw new BizException("基金类型仅支持 ONSHORE 或 OFFSHORE");
        }
        return fundType;
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BizException("基金代码必须为 6 位数字");
        }
        return code;
    }
}
