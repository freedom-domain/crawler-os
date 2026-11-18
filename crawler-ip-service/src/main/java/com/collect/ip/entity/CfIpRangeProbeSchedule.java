package com.collect.ip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cf_ip_range_probe_schedule")
public class CfIpRangeProbeSchedule {

    @TableId(type = IdType.INPUT)
    private Integer id;
    private Boolean enabled;
    private String frequency;
    private Integer samplesPerRange;
    private String tlsServerName;
    private LocalDateTime lastRunAt;
    private LocalDateTime updateTime;
}
