package com.collect.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.spider.entity.SpiderTaskLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SpiderTaskLogMapper extends BaseMapper<SpiderTaskLog> {

    @Delete("DELETE FROM spider_task_log WHERE task_id = #{taskId}")
    int physicalDeleteByTaskId(@Param("taskId") Long taskId);
}
