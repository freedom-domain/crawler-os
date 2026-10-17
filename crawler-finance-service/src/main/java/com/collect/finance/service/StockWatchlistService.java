package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.StockHistoryResponse;
import com.collect.finance.dto.StockWatchlistItem;
import com.collect.finance.entity.StockCatalog;
import com.collect.finance.mapper.StockCatalogMapper;
import com.collect.finance.mapper.StockWatchlistMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StockWatchlistService {

    private static final int HISTORY_LOOKBACK_DAYS = 30;

    private final StockCatalogMapper catalogMapper;
    private final StockWatchlistMapper watchlistMapper;
    private final TencentStockHistoryClient historyClient;

    public StockWatchlistService(StockCatalogMapper catalogMapper, StockWatchlistMapper watchlistMapper,
                                 TencentStockHistoryClient historyClient) {
        this.catalogMapper = catalogMapper;
        this.watchlistMapper = watchlistMapper;
        this.historyClient = historyClient;
    }

    public List<StockWatchlistItem> list() {
        return watchlistMapper.selectWatchlist();
    }

    public void add(String rawCode) {
        String code = normalizeCode(rawCode);
        StockCatalog stock = catalogMapper.selectById(code);
        if (stock == null) {
            throw new BizException("股票目录中没有该股票，请先同步股票列表");
        }
        if (watchlistMapper.add(code) == 0) {
            throw new BizException("该股票已在关注清单中");
        }
    }

    public void remove(String rawCode) {
        watchlistMapper.remove(normalizeCode(rawCode));
    }

    public StockHistoryResponse refresh(String rawCode) {
        String code = normalizeCode(rawCode);
        StockCatalog stock = catalogMapper.selectById(code);
        if (stock == null) {
            throw new BizException("股票目录中没有该股票，请先同步股票列表");
        }
        LocalDate endDate = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        return historyClient.getHistory(code, endDate.minusDays(HISTORY_LOOKBACK_DAYS), endDate);
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new BizException("股票代码必须为 6 位数字");
        }
        return code;
    }
}
