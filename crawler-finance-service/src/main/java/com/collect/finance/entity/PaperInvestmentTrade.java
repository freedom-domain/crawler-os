package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_investment_trade")
public class PaperInvestmentTrade extends BaseEntity {

    private Long userId;
    private String code;
    private String name;
    private String market;
    private String instrumentType;
    private String side;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal stampTax;
}
