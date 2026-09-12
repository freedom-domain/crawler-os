package com.collect.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.worker.entity.SpiderTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpiderTaskMapper extends BaseMapper<SpiderTask> {

    @Select("SELECT * FROM spider_task WHERE task_id = #{taskId} AND deleted = 0")
    SpiderTask selectByTaskId(Long taskId);

    @Update("UPDATE spider_task SET success_count = success_count + #{delta} WHERE id = #{id}")
    int incrSuccess(Long id, int delta);

    @Update("UPDATE spider_task SET fail_count = fail_count + #{delta} WHERE id = #{id}")
    int incrFail(Long id, int delta);
}
