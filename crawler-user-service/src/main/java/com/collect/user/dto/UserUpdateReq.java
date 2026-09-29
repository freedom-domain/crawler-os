package com.collect.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateReq {

    @Size(min = 3, max = 32, message = "用户名长度3-32位")
    private String username;

    private String nickname;
    private String email;
    private String phone;
    private Integer status;
    private Long roleId;

    /** 可选：重置密码（为空则不修改） */
    @Size(min = 6, max = 64, message = "密码长度6-64位")
    private String password;
}
