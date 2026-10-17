package com.collect.finance.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("fund_refresh_setting")
public class FundRefreshSetting {
    @TableId
    private Integer id;
    private String frequency;
    private String lastRunSlot;
}
