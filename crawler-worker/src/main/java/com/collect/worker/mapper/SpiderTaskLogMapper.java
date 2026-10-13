package com.collect.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.worker.entity.SpiderTaskLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpiderTaskLogMapper extends BaseMapper<SpiderTaskLog> {

    /**
     * 批量插入任务日志：1 次 SQL 替代 N 次单条 INSERT。
     */
    @Insert("<script>" +
            "INSERT INTO spider_task_log (task_id, spider_id, url, status, level, type, message, cost_ms, create_time, update_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.taskId}, #{item.spiderId}, #{item.url}, #{item.status}, #{item.level}, #{item.type}, #{item.message}, #{item.costMs}, #{item.createTime}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<SpiderTaskLog> list);
}
