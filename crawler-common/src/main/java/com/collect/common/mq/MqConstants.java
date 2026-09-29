package com.collect.common.mq;

public final class MqConstants {

    /** 默认环境标识，与 application.yml（非 local profile）中的 topic 后缀一致 */
    public static final String DEFAULT_ENV = "default";

    public static final String SPIDER_TASK_TOPIC = "spider_task_topic";

    public static final String RESULT_TOPIC = "spider_result_topic";

    /** 任务日志 topic：worker 产生日志先发到该 topic，再由消费端批量入库 */
    public static final String TASK_LOG_TOPIC = "spider_task_log_topic";

    private MqConstants() {
    }

    /**
     * 按环境拼接 topic 名称，保证各环境（default/local/...）topic 隔离。
     * 例：topicWithEnv(TASK_LOG_TOPIC, "local") -> spider_task_log_topic-local
     */
    public static String topicWithEnv(String baseTopic, String env) {
        return baseTopic + "-" + (env == null || env.isBlank() ? DEFAULT_ENV : env);
    }
}
