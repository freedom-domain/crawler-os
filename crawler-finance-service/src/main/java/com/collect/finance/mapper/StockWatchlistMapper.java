package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.dto.StockWatchlistItem;
import com.collect.finance.entity.StockWatchlist;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockWatchlistMapper extends BaseMapper<StockWatchlist> {

    @Select("""
            SELECT w.code, COALESCE(s.name, c.name, w.code) AS name,
                   s.market, s.industry, COALESCE(c.cachedPoints, 0) AS cachedPoints,
                   c.latestDate, w.create_time AS addedAt
            FROM stock_watchlist w
            LEFT JOIN stock_catalog s ON s.code = w.code
            LEFT JOIN (
                SELECT code, MAX(name) AS name, COUNT(*) AS cachedPoints, MAX(trade_date) AS latestDate
                FROM stock_history_cache
                GROUP BY code
            ) c ON c.code = w.code
            ORDER BY w.create_time DESC, w.code
            """)
    List<StockWatchlistItem> selectWatchlist();

    @Insert("""
            INSERT IGNORE INTO stock_watchlist (code) VALUES (#{code})
            """)
    int add(@Param("code") String code);

    @Delete("""
            DELETE FROM stock_watchlist WHERE code = #{code}
            """)
    int remove(@Param("code") String code);
}
