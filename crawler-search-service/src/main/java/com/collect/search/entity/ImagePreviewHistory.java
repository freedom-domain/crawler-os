package com.collect.search.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.collect.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("image_preview_history")
public class ImagePreviewHistory extends BaseEntity {

    private Long userId;

    private String contentId;

    private String title;

    private String url;

    private String spiderName;

    private Integer imageCount;
}
