package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.entity.HistorySyncTaskItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface HistorySyncTaskItemMapper extends BaseMapper<HistorySyncTaskItem> {

    @Insert("""
            INSERT INTO history_sync_task_item
                (task_id, instrument_type, fund_type, code, name, status)
            SELECT #{taskId}, 'FUND', fund_type, code, name, 'PENDING'
            FROM fund_catalog
            """)
    int insertFundItems(@Param("taskId") long taskId);

    @Insert("""
            INSERT INTO history_sync_task_item
                (task_id, instrument_type, code, name, status)
            SELECT #{taskId}, 'STOCK', code, name, 'PENDING'
            FROM stock_catalog
            """)
    int insertStockItems(@Param("taskId") long taskId);

    @Select("""
            SELECT * FROM history_sync_task_item
            WHERE task_id = #{taskId}
            ORDER BY CASE status
                WHEN 'RUNNING' THEN 0
                WHEN 'FAILED' THEN 1
                WHEN 'SUCCESS' THEN 2
                ELSE 3
            END, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<HistorySyncTaskItem> selectByTaskId(@Param("taskId") long taskId,
                                             @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            SELECT * FROM history_sync_task_item
            WHERE task_id = #{taskId}
              AND id > #{afterId}
              AND status = 'PENDING'
            ORDER BY id
            LIMIT #{limit}
            """)
    List<HistorySyncTaskItem> selectNextPendingBatch(@Param("taskId") long taskId,
                                                     @Param("afterId") long afterId,
                                                     @Param("limit") int limit);

    @Update("""
            UPDATE history_sync_task_item
            SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP
            WHERE id = #{itemId}
              AND task_id = #{taskId}
              AND status = 'PENDING'
              AND EXISTS (
                  SELECT 1 FROM history_sync_task
                  WHERE id = #{taskId} AND status = 'RUNNING'
              )
            """)
    int claimForProcessing(@Param("taskId") long taskId, @Param("itemId") long itemId);

    @Update("""
            UPDATE history_sync_task_item
            SET status = 'CANCELLED',
                message = '任务已取消，未开始同步',
                finished_at = CURRENT_TIMESTAMP
            WHERE task_id = #{taskId} AND status = 'PENDING'
            """)
    int cancelPendingItems(@Param("taskId") long taskId);

    @Update("""
            UPDATE history_sync_task_item
            SET status = 'CANCELLED',
                message = '服务重启时取消任务',
                finished_at = CURRENT_TIMESTAMP
            WHERE task_id = #{taskId} AND status = 'RUNNING'
            """)
    int cancelRunningItems(@Param("taskId") long taskId);

    @Select("SELECT COUNT(*) FROM history_sync_task_item WHERE task_id = #{taskId}")
    long countByTaskId(@Param("taskId") long taskId);

    @Select("""
            SELECT COUNT(*) FROM history_sync_task_item
            WHERE task_id = #{taskId} AND status = 'CANCELLED'
            """)
    int countCancelledByTaskId(@Param("taskId") long taskId);
}
