package com.collect.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.finance.dto.StockHistoryPoint;
import com.collect.finance.dto.StockHistoryResponse;
import com.collect.finance.entity.StockHistoryCacheRecord;
import com.collect.finance.mapper.StockHistoryCacheMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StockHistoryCacheService {

    private final StockHistoryCacheMapper mapper;

    public StockHistoryCacheService(StockHistoryCacheMapper mapper) {
        this.mapper = mapper;
    }

    public void save(String code, String name, List<StockHistoryPoint> points) {
        List<StockHistoryCacheRecord> records = points.stream().map(point -> {
            StockHistoryCacheRecord record = new StockHistoryCacheRecord();
            record.setCode(code);
            record.setTradeDate(LocalDate.parse(point.getDate()));
            record.setName(name);
            record.setOpen(point.getOpen());
            record.setClose(point.getClose());
            record.setHigh(point.getHigh());
            record.setLow(point.getLow());
            record.setVolume(point.getVolume());
            record.setChangePercent(point.getChangePercent());
            return record;
        }).toList();
        if (!records.isEmpty()) {
            mapper.upsertBatch(records);
        }
    }

    public StockHistoryResponse get(String code, LocalDate startDate, LocalDate endDate) {
        List<StockHistoryCacheRecord> records = mapper.selectList(new QueryWrapper<StockHistoryCacheRecord>()
                .eq("code", code)
                .ge("trade_date", startDate)
                .le("trade_date", endDate)
                .orderByAsc("trade_date"));
        if (records.isEmpty()) {
            throw new BizException("数据库中没有股票 " + code + " 在 " + startDate + " 至 " + endDate
                    + " 的历史行情，请先选择 API 查询并缓存数据");
        }
        StockHistoryResponse response = new StockHistoryResponse();
        response.setCode(code);
        response.setName(records.getFirst().getName());
        response.setFrequency("day");
        response.setPoints(records.stream().map(record -> {
            StockHistoryPoint point = new StockHistoryPoint();
            point.setDate(record.getTradeDate().toString());
            point.setOpen(record.getOpen());
            point.setClose(record.getClose());
            point.setHigh(record.getHigh());
            point.setLow(record.getLow());
            point.setVolume(record.getVolume());
            point.setChangePercent(record.getChangePercent());
            return point;
        }).toList());
        return response;
    }
}
