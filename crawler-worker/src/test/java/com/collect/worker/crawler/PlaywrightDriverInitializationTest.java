package com.collect.worker.crawler;

import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlaywrightDriverInitializationTest {

    @Test
    void shouldInitializeDriverWithoutDownloadingBrowsersWhenUsingSystemChromium() {
        Playwright.CreateOptions options = new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));

        try (Playwright playwright = Playwright.create(options)) {
            assertNotNull(playwright.chromium());
        }
    }
}
