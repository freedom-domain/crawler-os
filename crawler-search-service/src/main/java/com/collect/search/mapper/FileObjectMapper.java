package com.collect.search.mapper;

import com.collect.search.entity.FileObjectRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileObjectMapper {

    @Select("""
            SELECT bucket, object_name AS objectName
            FROM file_metadata
            WHERE spider_id = #{spiderId}
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

    @Delete("""
            DELETE FROM file_metadata
            WHERE spider_id = #{spiderId}
            """)
    int deleteBySpiderId(Long spiderId);
}
