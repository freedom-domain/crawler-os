package com.collect.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_investment_account")
public class PaperInvestmentAccount extends BaseEntity {

    private Long userId;
    private BigDecimal initialCash;
    private BigDecimal cashBalance;
}
