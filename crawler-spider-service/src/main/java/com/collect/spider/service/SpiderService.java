package com.collect.spider.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.common.exception.BizException;
import com.collect.common.mq.MqConstants;
import com.collect.common.mq.TaskMessage;
import com.collect.spider.dto.SpiderCreateReq;
import com.collect.spider.dto.SpiderUpdateReq;
import com.collect.spider.entity.Spider;
import com.collect.spider.entity.SpiderTask;
import com.collect.spider.entity.SpiderTaskLog;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpiderService {

    private final com.collect.spider.mapper.SpiderMapper spiderMapper;
    private final com.collect.spider.mapper.SpiderTaskMapper taskMapper;
    private final com.collect.spider.mapper.SpiderTaskLogMapper logMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public Spider create(SpiderCreateReq req) {
        Spider exist = spiderMapper.selectOne(
                new LambdaQueryWrapper<Spider>().eq(Spider::getName, req.getName()));
        if (exist != null) {
            throw new BizException("爬虫名称已存在");
        }
        Spider spider = new Spider();
        spider.setName(req.getName());
        spider.setDescription(req.getDescription());
        spider.setType(req.getType());
        spider.setStartUrls(JSON.toJSONString(req.getStartUrls()));
        spider.setSelectors(req.getSelectors());
        spider.setSchedule(req.getSchedule());
        spider.setMaxDepth(req.getMaxDepth());
        spider.setTimeout(req.getTimeout());
        spider.setHeaders(req.getHeaders());
        spider.setEnabled(req.getEnabled());
        spider.setCreatorId(1L);
        spider.setStatus(0);
        spiderMapper.insert(spider);
        return spider;
    }

    public IPage<Spider> page(int current, int size, String keyword) {
        LambdaQueryWrapper<Spider> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Spider::getName, keyword);
        }
        qw.orderByDesc(Spider::getCreateTime);
        return spiderMapper.selectPage(new Page<>(current, size), qw);
    }

    public Spider getById(Long id) {
        return spiderMapper.selectById(id);
    }

    public void update(Long id, SpiderUpdateReq req) {
        Spider exist = spiderMapper.selectById(id);
        if (exist == null) {
            throw new BizException("爬虫不存在");
        }
        Spider other = spiderMapper.selectOne(
                new LambdaQueryWrapper<Spider>().eq(Spider::getName, req.getName()).ne(Spider::getId, id));
        if (other != null) {
            throw new BizException("爬虫名称已存在");
        }
        exist.setName(req.getName());
        exist.setDescription(req.getDescription());
        exist.setType(req.getType());
        exist.setStartUrls(JSON.toJSONString(req.getStartUrls()));
        exist.setSelectors(req.getSelectors());
        exist.setSchedule(req.getSchedule());
        exist.setMaxDepth(req.getMaxDepth());
        exist.setTimeout(req.getTimeout());
        exist.setHeaders(req.getHeaders());
        spiderMapper.updateById(exist);
    }

    public void delete(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spiderMapper.deleteById(id);
    }

    public void start(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spider.setStatus(1);
        spider.setEnabled(1);
        spiderMapper.updateById(spider);
    }

    public void stop(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        spider.setStatus(0);
        spiderMapper.updateById(spider);
    }

    public SpiderTask run(Long id) {
        Spider spider = spiderMapper.selectById(id);
        if (spider == null) {
            throw new BizException("爬虫不存在");
        }
        Long taskId = snowflakeId();
        SpiderTask task = new SpiderTask();
        task.setSpiderId(id);
        task.setSpiderName(spider.getName());
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        task.setSuccessCount(0);
        task.setFailCount(0);
        task.setTaskId(taskId);
        taskMapper.insert(task);

        TaskMessage msg = buildMessage(spider, taskId);
        String payload = JSON.toJSONString(msg);
        kafkaTemplate.send(MqConstants.SPIDER_TASK_TOPIC, payload);
        log.info("已派发爬虫任务: taskId={}, spider={}", taskId, spider.getName());
        return task;
    }

    private Long snowflakeId() {
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 999);
    }

    public IPage<SpiderTask> taskPage(int current, int size, Long spiderId, String status) {
        LambdaQueryWrapper<SpiderTask> qw = new LambdaQueryWrapper<>();
        if (spiderId != null) {
            qw.eq(SpiderTask::getSpiderId, spiderId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(SpiderTask::getStatus, status);
        }
        qw.orderByDesc(SpiderTask::getCreateTime);
        return taskMapper.selectPage(new Page<>(current, size), qw);
    }

    public SpiderTask taskDetail(Long id) {
        return taskMapper.selectById(id);
    }

    public IPage<SpiderTaskLog> taskLogPage(Long taskId, int current, int size) {
        LambdaQueryWrapper<SpiderTaskLog> qw = new LambdaQueryWrapper<>();
        qw.eq(SpiderTaskLog::getTaskId, taskId);
        qw.orderByAsc(SpiderTaskLog::getCreateTime);
        return logMapper.selectPage(new Page<>(current, size), qw);
    }

    public void cancelTask(Long id) {
        SpiderTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        if ("RUNNING".equals(task.getStatus())) {
            task.setStatus("CANCELED");
            task.setEndTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private TaskMessage buildMessage(Spider spider, Long taskId) {
        TaskMessage msg = new TaskMessage();
        msg.setTaskId(taskId);
        msg.setSpiderId(spider.getId());
        msg.setSpiderName(spider.getName());
        msg.setType(spider.getType());
        msg.setStartUrls(JSON.parseArray(spider.getStartUrls(), String.class));
        msg.setSelectors(spider.getSelectors());
        msg.setMaxDepth(spider.getMaxDepth());
        msg.setTimeout(spider.getTimeout());
        msg.setHeaders(spider.getHeaders());
        return msg;
    }
}
