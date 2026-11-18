package com.collect.ip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CfIpCandidatesReq {

    @NotBlank
    @Size(max = 100_000)
    private String ips;
}
