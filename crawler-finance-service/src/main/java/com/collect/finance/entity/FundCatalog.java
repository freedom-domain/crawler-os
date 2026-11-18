package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fund_catalog")
public class FundCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String fundType;
    private String code;
    private String name;
    private String category;
    private String pinyin;
    private LocalDateTime syncedAt;
}
