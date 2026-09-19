package com.collect.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.worker.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    @Select("SELECT * FROM file_metadata WHERE bucket = #{bucket} AND object_name = #{objectName} AND deleted = 0 LIMIT 1")
    FileMetadata selectByObject(String bucket, String objectName);
}
