package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.user.dto.FundHistoryPoint;
import com.collect.user.dto.FundHistoryResponse;
import com.collect.user.dto.OffExchangeFundHistoryResponse;
import com.collect.user.dto.OffExchangeFundNavPoint;
import com.collect.user.entity.FundHistoryCacheRecord;
import com.collect.user.mapper.FundHistoryCacheMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class FundHistoryCacheService {

    private static final String ONSHORE = "ONSHORE";
    private static final String OFFSHORE = "OFFSHORE";

    private final FundHistoryCacheMapper mapper;

    public FundHistoryCacheService(FundHistoryCacheMapper mapper) {
        this.mapper = mapper;
    }

    public void saveOnExchange(String code, String name, String frequency, List<FundHistoryPoint> points) {
        List<FundHistoryCacheRecord> records = points.stream().map(point -> {
            FundHistoryCacheRecord record = newRecord(ONSHORE, code, frequency, name, point.getDate());
            record.setOpen(point.getOpen());
            record.setClose(point.getClose());
            record.setHigh(point.getHigh());
            record.setLow(point.getLow());
            record.setVolume(point.getVolume());
            record.setChangePercent(point.getChangePercent());
            return record;
        }).toList();
        upsert(records);
    }

    public FundHistoryResponse getOnExchange(String code, String frequency, LocalDate startDate, LocalDate endDate) {
        List<FundHistoryCacheRecord> records = findRecords(ONSHORE, code, frequency, startDate, endDate);
        if (records.isEmpty()) {
            throw cacheMiss(code, startDate, endDate);
        }

        FundHistoryResponse response = new FundHistoryResponse();
        response.setCode(code);
        response.setName(records.getFirst().getName());
        response.setFrequency(frequency);
        List<FundHistoryPoint> points = records.stream().map(record -> {
            FundHistoryPoint point = new FundHistoryPoint();
            point.setDate(record.getTradeDate().toString());
            point.setOpen(record.getOpen());
            point.setClose(record.getClose());
            point.setHigh(record.getHigh());
            point.setLow(record.getLow());
            point.setVolume(record.getVolume());
            point.setChangePercent(record.getChangePercent());
            return point;
        }).toList();
        response.setPoints(points);
        response.setAnalysis(FundHistoryAnalyzer.analyzeOnExchange(points, frequency));
        return response;
    }

    public void saveOffExchange(String code, String name, List<OffExchangeFundNavPoint> points) {
        List<FundHistoryCacheRecord> records = points.stream().map(point -> {
            FundHistoryCacheRecord record = newRecord(OFFSHORE, code, "day", name, point.getDate());
            record.setUnitNav(point.getUnitNav());
            record.setCumulativeNav(point.getCumulativeNav());
            record.setChangePercent(point.getChangePercent());
            return record;
        }).toList();
        upsert(records);
    }

    public OffExchangeFundHistoryResponse getOffExchange(String code, LocalDate startDate, LocalDate endDate) {
        List<FundHistoryCacheRecord> records = findRecords(OFFSHORE, code, "day", startDate, endDate);
        if (records.isEmpty()) {
            throw cacheMiss(code, startDate, endDate);
        }

        OffExchangeFundHistoryResponse response = new OffExchangeFundHistoryResponse();
        response.setCode(code);
        response.setName(records.getFirst().getName());
        List<OffExchangeFundNavPoint> points = records.stream().map(record -> {
            OffExchangeFundNavPoint point = new OffExchangeFundNavPoint();
            point.setDate(record.getTradeDate().toString());
            point.setUnitNav(record.getUnitNav());
            point.setCumulativeNav(record.getCumulativeNav());
            point.setChangePercent(record.getChangePercent());
            return point;
        }).toList();
        response.setPoints(points);
        response.setAnalysis(FundHistoryAnalyzer.analyzeOffExchange(points));
        return response;
    }

    private FundHistoryCacheRecord newRecord(String fundType, String code, String frequency, String name,
                                              String rawDate) {
        FundHistoryCacheRecord record = new FundHistoryCacheRecord();
        record.setFundType(fundType);
        record.setCode(code);
        record.setFrequency(frequency);
        record.setTradeDate(LocalDate.parse(rawDate));
        record.setName(name);
        return record;
    }

    private void upsert(List<FundHistoryCacheRecord> records) {
        if (!records.isEmpty()) {
            mapper.upsertBatch(records);
        }
    }

    private List<FundHistoryCacheRecord> findRecords(String fundType, String code, String frequency,
                                                      LocalDate startDate, LocalDate endDate) {
        return mapper.selectList(new QueryWrapper<FundHistoryCacheRecord>()
                .eq("fund_type", fundType)
                .eq("code", code)
                .eq("frequency", frequency)
                .ge("trade_date", startDate)
                .le("trade_date", endDate)
                .orderByAsc("trade_date"));
    }

    private BizException cacheMiss(String code, LocalDate startDate, LocalDate endDate) {
        return new BizException("数据库中没有基金 " + code + " 在 " + startDate + " 至 " + endDate
                + " 的历史行情，请先选择 API 查询并缓存数据");
    }
}
