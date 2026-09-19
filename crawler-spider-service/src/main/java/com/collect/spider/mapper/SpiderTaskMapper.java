package com.collect.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.spider.entity.SpiderTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpiderTaskMapper extends BaseMapper<SpiderTask> {

    @Delete("DELETE FROM spider_task WHERE id = #{id}")
    void physicalDeleteById(@Param("id") Long id);
}
