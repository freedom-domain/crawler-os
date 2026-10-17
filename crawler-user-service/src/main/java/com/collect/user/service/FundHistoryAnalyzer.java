package com.collect.user.service;

import com.collect.user.dto.FundHistoryAnalysis;
import com.collect.user.dto.FundHistoryPoint;
import com.collect.user.dto.FundHistoryReturnPoint;
import com.collect.user.dto.OffExchangeFundNavPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class FundHistoryAnalyzer {

    private static final double DAYS_PER_YEAR = 365.25;
    private static final int PERCENT_SCALE = 4;

    private FundHistoryAnalyzer() {
    }

    public static FundHistoryAnalysis analyzeOnExchange(List<FundHistoryPoint> points, String frequency) {
        List<PriceObservation> observations = points.stream()
                .filter(point -> point.getClose() != null && point.getClose().signum() > 0)
                .map(point -> new PriceObservation(LocalDate.parse(point.getDate()), point.getClose()))
                .sorted(Comparator.comparing(PriceObservation::date))
                .toList();
        int periodsPerYear = switch (frequency) {
            case "day" -> 252;
            case "week" -> 52;
            case "month" -> 12;
            default -> throw new IllegalArgumentException("Unsupported fund history frequency: " + frequency);
        };
        return analyze(observations, periodsPerYear, frequency);
    }

    public static FundHistoryAnalysis analyzeOffExchange(List<OffExchangeFundNavPoint> points) {
        boolean hasCumulativeNav = !points.isEmpty() && points.stream()
                .allMatch(point -> point.getCumulativeNav() != null && point.getCumulativeNav().signum() > 0);
        List<PriceObservation> observations = points.stream()
                .map(point -> {
                    BigDecimal value = hasCumulativeNav ? point.getCumulativeNav() : point.getUnitNav();
                    if (value == null || value.signum() <= 0) {
                        return null;
                    }
                    return new PriceObservation(LocalDate.parse(point.getDate()), value);
                })
                .filter(observation -> observation != null)
                .sorted(Comparator.comparing(PriceObservation::date))
                .toList();
        return analyze(observations, 252, "净值日");
    }

    private static FundHistoryAnalysis analyze(List<PriceObservation> observations, int periodsPerYear,
                                               String returnSeriesFrequency) {
        FundHistoryAnalysis analysis = new FundHistoryAnalysis();
        analysis.setObservationCount(observations.size());
        analysis.setReturnSeriesFrequency(returnSeriesFrequency);
        if (observations.isEmpty()) {
            return analysis;
        }

        analysis.setMonthlyReturns(monthlyReturns(observations));
        analysis.setRollingOneYearReturns(rollingOneYearReturns(observations));
        PriceObservation first = observations.getFirst();
        PriceObservation last = observations.getLast();
        long periodDays = ChronoUnit.DAYS.between(first.date(), last.date());
        analysis.setPeriodDays(periodDays);
        analysis.setMaxDrawdownPercent(percent(maxDrawdown(observations)));

        if (observations.size() < 2) {
            return analysis;
        }

        double firstValue = first.value().doubleValue();
        double lastValue = last.value().doubleValue();
        double totalReturn = lastValue / firstValue - 1;
        analysis.setTotalReturnPercent(percent(totalReturn));
        if (periodDays > 0) {
            double annualizedReturn = Math.pow(lastValue / firstValue, DAYS_PER_YEAR / periodDays) - 1;
            if (Double.isFinite(annualizedReturn)) {
                analysis.setAnnualizedReturnPercent(percent(annualizedReturn));
            }
        }

        if (observations.size() >= 3) {
            double mean = 0;
            double sumSquaredDeviations = 0;
            double downsideSquares = 0;
            int returnCount = observations.size() - 1;
            double[] returns = new double[returnCount];
            for (int index = 1; index < observations.size(); index++) {
                returns[index - 1] = observations.get(index).value().doubleValue()
                        / observations.get(index - 1).value().doubleValue() - 1;
                mean += returns[index - 1];
                if (returns[index - 1] < 0) {
                    downsideSquares += returns[index - 1] * returns[index - 1];
                }
            }
            mean /= returnCount;
            for (double value : returns) {
                sumSquaredDeviations += Math.pow(value - mean, 2);
            }
            double standardDeviation = Math.sqrt(sumSquaredDeviations / (returnCount - 1));
            analysis.setAnnualizedVolatilityPercent(percent(standardDeviation * Math.sqrt(periodsPerYear)));
            if (standardDeviation > 0) {
                analysis.setSharpeRatio(ratio(mean / standardDeviation * Math.sqrt(periodsPerYear)));
            }
            double downsideDeviation = Math.sqrt(downsideSquares / returnCount);
            if (downsideDeviation > 0) {
                analysis.setSortinoRatio(ratio(mean / downsideDeviation * Math.sqrt(periodsPerYear)));
            }
        }
        return analysis;
    }

    private static List<FundHistoryReturnPoint> monthlyReturns(List<PriceObservation> observations) {
        List<FundHistoryReturnPoint> result = new ArrayList<>();
        PriceObservation previous = null;
        YearMonth activeMonth = null;
        PriceObservation monthEnd = null;
        for (PriceObservation observation : observations) {
            YearMonth month = YearMonth.from(observation.date());
            if (activeMonth != null && !activeMonth.equals(month)) {
                addMonthlyReturn(result, activeMonth, previous, monthEnd);
                previous = monthEnd;
            }
            activeMonth = month;
            monthEnd = observation;
        }
        if (activeMonth != null) {
            addMonthlyReturn(result, activeMonth, previous, monthEnd);
        }
        return result;
    }

    private static void addMonthlyReturn(List<FundHistoryReturnPoint> result, YearMonth month,
                                         PriceObservation previous, PriceObservation monthEnd) {
        if (previous != null && monthEnd != null) {
            double monthlyReturn = monthEnd.value().doubleValue() / previous.value().doubleValue() - 1;
            result.add(new FundHistoryReturnPoint(month.toString(), percent(monthlyReturn)));
        }
    }

    private static List<FundHistoryReturnPoint> rollingOneYearReturns(List<PriceObservation> observations) {
        TreeMap<LocalDate, PriceObservation> byDate = new TreeMap<>();
        observations.forEach(observation -> byDate.put(observation.date(), observation));
        List<FundHistoryReturnPoint> result = new ArrayList<>();
        YearMonth activeMonth = null;
        PriceObservation monthEnd = null;
        for (PriceObservation observation : observations) {
            YearMonth month = YearMonth.from(observation.date());
            if (activeMonth != null && !activeMonth.equals(month)) {
                addRollingReturn(result, byDate, monthEnd);
            }
            activeMonth = month;
            monthEnd = observation;
        }
        if (monthEnd != null) {
            addRollingReturn(result, byDate, monthEnd);
        }
        return result;
    }

    private static void addRollingReturn(List<FundHistoryReturnPoint> result,
                                         TreeMap<LocalDate, PriceObservation> observations,
                                         PriceObservation end) {
        LocalDate targetDate = end.date().minusYears(1);
        Map.Entry<LocalDate, PriceObservation> startEntry = observations.ceilingEntry(targetDate);
        if (startEntry == null || startEntry.getKey().isAfter(targetDate.plusDays(35))
                || ChronoUnit.DAYS.between(startEntry.getKey(), end.date()) < 350) {
            return;
        }
        double rollingReturn = end.value().doubleValue() / startEntry.getValue().value().doubleValue() - 1;
        result.add(new FundHistoryReturnPoint(end.date().toString(), percent(rollingReturn)));
    }

    private static double maxDrawdown(List<PriceObservation> observations) {
        double peak = observations.getFirst().value().doubleValue();
        double maxDrawdown = 0;
        for (PriceObservation observation : observations) {
            double value = observation.value().doubleValue();
            peak = Math.max(peak, value);
            maxDrawdown = Math.max(maxDrawdown, 1 - value / peak);
        }
        return maxDrawdown;
    }

    private static BigDecimal percent(double value) {
        return BigDecimal.valueOf(value * 100).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private record PriceObservation(LocalDate date, BigDecimal value) {
    }
}
