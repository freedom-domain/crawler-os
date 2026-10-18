package com.collect.ip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ip.entity.CfIpRange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CfIpRangeMapper extends BaseMapper<CfIpRange> {

    @Select("""
            SELECT *
            FROM cf_ip_range
            WHERE active = 1
            ORDER BY ip_version ASC, cidr ASC
            """)
    List<CfIpRange> selectActive();

    @org.apache.ibatis.annotations.Update("UPDATE cf_ip_range SET active = 0")
    int deactivateAll();

    @Insert("""
            INSERT INTO cf_ip_range (cidr, ip_version, region, active)
            VALUES (#{cidr}, #{ipVersion}, '全球任播', 1)
            ON DUPLICATE KEY UPDATE
                ip_version = VALUES(ip_version),
                active = 1,
                update_time = CURRENT_TIMESTAMP
            """)
    int upsertOfficialRange(CfIpRange range);
}
