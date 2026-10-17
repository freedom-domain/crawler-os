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
import com.collect.finance.dto.FundWatchlistRequest;
import com.collect.finance.dto.OffExchangeFundDcaRequest;
import com.collect.finance.dto.OffExchangeFundDcaResponse;
import com.collect.finance.dto.OffExchangeFundHistoryResponse;
import com.collect.finance.service.FundCatalogService;
import com.collect.finance.service.FundInvestmentBacktestService;
import com.collect.finance.service.FundManagementService;
import com.collect.finance.service.OffExchangeFundBacktestService;
import com.collect.finance.service.PaperInvestmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public PaperInvestmentController(PaperInvestmentService investmentService,
                                     FundCatalogService fundCatalogService,
                                     FundInvestmentBacktestService fundInvestmentBacktestService,
                                     OffExchangeFundBacktestService offExchangeFundBacktestService,
                                     FundManagementService fundManagementService) {
        this.investmentService = investmentService;
        this.fundCatalogService = fundCatalogService;
        this.fundInvestmentBacktestService = fundInvestmentBacktestService;
        this.offExchangeFundBacktestService = offExchangeFundBacktestService;
        this.fundManagementService = fundManagementService;
    }

    @GetMapping("/account")
    public R<InvestmentAccountResponse> account() {
        return R.ok(investmentService.getAccount());
    }

    @GetMapping("/quote")
    public R<InvestmentQuote> quote(@RequestParam String code) {
        return R.ok(investmentService.getQuote(code));
    }

    @PostMapping("/trade")
    public R<InvestmentTradeResponse> trade(@Valid @RequestBody InvestmentTradeReq request) {
        return R.ok(investmentService.trade(request));
    }

    @GetMapping("/trades")
    public R<List<InvestmentTradeResponse>> trades() {
        return R.ok(investmentService.recentTrades());
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

    @GetMapping("/fund-management/catalog")
    public R<FundCatalogPage> fundCatalog(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String fundType,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(fundCatalogService.page(keyword, fundType, page, pageSize));
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

    @PostMapping("/fund-management/watchlist")
    public R<Void> addFundToWatchlist(@Valid @RequestBody FundWatchlistRequest request) {
        fundManagementService.addToWatchlist(request);
        return R.ok();
    }

    @DeleteMapping("/fund-management/watchlist")
    public R<Void> removeFundFromWatchlist(@RequestParam String fundType, @RequestParam String code,
                                           @RequestParam String frequency) {
        fundManagementService.removeFromWatchlist(fundType, code, frequency);
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
