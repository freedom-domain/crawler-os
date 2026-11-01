package com.collect.worker.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
final class PlaywrightHtmlRenderer {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) CollectX/1.0";
    private static final long MAX_SELECTOR_WAIT_MILLIS = 5_000;
    private static final long FALLBACK_RENDER_WAIT_MILLIS = 1_500;

    private PlaywrightHtmlRenderer() {}

    static String render(String url, String imageSelector, String configuredHeaders,
                         boolean skipTlsVerify, Integer configuredTimeout) {
        long timeoutMillis = configuredTimeout == null || configuredTimeout <= 0
                ? 15_000 : configuredTimeout;
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "text/html,application/xhtml+xml");
        for (Map.Entry<String, String> header : CrawlerEngine.parseConfiguredHeaders(configuredHeaders)) {
            headers.put(header.getKey(), header.getValue());
        }

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(java.util.List.of("--no-sandbox", "--disable-dev-shm-usage"));
        String executablePath = System.getenv("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
        if (executablePath != null && !executablePath.isBlank()) {
            launchOptions.setExecutablePath(Path.of(executablePath));
        }

        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(launchOptions);
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setIgnoreHTTPSErrors(skipTlsVerify)
                     .setExtraHTTPHeaders(headers));
             Page page = context.newPage()) {
            page.setDefaultNavigationTimeout(timeoutMillis);
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(timeoutMillis));
            waitForRenderedContent(page, imageSelector, timeoutMillis);
            return page.content();
        }
    }

    private static void waitForRenderedContent(Page page, String imageSelector, long timeoutMillis) {
        if (imageSelector != null && !imageSelector.isBlank()) {
            try {
                page.waitForSelector(imageSelector, new Page.WaitForSelectorOptions()
                        .setTimeout(Math.min(timeoutMillis, MAX_SELECTOR_WAIT_MILLIS)));
                page.waitForTimeout(250);
            } catch (TimeoutError timeout) {
                log.warn("JS 渲染等待图片选择器超时，继续解析当前 DOM: selector={}", imageSelector);
            }
            return;
        }

        try {
            page.waitForLoadState(LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions()
                            .setTimeout(FALLBACK_RENDER_WAIT_MILLIS));
        } catch (TimeoutError timeout) {
            log.debug("页面未进入 network-idle，继续解析当前 DOM: url={}", page.url());
        }
    }
}
