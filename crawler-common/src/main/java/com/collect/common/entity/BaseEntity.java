package com.collect.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer deleted;

    @com.baomidou.mybatisplus.annotation.TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @com.baomidou.mybatisplus.annotation.TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
