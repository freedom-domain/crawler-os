package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.PaperInvestmentHolding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PaperInvestmentHoldingMapper extends BaseMapper<PaperInvestmentHolding> {

    @Select("""
            SELECT * FROM paper_investment_holding
            WHERE user_id = #{userId} AND code = #{code} AND deleted = 0
            FOR UPDATE
            """)
    PaperInvestmentHolding selectForUpdate(@Param("userId") Long userId, @Param("code") String code);
}
