package com.collect.spider.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.spider.entity.Spider;
import com.collect.spider.mapper.SpiderMapper;
import com.collect.spider.service.SpiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 爬虫定时任务调度器
 * 每分钟检查一次，执行到期的定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderScheduler {

    private final SpiderMapper spiderMapper;
    private final SpiderService spiderService;

    /**
     * 每分钟检查一次定时任务
     */
    @Scheduled(cron = "0 * * * * ?")
    public void checkScheduledTasks() {
        // 查询所有启用的、有调度表达式的爬虫
        List<Spider> spiders = spiderMapper.selectList(
                new LambdaQueryWrapper<Spider>()
                        .eq(Spider::getStatus, 1)
                        .isNotNull(Spider::getSchedule)
                        .ne(Spider::getSchedule, "")
        );

        for (Spider spider : spiders) {
            try {
                if (shouldRunNow(spider.getSchedule())) {
                    log.info("定时任务触发: spider={}, schedule={}", spider.getName(), spider.getSchedule());
                    spiderService.run(spider.getId());
                }
            } catch (Exception e) {
                log.error("定时任务执行失败: spider={}", spider.getName(), e);
            }
        }
    }

    /**
     * 判断当前时间是否匹配 cron 表达式
     * 简化实现：只支持常见的 cron 格式
     */
    private boolean shouldRunNow(String cron) {
        if (cron == null || cron.isBlank()) {
            return false;
        }

        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6) {
            return false;
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int second = now.getSecond();
        int minute = now.getMinute();
        int hour = now.getHour();
        int day = now.getDayOfMonth();
        int month = now.getMonthValue();
        int dayOfWeek = now.getDayOfWeek().getValue() % 7; // 0=Sunday

        // 秒
        if (!matchCronField(parts[0], second, 0, 59)) {
            return false;
        }
        // 分
        if (!matchCronField(parts[1], minute, 0, 59)) {
            return false;
        }
        // 时
        if (!matchCronField(parts[2], hour, 0, 23)) {
            return false;
        }
        // 日
        if (!matchCronField(parts[3], day, 1, 31)) {
            return false;
        }
        // 月
        if (!matchCronField(parts[4], month, 1, 12)) {
            return false;
        }
        // 星期
        if (!matchCronField(parts[5], dayOfWeek, 0, 6)) {
            return false;
        }

        return true;
    }

    /**
     * 匹配 cron 字段
     */
    private boolean matchCronField(String field, int value, int min, int max) {
        if ("*".equals(field) || "?".equals(field)) {
            return true;
        }

        // 处理逗号分隔的多个值
        if (field.contains(",")) {
            String[] values = field.split(",");
            for (String v : values) {
                if (matchSingleValue(v.trim(), value, min, max)) {
                    return true;
                }
            }
            return false;
        }

        // 处理范围 (如 1-5)
        if (field.contains("-")) {
            String[] range = field.split("-");
            if (range.length == 2) {
                try {
                    int start = Integer.parseInt(range[0]);
                    int end = Integer.parseInt(range[1]);
                    return value >= start && value <= end;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // 处理步长 (如 */5, 10/5)
        if (field.contains("/")) {
            String[] stepParts = field.split("/");
            int step = Integer.parseInt(stepParts[1]);
            int start = "*".equals(stepParts[0]) ? min : Integer.parseInt(stepParts[0]);
            return (value - start) % step == 0 && value >= start;
        }

        // 单个值
        return matchSingleValue(field, value, min, max);
    }

    private boolean matchSingleValue(String value, int actual, int min, int max) {
        try {
            int v = Integer.parseInt(value);
            return v == actual;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
