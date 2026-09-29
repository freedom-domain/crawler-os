package com.collect.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class LoginResp {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private List<String> permissions;
}
