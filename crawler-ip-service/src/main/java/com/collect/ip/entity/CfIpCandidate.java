package com.collect.ip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cf_ip_candidate")
public class CfIpCandidate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ipAddress;
    private String region;
    private Long sourceRangeId;
    @TableField(exist = false)
    private String tlsServerName;
    private Integer latencyMs;
    private BigDecimal pingMs;
    private BigDecimal tlsMs;
    private BigDecimal speedMbps;
    private String status;
    private String errorMessage;
    private LocalDateTime lastTestedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
