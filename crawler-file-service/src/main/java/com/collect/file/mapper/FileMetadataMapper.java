package com.collect.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.file.entity.FileMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileMetadataMapper extends BaseMapper<FileMetadata> {

    @Select("<script>"
			+ "SELECT f.*, s.name AS crawler_name FROM file_metadata f "
	    + "LEFT JOIN spider s ON s.id = f.spider_id AND s.deleted = 0 "
	    + "WHERE f.deleted = 0 "
			+ "<if test='category != null and category != \"\"'>AND f.category = #{category}</if> "
	    + "<if test='spiderId != null'>AND f.spider_id = #{spiderId}</if> "
			+ "<if test='fileName != null and fileName != \"\"'>AND f.file_name LIKE CONCAT('%', #{fileName}, '%')</if> "
	    + "<if test='source != null and source != \"\"'>AND f.source LIKE CONCAT('%', #{source}, '%')</if> "
	    + "<if test='referrer != null and referrer != \"\"'>AND f.referrer LIKE CONCAT('%', #{referrer}, '%')</if> "
	    + "<if test='title != null and title != \"\"'>"
			+ "<choose>"
			+ "<when test='useTitleFullText'>AND MATCH(f.title) AGAINST (#{titleSearch} IN BOOLEAN MODE)</when> "
			+ "<otherwise>AND f.title LIKE CONCAT('%', #{title}, '%')</otherwise> "
			+ "</choose>"
			+ "</if> "
	    + "ORDER BY f.create_time DESC, f.id DESC"
	    + "</script>")
    IPage<FileMetadata> selectFilePage(Page<FileMetadata> page,
				       @Param("category") String category,
									   @Param("spiderId") Long spiderId,
									   @Param("fileName") String fileName,
									   @Param("title") String title,
									   @Param("titleSearch") String titleSearch,
									   @Param("useTitleFullText") boolean useTitleFullText,
									   @Param("source") String source,
									   @Param("referrer") String referrer);

    @Select("SELECT * FROM file_metadata WHERE object_name = #{objectName} AND deleted = 0 LIMIT 1")
    FileMetadata selectByObjectName(@Param("objectName") String objectName);

    @Select("SELECT * FROM file_metadata WHERE source = #{source} AND deleted = 0 LIMIT 1")
    FileMetadata selectBySource(@Param("source") String source);

}
