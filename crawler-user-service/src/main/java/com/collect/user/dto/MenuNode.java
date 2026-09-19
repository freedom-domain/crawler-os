package com.collect.user.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuNode {

    private Long id;
    private String name;
    private String code;
    private String path;
    private Integer type;
    private Integer sort;
    private String icon;
    private List<MenuNode> children = new ArrayList<>();
}
