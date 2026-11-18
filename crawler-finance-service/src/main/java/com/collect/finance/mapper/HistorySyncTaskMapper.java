package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.HistorySyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HistorySyncTaskMapper extends BaseMapper<HistorySyncTask> {

    @Update("""
            UPDATE history_sync_task
            SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = 'PENDING'
            """)
    int start(@Param("taskId") long taskId);

    @Update("""
            UPDATE history_sync_task
            SET completed_at = IF(status = 'RUNNING' AND finished_count + 1 >= total_count,
                                  CURRENT_TIMESTAMP, completed_at),
                finished_count = finished_count + 1,
                success_count = success_count + #{successIncrement},
                failed_count = failed_count + #{failedIncrement},
                status = IF(status = 'RUNNING' AND finished_count >= total_count, 'COMPLETED', status)
            WHERE id = #{taskId} AND status IN ('RUNNING', 'CANCELLING')
            """)
    int recordItemCompletion(@Param("taskId") long taskId,
                             @Param("successIncrement") int successIncrement,
                             @Param("failedIncrement") int failedIncrement);

    @Update("""
            UPDATE history_sync_task
            SET status = 'CANCELLED',
                finished_count = total_count,
                message = '用户取消了同步任务',
                completed_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = 'PENDING'
            """)
    int cancelPending(@Param("taskId") long taskId);

    @Update("""
            UPDATE history_sync_task
            SET status = 'CANCELLING', message = '正在停止同步任务'
            WHERE id = #{taskId} AND status = 'RUNNING'
            """)
    int requestCancellation(@Param("taskId") long taskId);

    @Update("""
            UPDATE history_sync_task
            SET status = 'CANCELLED',
                finished_count = total_count,
                message = '用户取消了同步任务',
                completed_at = CURRENT_TIMESTAMP
            WHERE id = #{taskId} AND status = 'CANCELLING'
            """)
    int finishCancellation(@Param("taskId") long taskId);
}
