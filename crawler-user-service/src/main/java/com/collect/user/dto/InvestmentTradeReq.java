package com.collect.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InvestmentTradeReq {

    @NotBlank
    private String code;

    @NotBlank
    private String side;

    @NotNull
    @Min(100)
    private Integer quantity;
}
