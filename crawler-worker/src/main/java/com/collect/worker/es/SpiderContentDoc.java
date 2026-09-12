package com.collect.worker.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;



@Data
@Document(indexName = "spider_content", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class SpiderContentDoc {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
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
    private String crawlTime;

    @Field(type = FieldType.Keyword)
    private String fileId;
}
