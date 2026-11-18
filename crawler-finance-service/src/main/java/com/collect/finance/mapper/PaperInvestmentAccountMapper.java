package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.PaperInvestmentAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface PaperInvestmentAccountMapper extends BaseMapper<PaperInvestmentAccount> {

    @Insert("""
            INSERT INTO paper_investment_account (user_id, initial_cash, cash_balance, deleted, create_time, update_time)
            VALUES (#{userId}, #{initialCash}, #{initialCash}, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
            """)
    void createIfAbsent(@Param("userId") Long userId, @Param("initialCash") BigDecimal initialCash);

    @Select("SELECT * FROM paper_investment_account WHERE user_id = #{userId} AND deleted = 0 FOR UPDATE")
    PaperInvestmentAccount selectForUpdate(@Param("userId") Long userId);
}
