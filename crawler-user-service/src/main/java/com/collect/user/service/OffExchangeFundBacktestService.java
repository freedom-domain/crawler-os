package com.collect.user.service;

import com.collect.common.exception.BizException;
import com.collect.user.dto.OffExchangeFundDcaEntry;
import com.collect.user.dto.OffExchangeFundDcaRequest;
import com.collect.user.dto.OffExchangeFundDcaResponse;
import com.collect.user.dto.OffExchangeFundHistoryResponse;
import com.collect.user.dto.OffExchangeFundNavPoint;
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

@Service
public class OffExchangeFundBacktestService {

    private static final int AMOUNT_SCALE = 2;
    private static final int SHARE_SCALE = 2;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final EastmoneyOffExchangeFundClient historyClient;
    private final FundHistoryCacheService historyCacheService;

    public OffExchangeFundBacktestService(EastmoneyOffExchangeFundClient historyClient,
                                          FundHistoryCacheService historyCacheService) {
        this.historyClient = historyClient;
        this.historyCacheService = historyCacheService;
    }

    public OffExchangeFundHistoryResponse history(String code, String rawStartDate, String rawEndDate, String source) {
        DateRange range = validateRange(rawStartDate, rawEndDate);
        String normalizedCode = normalizeCode(code);
        String normalizedSource = source == null ? "" : source.trim();
        if ("api".equalsIgnoreCase(normalizedSource)) {
            return historyClient.getHistory(normalizedCode, range.startDate(), range.endDate());
        }
        if ("database".equalsIgnoreCase(normalizedSource)) {
            return historyCacheService.getOffExchange(normalizedCode, range.startDate(), range.endDate());
        }
        throw new BizException("历史行情来源仅支持 api 或 database");
    }

    public OffExchangeFundDcaResponse simulate(OffExchangeFundDcaRequest request) {
        String code = normalizeCode(request.getCode());
        DateRange range = validateRange(request.getStartDate(), request.getEndDate());
        validateRate(request.getSubscriptionFeeRate(), "申购费率");
        validateRate(request.getRedemptionFeeRate(), "赎回费率");
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().scale() > AMOUNT_SCALE) {
            throw new BizException("每期定投金额必须大于 0，最多保留两位小数");
        }
        if (request.getWeekday() == null || request.getWeekday() < 1 || request.getWeekday() > 5) {
            throw new BizException("定投日请选择星期一至星期五");
        }

        OffExchangeFundHistoryResponse history = historyClient.getHistory(code, range.startDate(), range.endDate());
        TreeMap<LocalDate, OffExchangeFundNavPoint> navs = new TreeMap<>();
        for (OffExchangeFundNavPoint point : history.getPoints()) {
            navs.put(LocalDate.parse(point.getDate()), point);
        }
        var scheduleDates = createSchedule(range.startDate(), range.endDate(), request.getWeekday(), navs);
        if (scheduleDates.isEmpty()) {
            throw new BizException("所选时间段没有可执行定投的净值日期");
        }

        BigDecimal contributionPerPeriod = request.getAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal subscriptionRate = request.getSubscriptionFeeRate()
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal redemptionRate = request.getRedemptionFeeRate()
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
        BigDecimal totalContribution = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
        BigDecimal totalSubscriptionFee = BigDecimal.ZERO.setScale(AMOUNT_SCALE);
        BigDecimal totalShares = BigDecimal.ZERO.setScale(SHARE_SCALE);
        var entries = new ArrayList<OffExchangeFundDcaEntry>();

