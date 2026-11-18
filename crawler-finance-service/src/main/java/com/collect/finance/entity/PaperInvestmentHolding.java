package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_investment_holding")
public class PaperInvestmentHolding extends BaseEntity {

    private Long userId;
    private String code;
    private String name;
    private String market;
    private String instrumentType;
    private Integer quantity;
    private Integer availableQuantity;
    private BigDecimal averageCost;
    private LocalDate settledDate;
}
