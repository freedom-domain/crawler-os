package com.collect.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.collect.finance.dto.StockCatalogItem;
import com.collect.finance.entity.StockCatalog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface StockCatalogMapper extends BaseMapper<StockCatalog> {

    @Select("""
            <script>
            SELECT s.code, s.name, s.market, s.industry, s.synced_at AS syncedAt,
                   EXISTS(SELECT 1 FROM stock_watchlist w WHERE w.code = s.code) AS watched
            FROM stock_catalog s
            <where>
                <if test="keyword != null and keyword != ''">
                    (s.code LIKE CONCAT('%', #{keyword}, '%')
                     OR s.name LIKE CONCAT('%', #{keyword}, '%')
                     OR s.industry LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="market != null and market != ''">
                    AND s.market = #{market}
                </if>
                <if test="watched != null and watched">
                    AND EXISTS(SELECT 1 FROM stock_watchlist w WHERE w.code = s.code)
                </if>
                <if test="watched != null and !watched">
                    AND NOT EXISTS(SELECT 1 FROM stock_watchlist w WHERE w.code = s.code)
                </if>
            </where>
            ORDER BY s.code
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<StockCatalogItem> selectCatalogPage(@Param("keyword") String keyword, @Param("market") String market,
                                             @Param("watched") Boolean watched, @Param("limit") int limit,
                                             @Param("offset") long offset);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM stock_catalog
            <where>
                <if test="keyword != null and keyword != ''">
                    (code LIKE CONCAT('%', #{keyword}, '%')
                     OR name LIKE CONCAT('%', #{keyword}, '%')
                     OR industry LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="market != null and market != ''">
                    AND market = #{market}
                </if>
                <if test="watched != null and watched">
                    AND EXISTS(SELECT 1 FROM stock_watchlist w WHERE w.code = stock_catalog.code)
                </if>
                <if test="watched != null and !watched">
                    AND NOT EXISTS(SELECT 1 FROM stock_watchlist w WHERE w.code = stock_catalog.code)
                </if>
            </where>
            </script>
            """)
    long countCatalog(@Param("keyword") String keyword, @Param("market") String market,
                      @Param("watched") Boolean watched);

    @Select("""
            SELECT s.code, s.name, s.market, s.industry, s.synced_at AS syncedAt,
                   p.company_name AS companyName,
                   p.former_names AS formerNames,
                   p.legal_representative AS legalRepresentative,
                   p.registered_capital AS registeredCapital,
                   p.established_date AS establishedDate,
                   p.province,
                   p.employee_count AS employeeCount,
                   p.main_business AS mainBusiness,
                   p.business_scope AS businessScope,
                   p.company_profile AS companyProfile,
                   p.registered_address AS registeredAddress,
                   p.website, p.email, p.phone,
                   p.updated_at AS profileUpdatedAt
            FROM stock_catalog s
            LEFT JOIN stock_profile p ON p.code = s.code
            WHERE s.code = #{code}
            """)
    StockCatalogItem selectCatalogItem(@Param("code") String code);

    @Insert("""
            INSERT INTO stock_profile (
                code, company_name, former_names, legal_representative, registered_capital,
                established_date, province, employee_count, main_business, business_scope,
                company_profile, registered_address, website, email, phone
            ) VALUES (
                #{item.code}, #{item.companyName}, #{item.formerNames}, #{item.legalRepresentative},
                #{item.registeredCapital}, #{item.establishedDate}, #{item.province}, #{item.employeeCount},
                #{item.mainBusiness}, #{item.businessScope}, #{item.companyProfile}, #{item.registeredAddress},
                #{item.website}, #{item.email}, #{item.phone}
            )
            ON DUPLICATE KEY UPDATE
                company_name = VALUES(company_name),
                former_names = VALUES(former_names),
                legal_representative = VALUES(legal_representative),
                registered_capital = VALUES(registered_capital),
                established_date = VALUES(established_date),
                province = VALUES(province),
                employee_count = VALUES(employee_count),
                main_business = VALUES(main_business),
                business_scope = VALUES(business_scope),
                company_profile = VALUES(company_profile),
                registered_address = VALUES(registered_address),
                website = VALUES(website),
                email = VALUES(email),
                phone = VALUES(phone),
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertStockProfile(@Param("item") StockCatalogItem item);

    @Delete("DELETE FROM stock_catalog")
    int deleteAll();

    @Insert("""
            <script>
            INSERT INTO stock_catalog (code, name, market, industry, synced_at)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.code}, #{item.name}, #{item.market}, #{item.industry}, #{item.syncedAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<StockCatalog> items);
}
