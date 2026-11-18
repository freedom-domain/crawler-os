package com.collect.finance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HistorySyncSettingMapper {

    @Select("SELECT max_concurrent_requests FROM history_sync_setting WHERE id = 1")
    Integer selectConcurrency();

    @Select("SELECT request_interval_millis FROM history_sync_setting WHERE id = 1")
    Integer selectRequestIntervalMillis();

    @Select("SELECT enabled_apis FROM history_sync_setting WHERE id = 1")
    String selectEnabledApis();

    @Update("""
            UPDATE history_sync_setting
            SET max_concurrent_requests = #{concurrency},
                enabled_apis = #{enabledApis},
                request_interval_millis = #{requestIntervalMillis}
            WHERE id = 1
            """)
    int updateSettings(@Param("concurrency") int concurrency, @Param("enabledApis") String enabledApis,
                       @Param("requestIntervalMillis") int requestIntervalMillis);
}
