package com.collect.ip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ip.dto.CfIpRangeProbeScheduleReq;
import com.collect.ip.entity.CfIpRangeProbeSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CfIpRangeProbeScheduleMapper extends BaseMapper<CfIpRangeProbeSchedule> {

    @Select("SELECT * FROM cf_ip_range_probe_schedule WHERE id = 1")
    CfIpRangeProbeSchedule selectSettings();

    @Update("""
            UPDATE cf_ip_range_probe_schedule
            SET enabled = #{settings.enabled},
                frequency = #{settings.frequency},
                samples_per_range = #{settings.samplesPerRange},
                tls_server_name = #{settings.tlsServerName},
                last_run_at = NULL,
                update_time = CURRENT_TIMESTAMP
            WHERE id = 1
            """)
    int updateSettings(@Param("settings") CfIpRangeProbeScheduleReq settings);

    @Update("""
            UPDATE cf_ip_range_probe_schedule
            SET last_run_at = #{runAt}, update_time = CURRENT_TIMESTAMP
            WHERE id = 1
              AND enabled = 1
              AND (last_run_at IS NULL OR last_run_at < #{runAt})
            """)
    int claimRun(@Param("runAt") LocalDateTime runAt);
}
