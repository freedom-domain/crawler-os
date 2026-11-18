package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.dto.FundManagementItem;
import com.collect.finance.entity.FundHistoryCacheKey;
import com.collect.finance.entity.FundHistoryCacheRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FundHistoryCacheMapper extends BaseMapper<FundHistoryCacheRecord> {

    @Select("""
            SELECT w.id AS watchlistId, keys_union.fund_type AS fundType, keys_union.code,
                   keys_union.frequency, COALESCE(MAX(cache.name), keys_union.code) AS name,
                   COUNT(cache.id) AS cachedPoints, MAX(cache.trade_date) AS latestDate,
                   (w.id IS NOT NULL) AS watched, w.create_time AS addedAt
            FROM (
                SELECT fund_type, code, frequency FROM fund_history_cache
                GROUP BY fund_type, code, frequency
                UNION
                SELECT fund_type, code, frequency FROM fund_watchlist
            ) keys_union
            LEFT JOIN fund_watchlist w
              ON w.fund_type = keys_union.fund_type
             AND w.code = keys_union.code
             AND w.frequency = keys_union.frequency
            LEFT JOIN fund_history_cache cache
              ON cache.fund_type = keys_union.fund_type
             AND cache.code = keys_union.code
             AND cache.frequency = keys_union.frequency
            GROUP BY w.id, keys_union.fund_type, keys_union.code, keys_union.frequency, w.create_time
            ORDER BY watched DESC, keys_union.fund_type, keys_union.code, keys_union.frequency
            """)
    List<FundManagementItem> selectManagementItems();

    @Select("""
            SELECT DISTINCT fund_type AS fundType, code, frequency
            FROM fund_history_cache
            ORDER BY fund_type, code, frequency
            """)
    List<FundHistoryCacheKey> selectCachedKeys();

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
