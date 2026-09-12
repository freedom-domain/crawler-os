package com.collect.worker.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "spider_content")
public class SpiderContentDoc {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String content;

    @Field(type = FieldType.Keyword)
    private String url;

    @Field(type = FieldType.Text)
    private String author;

    @Field(type = FieldType.Long)
    private Long spiderId;

    @Field(type = FieldType.Keyword)
    private String spiderName;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Date)
    private LocalDateTime crawlTime;

    @Field(type = FieldType.Keyword)
    private String fileId;
}
