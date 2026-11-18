package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("stock_history_cache")
public class StockHistoryCacheRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;
    private LocalDate tradeDate;
    private String name;
    private BigDecimal open;
    private BigDecimal close;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal volume;
    private BigDecimal changePercent;
}
