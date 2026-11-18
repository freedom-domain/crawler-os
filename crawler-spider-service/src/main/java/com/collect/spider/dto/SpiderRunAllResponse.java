package com.collect.spider.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一键运行所有已开启定时任务的结果
 */
@Data
public class SpiderRunAllResponse {
    /** 已开启的定时任务总数（status=1 且已配置调度表达式） */
    private int total;
    /** 成功触发的任务数 */
    private int runCount;
    /** 跳过的任务及原因 */
    private List<SkippedTask> skipped = new ArrayList<>();

    @Data
    public static class SkippedTask {
        private Long spiderId;
        private String spiderName;
        private String reason;
    }
}
