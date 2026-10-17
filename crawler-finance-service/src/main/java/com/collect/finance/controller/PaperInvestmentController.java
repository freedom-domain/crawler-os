package com.collect.finance.controller;

import com.collect.common.result.R;
import com.collect.finance.dto.InvestmentAccountResponse;
import com.collect.finance.dto.InvestmentQuote;
import com.collect.finance.dto.InvestmentTradeReq;
import com.collect.finance.dto.InvestmentTradeResponse;
import com.collect.finance.dto.FundCatalogItem;
import com.collect.finance.dto.FundCatalogPage;
import com.collect.finance.dto.FundCatalogSyncResult;
import com.collect.finance.dto.FundDcaRequest;
import com.collect.finance.dto.FundDcaResponse;
import com.collect.finance.dto.FundHistoryResponse;
import com.collect.finance.dto.FundManagementItem;
import com.collect.finance.dto.FundRefreshSchedule;
import com.collect.finance.dto.FundWatchlistRequest;
import com.collect.finance.dto.OffExchangeFundDcaRequest;
import com.collect.finance.dto.OffExchangeFundDcaResponse;
import com.collect.finance.dto.OffExchangeFundHistoryResponse;
import com.collect.finance.dto.StockCatalogItem;
import com.collect.finance.dto.StockCatalogPage;
import com.collect.finance.dto.StockCatalogSyncResult;
import com.collect.finance.dto.StockBacktestRequest;
import com.collect.finance.dto.StockBacktestResponse;
import com.collect.finance.dto.StockHistoryResponse;
import com.collect.finance.dto.StockWatchlistItem;
import com.collect.finance.service.FundCatalogService;
import com.collect.finance.service.FundInvestmentBacktestService;
import com.collect.finance.service.FundManagementService;
import com.collect.finance.service.FundRefreshSettingService;
import com.collect.finance.service.OffExchangeFundBacktestService;
import com.collect.finance.service.PaperInvestmentService;
import com.collect.finance.service.StockCatalogService;
import com.collect.finance.service.StockRefreshSettingService;
import com.collect.finance.service.StockInvestmentBacktestService;
import com.collect.finance.service.StockWatchlistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/investment")
public class PaperInvestmentController {

    private final PaperInvestmentService investmentService;
    private final FundCatalogService fundCatalogService;
    private final FundInvestmentBacktestService fundInvestmentBacktestService;
    private final OffExchangeFundBacktestService offExchangeFundBacktestService;
    private final FundManagementService fundManagementService;
    private final FundRefreshSettingService fundRefreshSettingService;
    private final StockCatalogService stockCatalogService;
    private final StockInvestmentBacktestService stockInvestmentBacktestService;
    private final StockWatchlistService stockWatchlistService;
    private final StockRefreshSettingService stockRefreshSettingService;

    public PaperInvestmentController(PaperInvestmentService investmentService,
                                     FundCatalogService fundCatalogService,
                                     FundInvestmentBacktestService fundInvestmentBacktestService,
                                     OffExchangeFundBacktestService offExchangeFundBacktestService,
                                     FundManagementService fundManagementService,
                                     FundRefreshSettingService fundRefreshSettingService,
                                     StockCatalogService stockCatalogService,
                                     StockInvestmentBacktestService stockInvestmentBacktestService,
                                     StockWatchlistService stockWatchlistService,
                                     StockRefreshSettingService stockRefreshSettingService) {
        this.investmentService = investmentService;
        this.fundCatalogService = fundCatalogService;
        this.fundInvestmentBacktestService = fundInvestmentBacktestService;
        this.offExchangeFundBacktestService = offExchangeFundBacktestService;
        this.fundManagementService = fundManagementService;
        this.fundRefreshSettingService = fundRefreshSettingService;
        this.stockCatalogService = stockCatalogService;
        this.stockInvestmentBacktestService = stockInvestmentBacktestService;
        this.stockWatchlistService = stockWatchlistService;
        this.stockRefreshSettingService = stockRefreshSettingService;
    }

    @GetMapping("/account")
    public R<InvestmentAccountResponse> account() {
        return R.ok(investmentService.getAccount());
    }

    @GetMapping("/quote")
    public R<InvestmentQuote> quote(@RequestParam String code) {
        return R.ok(investmentService.getQuote(code));
    }

    @GetMapping("/stock/history")
    public R<StockHistoryResponse> stockHistory(@RequestParam String code,
                                                @RequestParam String startDate,
                                                @RequestParam String endDate,
                                                @RequestParam(defaultValue = "api") String source) {
        return R.ok(stockInvestmentBacktestService.history(code, startDate, endDate, source));
    }

    @PostMapping("/stock/backtest")
    public R<StockBacktestResponse> simulateStockBacktest(@Valid @RequestBody StockBacktestRequest request) {
        return R.ok(stockInvestmentBacktestService.simulate(request));
    }

    @PostMapping("/trade")
    public R<InvestmentTradeResponse> trade(@Valid @RequestBody InvestmentTradeReq request) {
        return R.ok(investmentService.trade(request));
    }

    @GetMapping("/trades")
    public R<List<InvestmentTradeResponse>> trades(@RequestParam(required = false) String code) {
        return R.ok(investmentService.recentTrades(code));
    }

    @GetMapping("/fund/history")
    public R<FundHistoryResponse> fundHistory(@RequestParam String code,
                                              @RequestParam String startDate,
                                              @RequestParam String endDate,
                                              @RequestParam(defaultValue = "day") String frequency,
                                              @RequestParam(defaultValue = "api") String source) {
        return R.ok(fundInvestmentBacktestService.history(code, startDate, endDate, frequency, source));
    }

