package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.InvestmentQuote;
import com.collect.finance.dto.StockCatalogItem;
import com.collect.finance.dto.StockCatalogPage;
import com.collect.finance.dto.StockCatalogSyncResult;
import com.collect.finance.entity.StockCatalog;
import com.collect.finance.mapper.StockCatalogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StockCatalogMapper catalogMapper;
    private final StockCatalogSyncClient syncClient;
    private final StockCatalogPersistenceService persistenceService;
    private final StockProfileClient profileClient;
    private final TencentQuoteClient quoteClient;

    public StockCatalogService(StockCatalogMapper catalogMapper, StockCatalogSyncClient syncClient,
                               StockCatalogPersistenceService persistenceService, StockProfileClient profileClient,
                               TencentQuoteClient quoteClient) {
        this.catalogMapper = catalogMapper;
        this.syncClient = syncClient;
        this.persistenceService = persistenceService;
        this.profileClient = profileClient;
        this.quoteClient = quoteClient;
    }

    public StockCatalogPage page(String rawKeyword, String rawMarket, int rawPage, int rawPageSize) {
        String keyword = rawKeyword == null || rawKeyword.isBlank() ? null : rawKeyword.trim();
        String market = rawMarket == null || rawMarket.isBlank() ? null : rawMarket.trim();
        if (market != null && !List.of("上海", "深圳", "北京").contains(market)) {
            throw new BizException("股票市场仅支持上海、深圳或北京");
        }
        int page = Math.max(rawPage, 1);
        int pageSize = Math.min(Math.max(rawPageSize, 1), MAX_PAGE_SIZE);
        long offset = (long) (page - 1) * pageSize;
        StockCatalogPage result = new StockCatalogPage();
        result.setItems(catalogMapper.selectCatalogPage(keyword, market, pageSize, offset));
        result.setTotal(catalogMapper.countCatalog(keyword, market));
        return result;
    }

    public StockCatalogItem detail(String rawCode) {
        String code = normalizeCode(rawCode);
        StockCatalogItem item = catalogMapper.selectCatalogItem(code);
        if (item == null) {
            throw new BizException("股票目录中没有该股票，请先同步股票列表");
        }
        if (item.getProfileUpdatedAt() == null) {
            profileClient.enrich(item);
            catalogMapper.upsertStockProfile(item);
            item = catalogMapper.selectCatalogItem(code);
        }
        attachQuote(item);
        return item;
    }

    public StockCatalogItem refreshProfile(String rawCode) {
        String code = normalizeCode(rawCode);
        StockCatalogItem item = catalogMapper.selectCatalogItem(code);
        if (item == null) {
            throw new BizException("股票目录中没有该股票，请先同步股票列表");
        }
        profileClient.enrich(item);
        catalogMapper.upsertStockProfile(item);
        item = catalogMapper.selectCatalogItem(code);
        attachQuote(item);
        return item;
    }

    public StockCatalogSyncResult sync() {
        LocalDateTime syncedAt = LocalDateTime.now();
        List<StockCatalog> items = syncClient.fetchCatalog(syncedAt);
        persistenceService.replaceCatalog(items);
        StockCatalogSyncResult result = new StockCatalogSyncResult();
        result.setTotal(items.size());
        result.setSyncedAt(syncedAt);
        return result;
    }

    private void attachQuote(StockCatalogItem item) {
        InvestmentQuote quote = quoteClient.getQuote(item.getCode());
        item.setCurrentPrice(quote.getCurrentPrice());
        item.setPreviousClose(quote.getPreviousClose());
        item.setChangePercent(quote.getChangePercent());
        item.setQuoteTime(quote.getQuoteTime());
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BizException("股票代码必须为 6 位数字");
        }
        return code;
    }
}
