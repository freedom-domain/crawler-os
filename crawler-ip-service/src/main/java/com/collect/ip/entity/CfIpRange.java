package com.collect.ip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cf_ip_range")
public class CfIpRange {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String cidr;
    private Integer ipVersion;
    private String region;
    private String colo;
    private Boolean active;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
