package com.collect.ip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CfIpProbeReq {

    @NotEmpty
    @Size(max = 100)
    private List<Long> ids;

    @NotBlank
    @Size(max = 253)
    @Pattern(regexp = "(?i)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    private String tlsServerName;
}
