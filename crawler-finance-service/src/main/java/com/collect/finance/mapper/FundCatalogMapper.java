package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.dto.FundCatalogItem;
import com.collect.finance.entity.FundCatalog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FundCatalogMapper extends BaseMapper<FundCatalog> {

    @Select("""
            <script>
            SELECT f.fund_type AS fundType, f.code, f.name, f.category, f.pinyin,
                   EXISTS(
                       SELECT 1 FROM fund_watchlist w
                       WHERE w.fund_type = f.fund_type AND w.code = f.code
                   ) AS watched,
                   (SELECT GROUP_CONCAT(DISTINCT w.frequency ORDER BY w.frequency)
                    FROM fund_watchlist w
                    WHERE w.fund_type = f.fund_type AND w.code = f.code) AS watchlistFrequencies,
                   f.synced_at AS syncedAt
            FROM fund_catalog f
            <where>
                <if test="fundType != null and fundType != ''">
                    f.fund_type = #{fundType}
                </if>
                <if test="keyword != null and keyword != ''">
                    AND (f.code LIKE CONCAT('%', #{keyword}, '%')
                         OR f.name LIKE CONCAT('%', #{keyword}, '%')
                         OR f.pinyin LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="watched != null">
                    AND EXISTS(
                        SELECT 1 FROM fund_watchlist w
                        WHERE w.fund_type = f.fund_type AND w.code = f.code
                    ) = #{watched}
                </if>
            </where>
            ORDER BY f.name, f.code
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<FundCatalogItem> selectCatalogPage(@Param("keyword") String keyword, @Param("fundType") String fundType,
                                            @Param("watched") Boolean watched,
                                            @Param("limit") int limit, @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM fund_catalog f
            <where>
                <if test="fundType != null and fundType != ''">
                    f.fund_type = #{fundType}
                </if>
                <if test="keyword != null and keyword != ''">
                    AND (f.code LIKE CONCAT('%', #{keyword}, '%')
                         OR f.name LIKE CONCAT('%', #{keyword}, '%')
                         OR f.pinyin LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="watched != null">
                    AND EXISTS(
                        SELECT 1 FROM fund_watchlist w
                        WHERE w.fund_type = f.fund_type AND w.code = f.code
                    ) = #{watched}
                </if>
            </where>
            </script>
            """)
    long countCatalog(@Param("keyword") String keyword, @Param("fundType") String fundType,
                      @Param("watched") Boolean watched);

    @Select("""
            SELECT f.fund_type AS fundType, f.code, f.name, f.category, f.pinyin,
                   EXISTS(
                       SELECT 1 FROM fund_watchlist w
                       WHERE w.fund_type = f.fund_type AND w.code = f.code
                   ) AS watched,
                   f.synced_at AS syncedAt,
                   p.fund_full_name AS fundFullName,
                   p.issue_date AS issueDate,
                   p.established_date AS establishedDate,
                   p.establishment_scale AS establishmentScale,
                   p.net_asset_scale AS netAssetScale,
                   p.net_asset_scale_as_of AS netAssetScaleAsOf,
                   p.share_scale AS shareScale,
                   p.share_scale_as_of AS shareScaleAsOf,
                   p.management_company AS managementCompany,
                   p.custodian,
                   p.manager_names AS managerNames,
                   p.distribution_since_launch AS distributionSinceLaunch,
                   p.management_fee_rate AS managementFeeRate,
                   p.custodian_fee_rate AS custodianFeeRate,
                   p.service_fee_rate AS serviceFeeRate,
                   p.max_subscription_fee_rate AS maxSubscriptionFeeRate,
                   p.max_purchase_fee_rate AS maxPurchaseFeeRate,
                   p.max_redemption_fee_rate AS maxRedemptionFeeRate,
                   p.benchmark,
                   p.tracking_target AS trackingTarget,
                   p.investment_objective AS investmentObjective,
                   p.investment_philosophy AS investmentPhilosophy,
                   p.investment_scope AS investmentScope,
                   p.investment_strategy AS investmentStrategy,
                   p.dividend_policy AS dividendPolicy,
                   p.risk_return_feature AS riskReturnFeature,
                   p.updated_at AS profileUpdatedAt,
                   w.watchlistFrequencies,
                   w.firstWatchedAt,
                   COALESCE(h.cachedPoints, 0) AS cachedPoints,
                   COALESCE(h.dailyCachedPoints, 0) AS dailyCachedPoints,
                   COALESCE(h.weeklyCachedPoints, 0) AS weeklyCachedPoints,
                   COALESCE(h.monthlyCachedPoints, 0) AS monthlyCachedPoints,
                   h.cachedStartDate,
                   h.latestMarketDate,
                   h.periodHigh,
                   h.periodLow,
                   (SELECT CASE WHEN f.fund_type = 'ONSHORE' THEN c.close ELSE c.unit_nav END
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code
                      AND CASE WHEN f.fund_type = 'ONSHORE' THEN c.close ELSE c.unit_nav END IS NOT NULL
                    ORDER BY c.trade_date ASC, FIELD(c.frequency, 'day', 'week', 'month'), c.id ASC LIMIT 1) AS firstMarketValue,
                   (SELECT c.open
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.open IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestOpen,
                   (SELECT c.close FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.close IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestClose,
                   (SELECT c.high
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.high IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestHigh,
                   (SELECT c.low
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.low IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestLow,
                   (SELECT c.volume
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.volume IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestVolume,
                   (SELECT c.change_percent
                    FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.change_percent IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestChangePercent,
                   (SELECT c.unit_nav FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.unit_nav IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestUnitNav,
                   (SELECT c.cumulative_nav FROM fund_history_cache c
                    WHERE c.fund_type = f.fund_type AND c.code = f.code AND c.cumulative_nav IS NOT NULL
                    ORDER BY c.trade_date DESC, FIELD(c.frequency, 'day', 'week', 'month'), c.id DESC LIMIT 1) AS latestCumulativeNav
            FROM fund_catalog f
            LEFT JOIN fund_profile p ON p.fund_type = f.fund_type AND p.code = f.code
            LEFT JOIN (
                SELECT fund_type, code,
                       GROUP_CONCAT(DISTINCT frequency ORDER BY frequency) AS watchlistFrequencies,
                       MIN(create_time) AS firstWatchedAt
                FROM fund_watchlist
                GROUP BY fund_type, code
            ) w ON w.fund_type = f.fund_type AND w.code = f.code
            LEFT JOIN (
                SELECT fund_type, code,
                       COUNT(*) AS cachedPoints,
                       SUM(frequency = 'day') AS dailyCachedPoints,
                       SUM(frequency = 'week') AS weeklyCachedPoints,
                       SUM(frequency = 'month') AS monthlyCachedPoints,
                       MIN(trade_date) AS cachedStartDate,
                       MAX(trade_date) AS latestMarketDate,
                       MAX(CASE WHEN fund_type = 'ONSHORE' THEN high ELSE unit_nav END) AS periodHigh,
                       MIN(CASE WHEN fund_type = 'ONSHORE' THEN low ELSE unit_nav END) AS periodLow
                FROM fund_history_cache
                GROUP BY fund_type, code
            ) h ON h.fund_type = f.fund_type AND h.code = f.code
            WHERE f.fund_type = #{fundType} AND f.code = #{code}
            """)
    FundCatalogItem selectCatalogItem(@Param("fundType") String fundType, @Param("code") String code);

    @Insert("""
            INSERT INTO fund_profile (
                fund_type, code, fund_full_name, issue_date, established_date, establishment_scale,
                net_asset_scale, net_asset_scale_as_of, share_scale, share_scale_as_of,
                management_company, custodian, manager_names, distribution_since_launch,
                management_fee_rate, custodian_fee_rate, service_fee_rate,
                max_subscription_fee_rate, max_purchase_fee_rate, max_redemption_fee_rate,
                benchmark, tracking_target, investment_objective, investment_philosophy,
                investment_scope, investment_strategy, dividend_policy, risk_return_feature
            ) VALUES (
                #{item.fundType}, #{item.code}, #{item.fundFullName}, #{item.issueDate}, #{item.establishedDate},
                #{item.establishmentScale}, #{item.netAssetScale}, #{item.netAssetScaleAsOf}, #{item.shareScale},
                #{item.shareScaleAsOf}, #{item.managementCompany}, #{item.custodian}, #{item.managerNames},
                #{item.distributionSinceLaunch}, #{item.managementFeeRate}, #{item.custodianFeeRate},
                #{item.serviceFeeRate}, #{item.maxSubscriptionFeeRate}, #{item.maxPurchaseFeeRate},
                #{item.maxRedemptionFeeRate}, #{item.benchmark}, #{item.trackingTarget},
                #{item.investmentObjective}, #{item.investmentPhilosophy}, #{item.investmentScope},
                #{item.investmentStrategy}, #{item.dividendPolicy}, #{item.riskReturnFeature}
            )
            ON DUPLICATE KEY UPDATE
                fund_full_name = VALUES(fund_full_name),
                issue_date = VALUES(issue_date),
                established_date = VALUES(established_date),
                establishment_scale = VALUES(establishment_scale),
                net_asset_scale = VALUES(net_asset_scale),
                net_asset_scale_as_of = VALUES(net_asset_scale_as_of),
                share_scale = VALUES(share_scale),
                share_scale_as_of = VALUES(share_scale_as_of),
                management_company = VALUES(management_company),
                custodian = VALUES(custodian),
                manager_names = VALUES(manager_names),
                distribution_since_launch = VALUES(distribution_since_launch),
                management_fee_rate = VALUES(management_fee_rate),
                custodian_fee_rate = VALUES(custodian_fee_rate),
                service_fee_rate = VALUES(service_fee_rate),
                max_subscription_fee_rate = VALUES(max_subscription_fee_rate),
                max_purchase_fee_rate = VALUES(max_purchase_fee_rate),
                max_redemption_fee_rate = VALUES(max_redemption_fee_rate),
                benchmark = VALUES(benchmark),
                tracking_target = VALUES(tracking_target),
                investment_objective = VALUES(investment_objective),
                investment_philosophy = VALUES(investment_philosophy),
                investment_scope = VALUES(investment_scope),
                investment_strategy = VALUES(investment_strategy),
                dividend_policy = VALUES(dividend_policy),
                risk_return_feature = VALUES(risk_return_feature),
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertFundProfile(@Param("item") FundCatalogItem item);

    @Delete("DELETE FROM fund_catalog")
    int deleteAll();

    @Insert("""
            <script>
            INSERT INTO fund_catalog (fund_type, code, name, category, pinyin, synced_at)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.fundType}, #{item.code}, #{item.name}, #{item.category}, #{item.pinyin}, #{item.syncedAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<FundCatalog> items);
}
