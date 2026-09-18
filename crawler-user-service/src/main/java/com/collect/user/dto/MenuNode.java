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
    private Integer sort;
    private List<MenuNode> children = new ArrayList<>();
}
