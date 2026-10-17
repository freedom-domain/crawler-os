package com.collect.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HistorySyncSettingMapper {

    @Select("SELECT max_concurrent_requests FROM history_sync_setting WHERE id = 1")
    Integer selectConcurrency();

    @Update("""
            UPDATE history_sync_setting
            SET max_concurrent_requests = #{concurrency}
            WHERE id = 1
            """)
    int updateConcurrency(@Param("concurrency") int concurrency);
}
