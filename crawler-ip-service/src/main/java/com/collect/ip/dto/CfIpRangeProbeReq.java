package com.collect.ip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CfIpRangeProbeReq {

    @Min(1)
    @Max(5)
    private int samplesPerRange = 1;

    @NotBlank
    @Size(max = 253)
    @Pattern(regexp = "(?i)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    private String tlsServerName;
}