        for (ScheduleDate scheduled : scheduleDates) {
            BigDecimal subscriptionFee = contributionPerPeriod.multiply(subscriptionRate)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            BigDecimal investableAmount = contributionPerPeriod.subtract(subscriptionFee);
            BigDecimal shares = investableAmount.divide(scheduled.point().getUnitNav(), SHARE_SCALE, RoundingMode.DOWN);
            totalContribution = totalContribution.add(contributionPerPeriod);
            totalSubscriptionFee = totalSubscriptionFee.add(subscriptionFee);
            totalShares = totalShares.add(shares);

            BigDecimal marketValue = scheduled.point().getUnitNav().multiply(totalShares)
                    .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            OffExchangeFundDcaEntry entry = new OffExchangeFundDcaEntry();
            entry.setScheduledDate(scheduled.scheduledDate().toString());
            entry.setTradeDate(scheduled.tradeDate().toString());
            entry.setNav(scheduled.point().getUnitNav());
            entry.setContribution(contributionPerPeriod);
            entry.setSubscriptionFee(subscriptionFee);
            entry.setShares(shares);
            entry.setCumulativeShares(totalShares);
            entry.setMarketValue(marketValue);
            entry.setProfit(marketValue.subtract(totalContribution).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP));
            entries.add(entry);
        }

        Map.Entry<LocalDate, OffExchangeFundNavPoint> lastNavEntry = navs.floorEntry(range.endDate());
        if (lastNavEntry == null) {
            throw new BizException("所选日期范围内没有可用于期末估值的基金净值");
        }
        OffExchangeFundNavPoint lastNav = lastNavEntry.getValue();
        BigDecimal grossMarketValue = lastNav.getUnitNav().multiply(totalShares)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal redemptionFee = grossMarketValue.multiply(redemptionRate)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal netLiquidationValue = grossMarketValue.subtract(redemptionFee)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal profit = netLiquidationValue.subtract(totalContribution)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);

        OffExchangeFundDcaResponse result = new OffExchangeFundDcaResponse();
        result.setCode(history.getCode());
        result.setName(history.getName());
        result.setStartDate(range.startDate().toString());
        result.setEndDate(range.endDate().toString());
        result.setWeekday(request.getWeekday());
        result.setAmountPerPeriod(contributionPerPeriod);
        result.setSubscriptionFeeRate(request.getSubscriptionFeeRate());
        result.setRedemptionFeeRate(request.getRedemptionFeeRate());
        result.setExecutedPeriods(entries.size());
        result.setSkippedPeriods(countScheduledWeeks(range.startDate(), range.endDate(), request.getWeekday()) - entries.size());
        result.setTotalContribution(totalContribution);
        result.setTotalSubscriptionFee(totalSubscriptionFee);
        result.setTotalShares(totalShares);
        result.setFinalNav(lastNav.getUnitNav());
        result.setGrossMarketValue(grossMarketValue);
        result.setRedemptionFee(redemptionFee);
        result.setNetLiquidationValue(netLiquidationValue);
        result.setProfit(profit);
        result.setProfitPercent(totalContribution.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : profit.multiply(BigDecimal.valueOf(100)).divide(totalContribution, 2, RoundingMode.HALF_UP));
        result.setFinalNavDate(lastNav.getDate());
        result.setSchedule(entries);
        return result;
    }

    private ArrayList<ScheduleDate> createSchedule(LocalDate startDate, LocalDate endDate, int weekday,
                                                    TreeMap<LocalDate, OffExchangeFundNavPoint> navs) {
        DayOfWeek dayOfWeek = DayOfWeek.of(weekday);
        LocalDate scheduledDate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        ArrayList<ScheduleDate> result = new ArrayList<>();
        while (!scheduledDate.isAfter(endDate)) {
            LocalDate weekEnd = scheduledDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            LocalDate latestAllowedDate = weekEnd.isAfter(endDate) ? endDate : weekEnd;
            Map.Entry<LocalDate, OffExchangeFundNavPoint> nav = navs.ceilingEntry(scheduledDate);
            if (nav != null && !nav.getKey().isAfter(latestAllowedDate)) {
                result.add(new ScheduleDate(scheduledDate, nav.getKey(), nav.getValue()));
            }
            scheduledDate = scheduledDate.plusWeeks(1);
        }
        return result;
    }

    private int countScheduledWeeks(LocalDate startDate, LocalDate endDate, int weekday) {
        LocalDate date = startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)));
        int count = 0;
        while (!date.isAfter(endDate)) {
            count++;
            date = date.plusWeeks(1);
        }
        return count;
    }

    private void validateRate(BigDecimal rate, String label) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0
                || rate.scale() > 4) {
            throw new BizException(label + "必须在 0 至 100 之间，最多保留四位小数");
        }
    }

    private String normalizeCode(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new BizException("场外基金代码应为 6 位数字");
        }
        return code.trim();
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
                throw new BizException("单次净值查询最多支持 10 年");
            }
            return new DateRange(startDate, endDate);
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 YYYY-MM-DD");
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record ScheduleDate(LocalDate scheduledDate, LocalDate tradeDate, OffExchangeFundNavPoint point) {
    }
}
