package com.collect.user.controller;

import com.collect.common.result.R;
import com.collect.user.dto.InvestmentAccountResponse;
import com.collect.user.dto.InvestmentQuote;
import com.collect.user.dto.InvestmentTradeReq;
import com.collect.user.dto.InvestmentTradeResponse;
import com.collect.user.dto.FundDcaRequest;
import com.collect.user.dto.FundDcaResponse;
import com.collect.user.dto.FundHistoryResponse;
import com.collect.user.dto.OffExchangeFundDcaRequest;
import com.collect.user.dto.OffExchangeFundDcaResponse;
import com.collect.user.dto.OffExchangeFundHistoryResponse;
import com.collect.user.service.FundInvestmentBacktestService;
import com.collect.user.service.OffExchangeFundBacktestService;
import com.collect.user.service.PaperInvestmentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final FundInvestmentBacktestService fundInvestmentBacktestService;
    private final OffExchangeFundBacktestService offExchangeFundBacktestService;

    public PaperInvestmentController(PaperInvestmentService investmentService,
                                     FundInvestmentBacktestService fundInvestmentBacktestService,
                                     OffExchangeFundBacktestService offExchangeFundBacktestService) {
        this.investmentService = investmentService;
        this.fundInvestmentBacktestService = fundInvestmentBacktestService;
        this.offExchangeFundBacktestService = offExchangeFundBacktestService;
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
}
