package com.collect.ip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CfIpRangeProbeScheduleReq {

    @NotNull
    private Boolean enabled;

    @NotBlank
    @Pattern(regexp = "EVERY_5_MINUTES|EVERY_15_MINUTES|EVERY_30_MINUTES|HOURLY|DAILY|WEEKLY")
    private String frequency;

    @Min(1)
    @Max(5)
    private int samplesPerRange;

    @NotBlank
    @Size(max = 253)
    @Pattern(regexp = "(?i)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    private String tlsServerName;
}
