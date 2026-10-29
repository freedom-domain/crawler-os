package com.collect.ip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.dto.CfIpCandidateSummary;
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
                CASE WHEN last_tested_at IS NULL THEN 1 ELSE 0 END,
                last_tested_at DESC,
                CASE WHEN speed_mbps IS NULL THEN 1 ELSE 0 END,
                speed_mbps DESC,
                CASE WHEN tls_ms IS NULL THEN 1 ELSE 0 END,
                tls_ms ASC,
                id DESC
            """)
    List<CfIpCandidate> selectAllOrdered();

    @Select("""
            <script>
            SELECT *
            FROM cf_ip_candidate
            <where>
                <if test="keyword != null and keyword != ''">
                    AND ip_address LIKE CONCAT(#{keyword}, '%')
                </if>
                <if test="ipVersion == 4">
                    AND ip_address NOT LIKE '%:%'
                </if>
                <if test="ipVersion == 6">
                    AND ip_address LIKE '%:%'
                </if>
                <if test="region != null and region != ''">
                    AND region = #{region}
                </if>
                <if test="colo != null and colo != ''">
                    AND colo = #{colo}
                </if>
                <if test="status != null and status != ''">
                    AND status = #{status}
                </if>
                <if test="maxTlsMs != null">
                    AND tls_ms IS NOT NULL AND tls_ms &lt;= #{maxTlsMs}
                </if>
                <if test="minSpeedMbps != null">
                    AND speed_mbps IS NOT NULL AND speed_mbps &gt;= #{minSpeedMbps}
                </if>
            </where>
            <choose>
                <when test="sortColumn != null and sortDirection != null">
                    ORDER BY CASE WHEN ${sortColumn} IS NULL THEN 1 ELSE 0 END,
                             ${sortColumn} ${sortDirection}, id DESC
                </when>
                <otherwise>
                    ORDER BY
                        CASE WHEN last_tested_at IS NULL THEN 1 ELSE 0 END,
                        last_tested_at DESC,
                        CASE WHEN speed_mbps IS NULL THEN 1 ELSE 0 END,
                        speed_mbps DESC,
                        CASE WHEN tls_ms IS NULL THEN 1 ELSE 0 END,
                        tls_ms ASC,
                        id DESC
                </otherwise>
            </choose>
            </script>
            """)
    IPage<CfIpCandidate> selectCandidatePage(Page<CfIpCandidate> page,
                                             @Param("keyword") String keyword,
                                             @Param("ipVersion") Integer ipVersion,
                                             @Param("region") String region,
                                             @Param("colo") String colo,
                                             @Param("status") String status,
                                             @Param("maxTlsMs") Integer maxTlsMs,
                                             @Param("minSpeedMbps") Integer minSpeedMbps,
                                             @Param("sortColumn") String sortColumn,
                                             @Param("sortDirection") String sortDirection);

    @Select("""
            SELECT COUNT(*) AS total,
                   COALESCE(SUM(status = 'AVAILABLE'), 0) AS available_count,
                   COALESCE(SUM(status = 'AVAILABLE' AND latency_ms IS NOT NULL), 0) AS available_address_count,
                   COALESCE(SUM(status = 'PENDING'), 0) AS pending_count,
                   COALESCE(SUM(status = 'FAILED'), 0) AS failed_count,
                   MIN(CASE WHEN status = 'AVAILABLE' AND latency_ms IS NOT NULL THEN tls_ms END) AS fastest_tls_ms
            FROM cf_ip_candidate
            """)
    CfIpCandidateSummary selectSummary();

    @Select("SELECT DISTINCT region FROM cf_ip_candidate WHERE region IS NOT NULL ORDER BY region")
    List<String> selectDistinctRegions();

    @Select("SELECT DISTINCT colo FROM cf_ip_candidate WHERE colo IS NOT NULL AND colo != '' ORDER BY colo")
    List<String> selectDistinctColos();

    @Select("SELECT id FROM cf_ip_candidate ORDER BY id")
    List<Long> selectAllCandidateIds();

    @Select("SELECT id FROM cf_ip_candidate WHERE status = #{status} ORDER BY id")
    List<Long> selectCandidateIdsByStatus(@Param("status") String status);

    @Select("SELECT ip_address FROM cf_ip_candidate WHERE status = 'AVAILABLE' AND latency_ms IS NOT NULL ORDER BY id")
    List<String> selectAvailableCandidateIpAddresses();

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

        @Select("""
                        SELECT *
                        FROM cf_ip_candidate
                        WHERE source_range_id = #{rangeId}
                            AND status = 'PENDING'
                        ORDER BY id ASC
                        LIMIT #{limit}
                        """)
        List<CfIpCandidate> selectUntestedBySourceRangeId(@Param("rangeId") Long rangeId,
                                                                                                             @Param("limit") int limit);

                                                            @Select("""
                                                                    <script>
                                                                    SELECT ip_address
                                                                    FROM cf_ip_candidate
                                                                    WHERE ip_address IN
                                                                    <foreach collection="ipAddresses" item="ip" open="(" separator="," close=")">
                                                                        #{ip}
                                                                    </foreach>
                                                                    </script>
                                                                    """)
                                                            List<String> selectExistingIpAddresses(@Param("ipAddresses") List<String> ipAddresses);

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
