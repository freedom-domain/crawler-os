package com.collect.spider.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.spider.entity.Spider;
import com.collect.spider.mapper.SpiderMapper;
import com.collect.spider.service.SpiderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 爬虫定时任务调度器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpiderScheduler {

    private static final ZoneId SCHEDULER_ZONE = ZoneId.of("Asia/Shanghai");

    private final SpiderMapper spiderMapper;
    private final SpiderService spiderService;
    private final ConcurrentMap<Long, String> invalidSchedules = new ConcurrentHashMap<>();

    private ZonedDateTime lastCheck;

    /**
     * 高频轮询并检查整个间隔，避免调度线程延迟时错过触发时间。
     */
    @Scheduled(fixedDelayString = "${app.spider.schedule-check-delay-ms:1000}")
    public void checkScheduledTasks() {
        ZonedDateTime now = ZonedDateTime.now(SCHEDULER_ZONE).withNano(0);
        ZonedDateTime previous = lastCheck;
        lastCheck = now;
        if (previous == null) {
            previous = now.minusSeconds(1);
        }

        List<Spider> spiders = spiderMapper.selectList(
                new LambdaQueryWrapper<Spider>()
                        .eq(Spider::getStatus, 1)
                        .isNotNull(Spider::getSchedule)
                        .ne(Spider::getSchedule, "")
        );

        for (Spider spider : spiders) {
            String schedule = spider.getSchedule();
            if (schedule == null || schedule.isBlank()) {
                invalidSchedules.remove(spider.getId());
                continue;
            }

            CronExpression cron;
            try {
                cron = CronExpression.parse(schedule.trim());
            } catch (IllegalArgumentException e) {
                if (!schedule.equals(invalidSchedules.put(spider.getId(), schedule))) {
                    log.error("爬虫定时表达式无效: spider={}, schedule={}", spider.getName(), schedule, e);
                }
                continue;
            }
            invalidSchedules.remove(spider.getId());

            try {
                if (isDue(cron, previous, now)) {
                    log.info("定时任务触发: spider={}, schedule={}", spider.getName(), schedule);
                    spiderService.run(spider.getId());
                }
            } catch (Exception e) {
                log.error("定时任务执行失败: spider={}", spider.getName(), e);
            }
        }
    }

    static boolean isDue(CronExpression cron, ZonedDateTime previous, ZonedDateTime now) {
        ZonedDateTime next = cron.next(previous);
        return next != null && !next.isAfter(now);
    }
}
