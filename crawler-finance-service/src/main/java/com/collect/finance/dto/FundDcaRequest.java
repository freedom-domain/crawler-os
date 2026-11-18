package com.collect.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FundDcaRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String startDate;

    @NotBlank
    private String endDate;

    @NotNull
    @DecimalMin(value = "1.00")
    private BigDecimal amount;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer weekday;
}
