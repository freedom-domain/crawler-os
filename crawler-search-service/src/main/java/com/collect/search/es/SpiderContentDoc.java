package com.collect.search.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.annotation.Transient;

import java.util.List;


@Data
@Document(indexName = "spider_content", createIndex = false)
public class SpiderContentDoc {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String author;

    @Field(type = FieldType.Long)
    private Long spiderId;

    @Field(type = FieldType.Keyword)
    private String spiderName;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Date)
    private String crawlTime;

    @Field(type = FieldType.Date)
    private String updateTime;

    @Field(type = FieldType.Keyword)
    private List<String> images;

    @Transient
    private List<String> tags;

    /**
     * HTML 原文（不存入 ES，详情查询时从 MinIO 读取）。
     */
    @Transient
    private String rawHtml;
}
