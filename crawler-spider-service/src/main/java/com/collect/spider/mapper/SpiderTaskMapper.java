package com.collect.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.collect.spider.entity.SpiderTask;
import com.collect.spider.dto.TaskStatsResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SpiderTaskMapper extends BaseMapper<SpiderTask> {

    @Select("SELECT id FROM spider_task_creation_guard WHERE id = 1 FOR UPDATE")
    Long lockTaskCreation();

    @Select("SELECT * FROM spider_task WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SpiderTask selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM spider_task "
            + "WHERE deleted = 0 AND status IN ('RUNNING', 'CANCELING')")
    long countActiveTasks();

    @Select("SELECT * FROM spider_task WHERE deleted = 0 AND status = 'PENDING' "
            + "ORDER BY create_time ASC, id ASC LIMIT 1 FOR UPDATE")
    SpiderTask selectNextPendingTaskForUpdate();

    @Select({
        "<script>",
            "SELECT t.*, stats.html_success_count, stats.html_fail_count, stats.html_existing_count, "
                + "stats.image_success_count, stats.image_fail_count, stats.image_existing_count",
        "FROM spider_task t",
        "LEFT JOIN (",
            "SELECT l.task_id, "
                + "SUM(CASE WHEN l.type = 'html' AND l.status = 1 THEN 1 ELSE 0 END) AS html_success_count, "
                + "SUM(CASE WHEN l.type = 'html' AND l.status = 0 THEN 1 ELSE 0 END) AS html_fail_count, "
                + "SUM(CASE WHEN l.type = 'html' AND l.status = 2 THEN 1 ELSE 0 END) AS html_existing_count, "
                + "SUM(CASE WHEN l.type = 'image' AND l.status = 1 THEN 1 ELSE 0 END) AS image_success_count, "
                + "SUM(CASE WHEN l.type = 'image' AND l.status = 0 THEN 1 ELSE 0 END) AS image_fail_count, "
                + "SUM(CASE WHEN l.type = 'image' AND l.status = 2 THEN 1 ELSE 0 END) AS image_existing_count",
            "FROM spider_task_log l",
            "WHERE l.deleted = 0",
            "GROUP BY l.task_id",
        ") stats ON stats.task_id = t.id",
        "WHERE t.deleted = 0",
        "<if test='spiderId != null'>AND t.spider_id = #{spiderId}</if>",
        "<if test=\"status != null and status != ''\">AND t.status = #{status}</if>",
        "ORDER BY t.create_time DESC, t.id DESC",
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

            @Select("SELECT t.spider_name, t.status, t.success_count, t.fail_count, t.create_time "
                + "FROM spider_task t WHERE t.deleted = 0 ORDER BY t.create_time DESC LIMIT #{limit}")
        java.util.List<SpiderTask> selectRecentTasks(@Param("limit") int limit);

        @Select("SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END), 0) AS success "
                + "FROM spider_task WHERE deleted = 0 AND create_time >= CURDATE()")
        TaskStatsResponse selectTodayTaskStats();

        @Select("SELECT t.*, stats.html_success_count, stats.html_fail_count, stats.html_existing_count, "
            + "stats.image_success_count, stats.image_fail_count, stats.image_existing_count "
        + "FROM spider_task t LEFT JOIN ("
            + "SELECT l.task_id, "
            + "SUM(CASE WHEN l.type = 'html' AND l.status = 1 THEN 1 ELSE 0 END) AS html_success_count, "
            + "SUM(CASE WHEN l.type = 'html' AND l.status = 0 THEN 1 ELSE 0 END) AS html_fail_count, "
            + "SUM(CASE WHEN l.type = 'html' AND l.status = 2 THEN 1 ELSE 0 END) AS html_existing_count, "
            + "SUM(CASE WHEN l.type = 'image' AND l.status = 1 THEN 1 ELSE 0 END) AS image_success_count, "
            + "SUM(CASE WHEN l.type = 'image' AND l.status = 0 THEN 1 ELSE 0 END) AS image_fail_count, "
            + "SUM(CASE WHEN l.type = 'image' AND l.status = 2 THEN 1 ELSE 0 END) AS image_existing_count "
            + "FROM spider_task_log l WHERE l.task_id = #{id} AND l.deleted = 0 GROUP BY l.task_id"
        + ") stats ON stats.task_id = t.id "
        + "WHERE t.id = #{id} AND t.deleted = 0")
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
