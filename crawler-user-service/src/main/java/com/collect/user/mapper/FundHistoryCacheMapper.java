package com.collect.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.user.entity.FundHistoryCacheRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FundHistoryCacheMapper extends BaseMapper<FundHistoryCacheRecord> {

    @Insert("""
            <script>
            INSERT INTO fund_history_cache (
                fund_type, code, frequency, trade_date, name,
                open, close, high, low, volume, change_percent, unit_nav, cumulative_nav
            ) VALUES
            <foreach collection="records" item="record" separator=",">
                (
                    #{record.fundType}, #{record.code}, #{record.frequency}, #{record.tradeDate}, #{record.name},
                    #{record.open}, #{record.close}, #{record.high}, #{record.low}, #{record.volume},
                    #{record.changePercent}, #{record.unitNav}, #{record.cumulativeNav}
                )
            </foreach>
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                open = VALUES(open),
                close = VALUES(close),
                high = VALUES(high),
                low = VALUES(low),
                volume = VALUES(volume),
                change_percent = VALUES(change_percent),
                unit_nav = VALUES(unit_nav),
                cumulative_nav = VALUES(cumulative_nav)
            </script>
            """)
    int upsertBatch(@Param("records") List<FundHistoryCacheRecord> records);
}
