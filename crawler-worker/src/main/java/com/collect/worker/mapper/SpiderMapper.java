package com.collect.worker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.worker.entity.Spider;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫配置 mapper（worker 侧），用于读取缓存模式回写起始URL。
 */
@Mapper
public interface SpiderMapper extends BaseMapper<Spider> {
}
