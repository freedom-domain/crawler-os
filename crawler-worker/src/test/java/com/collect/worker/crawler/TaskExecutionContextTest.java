package com.collect.worker.crawler;

import okhttp3.Call;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TaskExecutionContextTest {

    @Test
    void cancel_shouldOnlyCancelCallsOwnedByThatTask() {
        CrawlerEngine.TaskExecutionContext canceledTask = new CrawlerEngine.TaskExecutionContext();
        CrawlerEngine.TaskExecutionContext runningTask = new CrawlerEngine.TaskExecutionContext();
        Call canceledCall = mock(Call.class);
        Call runningCall = mock(Call.class);
        canceledTask.register(canceledCall);
        runningTask.register(runningCall);

        canceledTask.cancel();

        verify(canceledCall).cancel();
        verify(runningCall, never()).cancel();
        assertTrue(canceledTask.isCancelled());
        assertFalse(runningTask.isCancelled());
    }
}
