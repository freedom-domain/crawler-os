package com.collect.ip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ip.entity.CfIpRange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

        @Select("""
                        <script>
                        SELECT *
                        FROM cf_ip_range
                        WHERE active = 1
                        <if test="cidr != null and cidr != ''">
                                AND cidr LIKE CONCAT('%', #{cidr}, '%')
                        </if>
                        <if test="ipVersion != null">
                                AND ip_version = #{ipVersion}
                        </if>
                        <if test="region != null and region != ''">
                                AND region LIKE CONCAT('%', #{region}, '%')
                        </if>
                        <if test="colo != null and colo != ''">
                                AND colo = #{colo}
                        </if>
                        ORDER BY ip_version ASC, cidr ASC
                        </script>
                        """)
        IPage<CfIpRange> selectActiveRangePage(Page<CfIpRange> page,
                                                                                  @Param("cidr") String cidr,
                                                                                  @Param("ipVersion") Integer ipVersion,
                                                                                  @Param("region") String region,
                                                                                  @Param("colo") String colo);

    @Select("""
            SELECT *
            FROM cf_ip_range
            WHERE active = 1 AND enabled = 1
            ORDER BY ip_version ASC, cidr ASC
            """)
    List<CfIpRange> selectEnabled();

    @org.apache.ibatis.annotations.Update("UPDATE cf_ip_range SET active = 0")
    int deactivateAll();

    @Update("""
            UPDATE cf_ip_range
            SET colo = (
                SELECT colo
                FROM cf_ip_candidate
                WHERE source_range_id = #{rangeId} AND colo IS NOT NULL
                GROUP BY colo
                ORDER BY COUNT(*) DESC, MAX(colo_updated_at) DESC, colo ASC
                LIMIT 1
            ),
            update_time = CURRENT_TIMESTAMP
            WHERE id = #{rangeId}
            """)
    int refreshColoFromCandidateObservations(@Param("rangeId") Long rangeId);

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
