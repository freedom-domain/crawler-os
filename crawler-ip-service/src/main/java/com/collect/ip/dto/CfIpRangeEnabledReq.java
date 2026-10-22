package com.collect.ip.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CfIpRangeEnabledReq {

    @NotNull
    private Boolean enabled;
}
