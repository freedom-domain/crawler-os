package com.collect.ip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.ip.entity.CfIpCandidate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CfIpCandidateMapper extends BaseMapper<CfIpCandidate> {

    @Select("""
            SELECT *
            FROM cf_ip_candidate
            ORDER BY
                CASE WHEN tls_ms IS NOT NULL THEN 0 ELSE 1 END,
                CASE WHEN speed_mbps IS NULL THEN 1 ELSE 0 END,
                speed_mbps DESC,
                CASE WHEN ping_ms IS NULL THEN 1 ELSE 0 END,
                ping_ms ASC,
                id DESC
            """)
    List<CfIpCandidate> selectAllOrdered();

    @Select("SELECT ip_address FROM cf_ip_candidate")
    List<String> selectAllIpAddresses();

    @Insert("""
            <script>
            INSERT IGNORE INTO cf_ip_candidate (ip_address, region, source_range_id, status)
            VALUES
            <foreach collection="candidates" item="candidate" separator=",">
                (#{candidate.ipAddress}, #{candidate.region}, #{candidate.sourceRangeId}, 'PENDING')
            </foreach>
            </script>
            """)
    int insertIgnoreBatch(@Param("candidates") List<CfIpCandidate> candidates);

    @Select("SELECT * FROM cf_ip_candidate " +
            "WHERE status = 'AVAILABLE' AND speed_mbps IS NOT NULL " +
            "ORDER BY RAND() LIMIT #{count}")
    List<CfIpCandidate> selectRandomAvailableCandidates(@Param("count") int count);

    @Select("SELECT * FROM cf_ip_candidate WHERE ip_address = #{ipAddress} LIMIT 1")
    CfIpCandidate selectByIpAddress(@Param("ipAddress") String ipAddress);

    @Select("""
            <script>
            SELECT * FROM cf_ip_candidate
            WHERE id IN
            <foreach item="id" collection="ids" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    List<CfIpCandidate> selectByIds(@Param("ids") List<Long> ids);

    @Delete("""
            <script>
            DELETE FROM cf_ip_candidate
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    int deleteByIds(@Param("ids") List<Long> ids);

    @Update("""
            UPDATE cf_ip_candidate
            SET latency_ms = #{latencyMs},
                ping_ms = #{pingMs},
                tls_ms = #{tlsMs},
                speed_mbps = #{speedMbps},
                colo = COALESCE(#{colo}, colo),
                colo_updated_at = COALESCE(#{coloUpdatedAt}, colo_updated_at),
                status = #{status},
                error_message = #{errorMessage},
                last_tested_at = #{lastTestedAt},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateProbeResult(CfIpCandidate candidate);

    @Update("""
            UPDATE cf_ip_candidate
            SET region = #{region},
                update_time = CURRENT_TIMESTAMP
            WHERE source_range_id = #{rangeId}
            """)
    int updateRangeRegion(@Param("rangeId") Long rangeId, @Param("region") String region);

}
