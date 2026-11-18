package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.StockRefreshSetting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockRefreshSettingMapper extends BaseMapper<StockRefreshSetting> {

    @Update("""
            UPDATE stock_refresh_setting
            SET last_run_slot = #{slot}
            WHERE id = 1
              AND frequency = #{frequency}
              AND (last_run_slot IS NULL OR last_run_slot <> #{slot})
            """)
    int claimRunSlot(@Param("frequency") String frequency, @Param("slot") String slot);
}