    @PostMapping("/fund/dca")
    public R<FundDcaResponse> simulateFundDca(@Valid @RequestBody FundDcaRequest request) {
        return R.ok(fundInvestmentBacktestService.simulate(request));
    }

    @GetMapping("/off-exchange-fund/history")
    public R<OffExchangeFundHistoryResponse> offExchangeFundHistory(@RequestParam String code,
                                                                    @RequestParam String startDate,
                                                                    @RequestParam String endDate,
                                                                    @RequestParam(defaultValue = "api") String source) {
        return R.ok(offExchangeFundBacktestService.history(code, startDate, endDate, source));
    }

    @PostMapping("/off-exchange-fund/dca")
    public R<OffExchangeFundDcaResponse> simulateOffExchangeFundDca(
            @Valid @RequestBody OffExchangeFundDcaRequest request) {
        return R.ok(offExchangeFundBacktestService.simulate(request));
    }

    @GetMapping("/fund-management")
    public R<List<FundManagementItem>> fundManagement() {
        return R.ok(fundManagementService.list());
    }

    @GetMapping("/fund-management/refresh-schedule")
    public R<FundRefreshSchedule> fundRefreshSchedule() {
        return R.ok(fundRefreshSettingService.getSchedule());
    }

    @PutMapping("/fund-management/refresh-schedule")
    public R<FundRefreshSchedule> updateFundRefreshSchedule(@RequestBody FundRefreshSchedule schedule) {
        return R.ok(fundRefreshSettingService.updateSchedule(schedule));
    }

    @GetMapping("/fund-management/catalog")
    public R<FundCatalogPage> fundCatalog(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String fundType,
                                          @RequestParam(required = false) Boolean watched,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(fundCatalogService.page(keyword, fundType, watched, page, pageSize));
    }

    @GetMapping("/fund-management/catalog/detail")
    public R<FundCatalogItem> fundCatalogDetail(@RequestParam String fundType, @RequestParam String code) {
        return R.ok(fundCatalogService.detail(fundType, code));
    }

    @PostMapping("/fund-management/catalog/profile/refresh")
    public R<FundCatalogItem> refreshFundProfile(@RequestParam String fundType, @RequestParam String code) {
        return R.ok(fundCatalogService.refreshProfile(fundType, code));
    }

    @PostMapping("/fund-management/catalog/sync")
    public R<FundCatalogSyncResult> syncFundCatalog() {
        return R.ok(fundCatalogService.sync());
    }

    @GetMapping("/stock-management/catalog")
    public R<StockCatalogPage> stockCatalog(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String market,
                                            @RequestParam(required = false) Boolean watched,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(stockCatalogService.page(keyword, market, watched, page, pageSize));
    }

    @GetMapping("/stock-management/catalog/detail")
    public R<StockCatalogItem> stockCatalogDetail(@RequestParam String code) {
        return R.ok(stockCatalogService.detail(code));
    }

    @PostMapping("/stock-management/catalog/profile/refresh")
    public R<StockCatalogItem> refreshStockProfile(@RequestParam String code) {
        return R.ok(stockCatalogService.refreshProfile(code));
    }

    @PostMapping("/stock-management/catalog/sync")
    public R<StockCatalogSyncResult> syncStockCatalog() {
        return R.ok(stockCatalogService.sync());
    }

    @GetMapping("/stock-management/watchlist")
    public R<List<StockWatchlistItem>> stockWatchlist() {
        return R.ok(stockWatchlistService.list());
    }

    @GetMapping("/stock-management/refresh-schedule")
    public R<FundRefreshSchedule> stockRefreshSchedule() {
        return R.ok(stockRefreshSettingService.getSchedule());
    }

    @PutMapping("/stock-management/refresh-schedule")
    public R<FundRefreshSchedule> updateStockRefreshSchedule(@RequestBody FundRefreshSchedule schedule) {
        return R.ok(stockRefreshSettingService.updateSchedule(schedule));
    }

    @PostMapping("/stock-management/watchlist")
    public R<Void> addStockToWatchlist(@RequestParam String code) {
        stockWatchlistService.add(code);
        return R.ok();
    }

    @DeleteMapping("/stock-management/watchlist")
    public R<Void> removeStockFromWatchlist(@RequestParam String code) {
        stockWatchlistService.remove(code);
        return R.ok();
    }

    @PostMapping("/stock-management/watchlist/refresh")
    public R<StockHistoryResponse> refreshWatchedStock(@RequestParam String code) {
        return R.ok(stockWatchlistService.refresh(code));
    }

    @PostMapping("/fund-management/watchlist")
    public R<Void> addFundToWatchlist(@Valid @RequestBody FundWatchlistRequest request) {
        fundManagementService.addToWatchlist(request);
        return R.ok();
    }

    @DeleteMapping("/fund-management/watchlist")
    public R<Void> removeFundFromWatchlist(@RequestParam String fundType, @RequestParam String code,
                                           @RequestParam(required = false) String frequency) {
        if (frequency == null || frequency.isBlank()) {
            fundManagementService.removeAllFromWatchlist(fundType, code);
        } else {
            fundManagementService.removeFromWatchlist(fundType, code, frequency);
        }
        return R.ok();
    }

    @PostMapping("/fund-management/refresh")
    public R<Void> refreshFundHistory(@RequestParam String fundType, @RequestParam String code,
                                      @RequestParam String frequency) {
        fundManagementService.refresh(fundType, code, frequency);
        return R.ok();
    }

    @DeleteMapping("/fund-management/cache")
    public R<Void> clearFundHistoryCache(@RequestParam String fundType, @RequestParam String code,
                                         @RequestParam String frequency) {
        fundManagementService.clearCache(fundType, code, frequency);
        return R.ok();
    }
}
