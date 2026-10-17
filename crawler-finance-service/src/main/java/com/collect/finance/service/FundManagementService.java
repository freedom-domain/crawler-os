package com.collect.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.finance.dto.FundManagementItem;
import com.collect.finance.dto.FundWatchlistRequest;
import com.collect.finance.entity.FundHistoryCacheKey;
import com.collect.finance.entity.FundHistoryCacheRecord;
import com.collect.finance.entity.FundWatchlist;
import com.collect.finance.mapper.FundHistoryCacheMapper;
import com.collect.finance.mapper.FundWatchlistMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@Service
public class FundManagementService {

    private static final String ONSHORE = "ONSHORE";
    private static final String OFFSHORE = "OFFSHORE";
    private static final int OFFSHORE_LOOKBACK_DAYS = 30;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final FundWatchlistMapper watchlistMapper;
    private final FundHistoryCacheMapper cacheMapper;
    private final FundHistoryCacheService cacheService;
    private final TencentFundHistoryClient onshoreClient;
    private final EastmoneyOffExchangeFundClient offshoreClient;

    public FundManagementService(FundWatchlistMapper watchlistMapper, FundHistoryCacheMapper cacheMapper,
                                FundHistoryCacheService cacheService, TencentFundHistoryClient onshoreClient,
                                EastmoneyOffExchangeFundClient offshoreClient) {
        this.watchlistMapper = watchlistMapper;
        this.cacheMapper = cacheMapper;
        this.cacheService = cacheService;
        this.onshoreClient = onshoreClient;
        this.offshoreClient = offshoreClient;
    }

    public List<FundManagementItem> list() {
        return cacheMapper.selectManagementItems();
    }

    public void addToWatchlist(FundWatchlistRequest request) {
        FundHistoryCacheKey key = validateKey(request.getFundType(), request.getCode(), request.getFrequency());
        long existing = watchlistMapper.selectCount(new LambdaQueryWrapper<FundWatchlist>()
                .eq(FundWatchlist::getFundType, key.getFundType())
                .eq(FundWatchlist::getCode, key.getCode())
                .eq(FundWatchlist::getFrequency, key.getFrequency()));
        if (existing > 0) {
            throw new BizException("该基金已在关注清单中");
        }
        FundWatchlist watchlist = new FundWatchlist();
        watchlist.setFundType(key.getFundType());
        watchlist.setCode(key.getCode());
        watchlist.setFrequency(key.getFrequency());
        watchlistMapper.insert(watchlist);
    }

    @Transactional
    public void removeFromWatchlist(String fundType, String code, String frequency) {
        FundHistoryCacheKey key = validateKey(fundType, code, frequency);
        watchlistMapper.delete(new LambdaQueryWrapper<FundWatchlist>()
                .eq(FundWatchlist::getFundType, key.getFundType())
                .eq(FundWatchlist::getCode, key.getCode())
                .eq(FundWatchlist::getFrequency, key.getFrequency()));
    }

    public void refresh(String fundType, String code, String frequency) {
        FundHistoryCacheKey key = validateKey(fundType, code, frequency);
        LocalDate endDate = LocalDate.now(MARKET_ZONE);
        if (ONSHORE.equals(key.getFundType())) {
            onshoreClient.getHistory(key.getCode(),
                    endDate.minusDays(onshoreLookbackDays(key.getFrequency())), endDate, key.getFrequency());
        } else {
            offshoreClient.getHistory(key.getCode(), endDate.minusDays(OFFSHORE_LOOKBACK_DAYS), endDate);
        }
    }

    public void clearCache(String fundType, String code, String frequency) {
        FundHistoryCacheKey key = validateKey(fundType, code, frequency);
        cacheMapper.delete(new QueryWrapper<FundHistoryCacheRecord>()
                .eq("fund_type", key.getFundType())
                .eq("code", key.getCode())
                .eq("frequency", key.getFrequency()));
    }

    public List<FundHistoryCacheKey> getRefreshKeys() {
        Map<String, FundHistoryCacheKey> keys = new TreeMap<>();
        cacheService.getCachedKeys().forEach(key -> keys.put(keyId(key), key));
        watchlistMapper.selectList(new QueryWrapper<FundWatchlist>()
                        .select("fund_type", "code", "frequency"))
                .forEach(item -> {
                    FundHistoryCacheKey key = new FundHistoryCacheKey();
                    key.setFundType(item.getFundType());
                    key.setCode(item.getCode());
                    key.setFrequency(item.getFrequency());
                    keys.put(keyId(key), key);
                });
        return List.copyOf(keys.values());
    }

    private FundHistoryCacheKey validateKey(String rawFundType, String rawCode, String rawFrequency) {
        String fundType = rawFundType == null ? "" : rawFundType.trim().toUpperCase(Locale.ROOT);
        String code = rawCode == null ? "" : rawCode.trim();
        String frequency = rawFrequency == null ? "" : rawFrequency.trim().toLowerCase(Locale.ROOT);
        if (!code.matches("\\d{6}")) {
            throw new BizException("基金代码必须为 6 位数字");
        }
        if (ONSHORE.equals(fundType)) {
            if (!code.matches("(5\\d{5}|15\\d{4}|16\\d{4}|18\\d{4})")) {
                throw new BizException("场内基金代码应为 5、15、16 或 18 开头的 6 位数字");
            }
            if (!List.of("day", "week", "month").contains(frequency)) {
                throw new BizException("场内基金周期仅支持 day、week 或 month");
            }
        } else if (OFFSHORE.equals(fundType)) {
            if (!"day".equals(frequency)) {
                throw new BizException("场外基金仅支持 day 周期");
            }
        } else {
            throw new BizException("基金类型仅支持 ONSHORE 或 OFFSHORE");
        }
        FundHistoryCacheKey key = new FundHistoryCacheKey();
        key.setFundType(fundType);
        key.setCode(code);
        key.setFrequency(frequency);
        return key;
    }

    private int onshoreLookbackDays(String frequency) {
        return switch (frequency) {
            case "day" -> 14;
            case "week" -> 70;
            case "month" -> 400;
            default -> throw new BizException("场内基金周期无效");
        };
    }

    private String keyId(FundHistoryCacheKey key) {
        return key.getFundType() + ':' + key.getCode() + ':' + key.getFrequency();
    }
}
