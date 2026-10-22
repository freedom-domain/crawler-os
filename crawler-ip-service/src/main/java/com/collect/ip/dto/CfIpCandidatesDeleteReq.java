package com.collect.ip.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CfIpCandidatesDeleteReq {

    @NotEmpty
    private List<@NotNull Long> ids;
}
