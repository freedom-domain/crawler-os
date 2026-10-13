package com.collect.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.worker.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    @Select("SELECT * FROM file_metadata WHERE bucket = #{bucket} AND object_name = #{objectName} AND deleted = 0 LIMIT 1")
    FileMetadata selectByObject(String bucket, String objectName);

    @Select("SELECT * FROM file_metadata WHERE source = #{source} AND deleted = 0 LIMIT 1")
    FileMetadata selectBySource(String source);

    /**
     * 幂等插入：忽略 object_name 或 source 重复的行，避免多 worker 并发竞争。
     * 返回 1=插入成功，0=已存在（被忽略）。
     */
    @Insert("INSERT IGNORE INTO file_metadata " +
            "(bucket, object_name, file_name, title, content_type, file_size, category, spider_id, source, create_time, update_time) " +
            "VALUES (#{bucket}, #{objectName}, #{fileName}, #{title}, #{contentType}, #{fileSize}, #{category}, #{spiderId}, #{source}, NOW(), NOW())")
    int insertIgnore(FileMetadata metadata);

    /**
     * 批量查询：按 bucket + objectName 列表查询，避免 N+1。
     * 用于读取缓存模式下批量发现已缓存的页面 URL。
     */
    @Select("<script>" +
            "SELECT * FROM file_metadata WHERE bucket = #{bucket} AND deleted = 0 AND object_name IN " +
            "<foreach collection='objectNames' item='name' open='(' separator=',' close=')'>#{name}</foreach>" +
            "</script>")
    List<FileMetadata> selectByObjects(@Param("bucket") String bucket,
                                       @Param("objectNames") List<String> objectNames);
}
