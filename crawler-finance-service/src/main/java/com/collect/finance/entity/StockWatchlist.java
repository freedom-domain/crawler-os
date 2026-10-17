package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_watchlist")
public class StockWatchlist {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private LocalDateTime createTime;
}
