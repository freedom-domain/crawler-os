package com.collect.finance.service;

import com.collect.common.exception.BizException;
import com.collect.finance.dto.StockBacktestComparison;
import com.collect.finance.dto.StockBacktestEntry;
import com.collect.finance.dto.StockBacktestRequest;
import com.collect.finance.dto.StockBacktestResponse;
import com.collect.finance.dto.StockHistoryPoint;
import com.collect.finance.dto.StockHistoryResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockInvestmentBacktestService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.0003");
    private static final BigDecimal MIN_COMMISSION = new BigDecimal("5.00");
    private static final long LOT_SIZE = 100;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final TencentStockHistoryClient historyClient;
    private final EastmoneyHistoryClient eastmoneyHistoryClient;
    private final StockHistoryCacheService historyCacheService;
    private final HistorySyncSettingService historySyncSettingService;

    public StockInvestmentBacktestService(TencentStockHistoryClient historyClient,
                                          EastmoneyHistoryClient eastmoneyHistoryClient,
                                          StockHistoryCacheService historyCacheService,
                                          HistorySyncSettingService historySyncSettingService) {
        this.historyClient = historyClient;
        this.eastmoneyHistoryClient = eastmoneyHistoryClient;
        this.historyCacheService = historyCacheService;
        this.historySyncSettingService = historySyncSettingService;
    }

    public StockHistoryResponse history(String rawCode, String rawStartDate, String rawEndDate, String source,
                                        String rawProvider) {
        String code = normalizeCode(rawCode);
        DateRange range = validateRange(rawStartDate, rawEndDate);
        if (isApiSource(source)) {
            String provider = normalizeProvider(rawProvider);
            historySyncSettingService.requireEnabledProvider(provider);
            return switch (provider) {
                case "TENCENT" -> historyClient.getHistory(code, range.startDate(), range.endDate());
                case "EASTMONEY" ->
                        eastmoneyHistoryClient.getStockHistory(provider, code, range.startDate(), range.endDate());
                default -> throw new BizException("不支持的历史行情 API：" + provider);
            };
        }
        return historyCacheService.get(code, range.startDate(), range.endDate());
    }

    private String normalizeProvider(String provider) {
        return provider == null || provider.isBlank() ? "TENCENT" : provider.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public int syncAllHistory(String rawCode) {
        String code = normalizeCode(rawCode);
        StockHistoryResponse response = historyClient.getHistory(
                code, LocalDate.of(1900, 1, 1), LocalDate.now(MARKET_ZONE));
        return response.getPoints().size();
    }

    public StockBacktestResponse simulate(StockBacktestRequest request) {
        String code = normalizeCode(request.getCode());
        DateRange range = validateRange(request.getStartDate(), request.getEndDate());
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().scale() > 2 || request.getAmount().compareTo(new BigDecimal("10000000")) > 0) {
            throw new BizException("每期定投金额必须为 1 至 10,000,000 元，最多保留两位小数");
        }
        if (request.getWeekday() == null || request.getWeekday() < 1 || request.getWeekday() > 5) {
            throw new BizException("定投日请选择星期一至星期五");
        }

        StockHistoryResponse history = historyClient.getHistory(code, range.startDate(), range.endDate());
        if (history.getPoints().isEmpty()) {
            throw new BizException("所选时间段没有可用的股票历史行情");
        }
        TreeMap<LocalDate, StockHistoryPoint> tradingDays = history.getPoints().stream()
                .collect(Collectors.toMap(point -> LocalDate.parse(point.getDate()), Function.identity(),
                        (left, right) -> right, TreeMap::new));
        var scheduleDates = createSchedule(range.startDate(), range.endDate(), request.getWeekday(), tradingDays);
        if (scheduleDates.isEmpty()) {
            throw new BizException("所选时间段没有可执行定投的交易日");
        }

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal totalContribution = BigDecimal.ZERO;
        BigDecimal investedAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        long totalShares = 0;
        ArrayList<StockBacktestEntry> schedule = new ArrayList<>();

        for (ScheduleDate scheduled : scheduleDates) {
            BigDecimal contribution = request.getAmount().setScale(2, RoundingMode.HALF_UP);
            cash = cash.add(contribution);
            totalContribution = totalContribution.add(contribution);
            BigDecimal price = scheduled.point().getClose();
            long lots = cash.divide(price.multiply(BigDecimal.valueOf(LOT_SIZE)), 0, RoundingMode.DOWN).longValue();
            while (lots > 0) {
                BigDecimal gross = price.multiply(BigDecimal.valueOf(lots * LOT_SIZE))
                        .setScale(2, RoundingMode.HALF_UP);
                if (gross.add(commission(gross)).compareTo(cash) <= 0) {
                    break;
                }
                lots--;
            }

            long boughtShares = lots * LOT_SIZE;
            BigDecimal gross = price.multiply(BigDecimal.valueOf(boughtShares)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = boughtShares > 0 ? commission(gross) : BigDecimal.ZERO.setScale(2);
            cash = cash.subtract(gross).subtract(fee).setScale(2, RoundingMode.HALF_UP);
            investedAmount = investedAmount.add(gross);
            totalCommission = totalCommission.add(fee);
            totalShares += boughtShares;

            BigDecimal marketValue = price.multiply(BigDecimal.valueOf(totalShares)).setScale(2, RoundingMode.HALF_UP);
            StockBacktestEntry entry = new StockBacktestEntry();
            entry.setScheduledDate(scheduled.scheduledDate().toString());
            entry.setTradeDate(scheduled.tradeDate().toString());
            entry.setPrice(price);
            entry.setContribution(contribution);
            entry.setShares(boughtShares);
            entry.setInvestedAmount(gross);
            entry.setCommission(fee);
            entry.setCumulativeShares(totalShares);
            entry.setCumulativeContribution(totalContribution);
            entry.setMarketValue(marketValue);
            entry.setProfit(marketValue.add(cash).subtract(totalContribution).setScale(2, RoundingMode.HALF_UP));
            schedule.add(entry);
        }

        StockHistoryPoint lastPoint = tradingDays.floorEntry(range.endDate()).getValue();
        BigDecimal finalPrice = lastPoint.getClose();
        BigDecimal marketValue = finalPrice.multiply(BigDecimal.valueOf(totalShares)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAssets = marketValue.add(cash).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = totalAssets.subtract(totalContribution).setScale(2, RoundingMode.HALF_UP);

        StockBacktestResponse response = new StockBacktestResponse();
        response.setCode(history.getCode());
        response.setName(history.getName());
        response.setStartDate(range.startDate().toString());
        response.setEndDate(range.endDate().toString());
        response.setWeekday(request.getWeekday());
        response.setAmountPerPeriod(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        response.setExecutedPeriods(schedule.size());
        response.setSkippedPeriods(countWeekdays(range.startDate(), range.endDate(), request.getWeekday())
                - schedule.size());
        response.setTotalContribution(totalContribution.setScale(2, RoundingMode.HALF_UP));
        response.setInvestedAmount(investedAmount.setScale(2, RoundingMode.HALF_UP));
        response.setTotalCommission(totalCommission.setScale(2, RoundingMode.HALF_UP));
        response.setRemainingCash(cash);
        response.setTotalShares(totalShares);
        response.setAverageCost(totalShares == 0 ? BigDecimal.ZERO.setScale(4)
                : investedAmount.add(totalCommission).divide(BigDecimal.valueOf(totalShares), 4,
                        RoundingMode.HALF_UP));
        response.setFinalPrice(finalPrice);
        response.setMarketValue(marketValue);
        response.setTotalAssets(totalAssets);
        response.setProfit(profit);
        response.setProfitPercent(totalContribution.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : profit.multiply(BigDecimal.valueOf(100)).divide(totalContribution, 2, RoundingMode.HALF_UP));
        response.setFinalQuoteDate(lastPoint.getDate());
        response.setSchedule(schedule);
        response.setLumpSumComparison(compareLumpSum(totalContribution, tradingDays.firstEntry().getValue(),
                finalPrice));
        return response;
    }

    private StockBacktestComparison compareLumpSum(BigDecimal contribution, StockHistoryPoint firstPoint,
                                                    BigDecimal finalPrice) {
        BigDecimal price = firstPoint.getClose();
        long lots = contribution.divide(price.multiply(BigDecimal.valueOf(LOT_SIZE)), 0, RoundingMode.DOWN)
                .longValue();
        BigDecimal investedAmount;
        BigDecimal fee;
        while (true) {
            long shares = lots * LOT_SIZE;
            investedAmount = price.multiply(BigDecimal.valueOf(shares)).setScale(2, RoundingMode.HALF_UP);
            fee = shares > 0 ? commission(investedAmount) : BigDecimal.ZERO.setScale(2);
            if (investedAmount.add(fee).compareTo(contribution) <= 0 || lots == 0) {
                break;
            }
            lots--;
        }
        long shares = lots * LOT_SIZE;
        BigDecimal remainingCash = contribution.subtract(investedAmount).subtract(fee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalValue = finalPrice.multiply(BigDecimal.valueOf(shares))
                .add(remainingCash).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = finalValue.subtract(contribution).setScale(2, RoundingMode.HALF_UP);

        StockBacktestComparison comparison = new StockBacktestComparison();
        comparison.setInvestmentDate(firstPoint.getDate());
        comparison.setTotalContribution(contribution);
        comparison.setInvestedAmount(investedAmount);
        comparison.setTotalFees(fee);
        comparison.setFinalValue(finalValue);
        comparison.setProfit(profit);
        comparison.setProfitPercent(contribution.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : profit.multiply(BigDecimal.valueOf(100)).divide(contribution, 2, RoundingMode.HALF_UP));
        return comparison;
    }

    private ArrayList<ScheduleDate> createSchedule(LocalDate startDate, LocalDate endDate, int weekday,
                                                    TreeMap<LocalDate, StockHistoryPoint> tradingDays) {
        DayOfWeek dayOfWeek = DayOfWeek.of(weekday);
        LocalDate scheduledDate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        ArrayList<ScheduleDate> schedule = new ArrayList<>();
        while (!scheduledDate.isAfter(endDate)) {
            LocalDate weekEnd = scheduledDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            LocalDate latestAllowedDate = weekEnd.isAfter(endDate) ? endDate : weekEnd;
            Map.Entry<LocalDate, StockHistoryPoint> tradeDay = tradingDays.ceilingEntry(scheduledDate);
            if (tradeDay != null && !tradeDay.getKey().isAfter(latestAllowedDate)) {
                schedule.add(new ScheduleDate(scheduledDate, tradeDay.getKey(), tradeDay.getValue()));
            }
            scheduledDate = scheduledDate.plusWeeks(1);
        }
        return schedule;
    }

    private int countWeekdays(LocalDate startDate, LocalDate endDate, int weekday) {
        DayOfWeek dayOfWeek = DayOfWeek.of(weekday);
        LocalDate date = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        int count = 0;
        while (!date.isAfter(endDate)) {
            count++;
            date = date.plusWeeks(1);
        }
        return count;
    }

    private BigDecimal commission(BigDecimal amount) {
        return amount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.CEILING).max(MIN_COMMISSION);
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new BizException("股票代码必须为 6 位数字");
        }
        return normalized;
    }

    private boolean isApiSource(String source) {
        String normalized = source == null ? "" : source.trim();
        if ("api".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("database".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new BizException("历史行情来源仅支持 api 或 database");
    }

    private DateRange validateRange(String rawStartDate, String rawEndDate) {
        try {
            LocalDate startDate = LocalDate.parse(rawStartDate);
            LocalDate endDate = LocalDate.parse(rawEndDate);
            if (startDate.isAfter(endDate)) {
                throw new BizException("开始日期不能晚于结束日期");
            }
            if (endDate.isAfter(LocalDate.now(MARKET_ZONE))) {
                throw new BizException("结束日期不能晚于今天");
            }
            if (ChronoUnit.DAYS.between(startDate, endDate) > 3650) {
                throw new BizException("单次历史查询最多支持 10 年");
            }
            return new DateRange(startDate, endDate);
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 YYYY-MM-DD");
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record ScheduleDate(LocalDate scheduledDate, LocalDate tradeDate, StockHistoryPoint point) {
    }
}
