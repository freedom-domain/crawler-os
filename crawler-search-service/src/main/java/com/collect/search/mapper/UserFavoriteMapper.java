package com.collect.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.search.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}