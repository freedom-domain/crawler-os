package com.collect.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.spider.entity.SpiderTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpiderTaskMapper extends BaseMapper<SpiderTask> {

    @Select({
        "<script>",
            "SELECT t.*, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 1) AS html_success_count, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 0) AS html_fail_count, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 2) AS html_existing_count, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 1) AS image_success_count, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 0) AS image_fail_count, "
                + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 2) AS image_existing_count",
        "FROM spider_task t",
        "WHERE t.deleted = 0",
        "<if test='spiderId != null'>AND t.spider_id = #{spiderId}</if>",
        "<if test=\"status != null and status != ''\">AND t.status = #{status}</if>",
        "ORDER BY t.create_time DESC",
        "</script>"
    })
        @Results({
            @Result(column = "html_success_count", property = "htmlSuccessCount"),
            @Result(column = "html_fail_count", property = "htmlFailCount"),
            @Result(column = "html_existing_count", property = "htmlExistingCount"),
            @Result(column = "image_success_count", property = "imageSuccessCount"),
            @Result(column = "image_fail_count", property = "imageFailCount"),
            @Result(column = "image_existing_count", property = "imageExistingCount")
        })
    IPage<SpiderTask> selectTaskPage(IPage<SpiderTask> page,
                      @Param("spiderId") Long spiderId,
                      @Param("status") String status);

        @Select("SELECT t.*, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 1) AS html_success_count, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 0) AS html_fail_count, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'html' AND l.status = 2) AS html_existing_count, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 1) AS image_success_count, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 0) AS image_fail_count, "
            + "(SELECT COUNT(*) FROM spider_task_log l WHERE l.task_id = t.id AND l.deleted = 0 AND l.type = 'image' AND l.status = 2) AS image_existing_count "
        + "FROM spider_task t WHERE t.id = #{id} AND t.deleted = 0")
        @Results({
            @Result(column = "html_success_count", property = "htmlSuccessCount"),
            @Result(column = "html_fail_count", property = "htmlFailCount"),
            @Result(column = "html_existing_count", property = "htmlExistingCount"),
            @Result(column = "image_success_count", property = "imageSuccessCount"),
            @Result(column = "image_fail_count", property = "imageFailCount"),
            @Result(column = "image_existing_count", property = "imageExistingCount")
        })
    SpiderTask selectTaskById(@Param("id") Long id);

    @Delete("DELETE FROM spider_task WHERE id = #{id}")
    void physicalDeleteById(@Param("id") Long id);
}
