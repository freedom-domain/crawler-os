package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fund_watchlist")
public class FundWatchlist {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundType;
    private String code;
    private String frequency;
    private LocalDateTime createTime;
}
