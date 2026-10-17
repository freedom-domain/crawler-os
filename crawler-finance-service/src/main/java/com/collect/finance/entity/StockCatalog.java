package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_catalog")
public class StockCatalog {

    @TableId
    private String code;
    private String name;
    private String market;
    private String industry;
    private LocalDateTime syncedAt;
}
