package com.collect.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleSaveReq {

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称最长64位")
    private String name;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 64, message = "角色编码最长64位")
    private String code;

    private String description;
    private Integer status;
}
