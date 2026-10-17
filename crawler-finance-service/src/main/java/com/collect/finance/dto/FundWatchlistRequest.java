package com.collect.finance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FundWatchlistRequest {

    @NotBlank
    private String fundType;

    @NotBlank
    private String code;

    @NotBlank
    private String frequency;
}
