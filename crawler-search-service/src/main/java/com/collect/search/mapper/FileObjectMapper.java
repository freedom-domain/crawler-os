package com.collect.search.mapper;

import com.collect.search.entity.FileObjectRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface FileObjectMapper {

    @Select("""
            SELECT bucket, object_name AS objectName
            FROM file_metadata
            WHERE spider_id = #{spiderId} AND deleted = 0
            """)
    List<FileObjectRecord> selectBySpiderId(Long spiderId);

    @Select("""
            SELECT COUNT(*)
            FROM spider_task
            WHERE spider_id = #{spiderId}
              AND status IN ('PENDING', 'RUNNING', 'CANCELING')
              AND deleted = 0
            """)
    long countActiveTasksBySpiderId(Long spiderId);

    @Update("""
            UPDATE file_metadata
            SET deleted = 1
            WHERE spider_id = #{spiderId} AND deleted = 0
            """)
    int softDeleteBySpiderId(Long spiderId);
}
