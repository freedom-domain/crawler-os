package com.collect.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
public class LoginUser implements Serializable {

    private Long userId;
    private String username;
    private String nickname;
    private Long roleId;
    private String roleName;
    private String roleCode;
    private Set<String> permissions;
}
