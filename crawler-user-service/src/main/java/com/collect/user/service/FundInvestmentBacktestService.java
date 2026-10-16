package com.collect.user.service;

import com.collect.common.exception.BizException;
import com.collect.user.dto.FundDcaEntry;
import com.collect.user.dto.FundDcaRequest;
import com.collect.user.dto.FundDcaResponse;
import com.collect.user.dto.FundHistoryPoint;
import com.collect.user.dto.FundHistoryResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FundInvestmentBacktestService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.0003");
    private static final BigDecimal MIN_COMMISSION = new BigDecimal("5.00");
    private static final int LOT_SIZE = 100;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final TencentFundHistoryClient historyClient;

    public FundInvestmentBacktestService(TencentFundHistoryClient historyClient) {
        this.historyClient = historyClient;
    }

    public FundHistoryResponse history(String rawCode, String rawStartDate, String rawEndDate, String frequency) {
        String code = normalizeCode(rawCode);
        DateRange range = validateRange(rawStartDate, rawEndDate);
        return historyClient.getHistory(code, range.startDate(), range.endDate(), frequency);
    }

    public FundDcaResponse simulate(FundDcaRequest request) {
        String code = normalizeCode(request.getCode());
        DateRange range = validateRange(request.getStartDate(), request.getEndDate());
        if (request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().scale() > 2) {
            throw new BizException("每期定投金额必须大于 0，最多保留两位小数");
        }
        if (request.getWeekday() == null || request.getWeekday() < 1 || request.getWeekday() > 5) {
            throw new BizException("定投日请选择星期一至星期五");
        }

        FundHistoryResponse history = historyClient.getHistory(code, range.startDate(), range.endDate(), "day");
        if (history.getPoints().isEmpty()) {
            throw new BizException("所选时间段没有可用的基金历史行情");
        }
        List<FundHistoryPoint> points = history.getPoints().stream()
                .sorted(Comparator.comparing(FundHistoryPoint::getDate))
                .toList();
        TreeMap<LocalDate, FundHistoryPoint> tradingDays = points.stream().collect(Collectors.toMap(
                point -> LocalDate.parse(point.getDate()),
                Function.identity(),
                (left, right) -> right,
                TreeMap::new));
        List<ScheduleDate> scheduleDates = createSchedule(range.startDate(), range.endDate(), request.getWeekday(), tradingDays);
        if (scheduleDates.isEmpty()) {
            throw new BizException("所选时间段没有可执行定投的交易日");
        }

        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal totalContribution = BigDecimal.ZERO;
        BigDecimal investedAmount = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        int totalShares = 0;
        List<FundDcaEntry> schedule = new ArrayList<>();

        for (ScheduleDate scheduled : scheduleDates) {
            BigDecimal contribution = request.getAmount().setScale(2, RoundingMode.HALF_UP);
            cash = cash.add(contribution);
            totalContribution = totalContribution.add(contribution);
            BigDecimal price = scheduled.point().getClose();
            int lots = cash.divide(price.multiply(BigDecimal.valueOf(LOT_SIZE)), 0, RoundingMode.DOWN).intValue();
            while (lots > 0) {
                BigDecimal gross = price.multiply(BigDecimal.valueOf((long) lots * LOT_SIZE))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal commission = commission(gross);
                if (gross.add(commission).compareTo(cash) <= 0) {
                    break;
                }
                lots--;
            }

            int boughtShares = lots * LOT_SIZE;
            BigDecimal gross = price.multiply(BigDecimal.valueOf(boughtShares)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = boughtShares > 0 ? commission(gross) : BigDecimal.ZERO.setScale(2);
            cash = cash.subtract(gross).subtract(fee).setScale(2, RoundingMode.HALF_UP);
            investedAmount = investedAmount.add(gross);
            totalCommission = totalCommission.add(fee);
            totalShares += boughtShares;

            BigDecimal marketValue = price.multiply(BigDecimal.valueOf(totalShares)).setScale(2, RoundingMode.HALF_UP);
            FundDcaEntry entry = new FundDcaEntry();
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

        FundHistoryPoint lastPoint = tradingDays.floorEntry(range.endDate()).getValue();
        BigDecimal finalPrice = lastPoint.getClose();
        BigDecimal marketValue = finalPrice.multiply(BigDecimal.valueOf(totalShares))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAssets = marketValue.add(cash).setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = totalAssets.subtract(totalContribution).setScale(2, RoundingMode.HALF_UP);

        FundDcaResponse response = new FundDcaResponse();
        response.setCode(history.getCode());
        response.setName(history.getName());
        response.setStartDate(range.startDate().toString());
        response.setEndDate(range.endDate().toString());
        response.setWeekday(request.getWeekday());
        response.setAmountPerPeriod(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        response.setExecutedPeriods(schedule.size());
        response.setSkippedPeriods(countWeekdays(range.startDate(), range.endDate(), request.getWeekday()) - schedule.size());
        response.setTotalContribution(totalContribution);
        response.setInvestedAmount(investedAmount.setScale(2, RoundingMode.HALF_UP));
        response.setTotalCommission(totalCommission.setScale(2, RoundingMode.HALF_UP));
        response.setRemainingCash(cash);
        response.setTotalShares(totalShares);
        response.setAverageCost(totalShares == 0 ? BigDecimal.ZERO.setScale(4)
                : investedAmount.add(totalCommission).divide(BigDecimal.valueOf(totalShares), 4, RoundingMode.HALF_UP));
        response.setFinalPrice(finalPrice);
        response.setMarketValue(marketValue);
        response.setTotalAssets(totalAssets);
        response.setProfit(profit);
        response.setProfitPercent(totalContribution.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : profit.multiply(BigDecimal.valueOf(100)).divide(totalContribution, 2, RoundingMode.HALF_UP));
        response.setFinalQuoteDate(lastPoint.getDate());
        response.setSchedule(schedule);
        return response;
    }

    private List<ScheduleDate> createSchedule(LocalDate startDate, LocalDate endDate, int weekday,
                                               TreeMap<LocalDate, FundHistoryPoint> tradingDays) {
        DayOfWeek dayOfWeek = DayOfWeek.of(weekday);
        LocalDate scheduledDate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        List<ScheduleDate> schedule = new ArrayList<>();
        while (!scheduledDate.isAfter(endDate)) {
            LocalDate weekEnd = scheduledDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
            LocalDate latestAllowedDate = weekEnd.isAfter(endDate) ? endDate : weekEnd;
            Map.Entry<LocalDate, FundHistoryPoint> tradeDay = tradingDays.ceilingEntry(scheduledDate);
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
        if (code == null || !code.trim().matches("(5\\d{5}|15\\d{4}|16\\d{4}|18\\d{4})")) {
            throw new BizException("基金历史行情仅支持场内基金代码，如 510300、159915");
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
                throw new BizException("单次历史查询最多支持 10 年");
            }
            return new DateRange(startDate, endDate);
        } catch (DateTimeParseException e) {
            throw new BizException("日期格式应为 YYYY-MM-DD");
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private record ScheduleDate(LocalDate scheduledDate, LocalDate tradeDate, FundHistoryPoint point) {
    }
}
