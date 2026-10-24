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
     * 按唯一键原子插入或更新元数据；字段均相同则保留原 update_time。
     */
    @Insert("INSERT INTO file_metadata " +
            "(bucket, object_name, file_name, title, content_type, file_size, category, spider_id, source, referrer, create_time, update_time) " +
            "VALUES (#{bucket}, #{objectName}, #{fileName}, #{title}, #{contentType}, #{fileSize}, #{category}, #{spiderId}, #{source}, #{referrer}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE " +
            "update_time = IF(NOT (bucket <=> VALUES(bucket) " +
            "AND object_name <=> VALUES(object_name) " +
            "AND file_name <=> VALUES(file_name) " +
            "AND title <=> VALUES(title) " +
            "AND content_type <=> VALUES(content_type) " +
            "AND file_size <=> VALUES(file_size) " +
            "AND category <=> VALUES(category) " +
            "AND spider_id <=> VALUES(spider_id) " +
            "AND source <=> VALUES(source) " +
            "AND referrer <=> VALUES(referrer) " +
            "AND deleted <=> 0), NOW(), update_time), " +
            "bucket = VALUES(bucket), object_name = VALUES(object_name), file_name = VALUES(file_name), " +
            "title = VALUES(title), content_type = VALUES(content_type), file_size = VALUES(file_size), " +
            "category = VALUES(category), spider_id = VALUES(spider_id), source = VALUES(source), " +
            "referrer = VALUES(referrer), deleted = 0")
    int upsert(FileMetadata metadata);

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
