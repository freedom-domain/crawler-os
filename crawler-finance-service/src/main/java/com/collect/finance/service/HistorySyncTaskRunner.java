package com.collect.finance.service;

import com.collect.finance.dto.FullHistorySyncResult;
import com.collect.finance.entity.HistorySyncTask;
import com.collect.finance.entity.HistorySyncTaskItem;
import com.collect.finance.mapper.HistorySyncTaskItemMapper;
import com.collect.finance.mapper.HistorySyncTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class HistorySyncTaskRunner {

    private final HistorySyncTaskMapper taskMapper;
    private final HistorySyncTaskItemMapper itemMapper;
    private final FundManagementService fundManagementService;
    private final StockInvestmentBacktestService stockInvestmentBacktestService;

    public HistorySyncTaskRunner(HistorySyncTaskMapper taskMapper,
                                 HistorySyncTaskItemMapper itemMapper,
                                 FundManagementService fundManagementService,
                                 StockInvestmentBacktestService stockInvestmentBacktestService) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.fundManagementService = fundManagementService;
        this.stockInvestmentBacktestService = stockInvestmentBacktestService;
    }

    @Async("historySyncExecutor")
    public void executeAsync(long taskId) {
        if (taskMapper.start(taskId) != 1) {
            return;
        }
        HistorySyncTask task = taskMapper.selectById(taskId);
        long lastItemId = 0;
        List<HistorySyncTaskItem> items;
        do {
            items = itemMapper.selectNextPendingBatch(taskId, lastItemId, 100);
            for (HistorySyncTaskItem item : items) {
                lastItemId = item.getId();
                if (itemMapper.claimForProcessing(taskId, item.getId()) != 1) {
                    finishCancellation(taskId);
                    return;
                }
                try {
                    String message;
                    if ("FUND".equals(task.getInstrumentType())) {
                        FullHistorySyncResult result = fundManagementService.syncAllHistory(
                                item.getFundType(), item.getCode());
                        item.setPointCount(result.pointCount());
                        message = result.message();
                        if (!result.successful()) {
                            item.setStatus("FAILED");
                            item.setMessage(limitMessage(message));
                            item.setFinishedAt(LocalDateTime.now());
                            itemMapper.updateById(item);
                            taskMapper.recordItemCompletion(taskId, 0, 1);
                            continue;
                        }
                    } else {
                        int points = stockInvestmentBacktestService.syncAllHistory(item.getCode());
                        item.setPointCount(points);
                        message = points + " 个日线数据点";
                    }
                    item.setStatus("SUCCESS");
                    item.setMessage(limitMessage(message));
                    item.setFinishedAt(LocalDateTime.now());
                    itemMapper.updateById(item);
                    taskMapper.recordItemCompletion(taskId, 1, 0);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null ? "同步失败" : exception.getMessage();
                    item.setStatus("FAILED");
                    item.setMessage(message.length() > 2000 ? message.substring(0, 2000) : message);
                    item.setFinishedAt(LocalDateTime.now());
                    itemMapper.updateById(item);
                    taskMapper.recordItemCompletion(taskId, 0, 1);
                    log.error("Failed to synchronize complete history: taskId={}, type={}, code={}",
                            taskId, task.getInstrumentType(), item.getCode(), exception);
                }
            }
        } while (!items.isEmpty());
        finishCancellation(taskId);
    }

    private void finishCancellation(long taskId) {
        itemMapper.cancelPendingItems(taskId);
        taskMapper.finishCancellation(taskId);
    }

    private String limitMessage(String message) {
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
