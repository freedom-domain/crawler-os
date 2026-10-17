package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.StockHistoryCacheRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StockHistoryCacheMapper extends BaseMapper<StockHistoryCacheRecord> {

    @Insert("""
            <script>
            INSERT INTO stock_history_cache (
                code, trade_date, name, open, close, high, low, volume, change_percent
            ) VALUES
            <foreach collection="records" item="record" separator=",">
                (
                    #{record.code}, #{record.tradeDate}, #{record.name},
                    #{record.open}, #{record.close}, #{record.high}, #{record.low},
                    #{record.volume}, #{record.changePercent}
                )
            </foreach>
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                open = VALUES(open),
                close = VALUES(close),
                high = VALUES(high),
                low = VALUES(low),
                volume = VALUES(volume),
                change_percent = VALUES(change_percent)
            </script>
            """)
    int upsertBatch(@Param("records") List<StockHistoryCacheRecord> records);
}
