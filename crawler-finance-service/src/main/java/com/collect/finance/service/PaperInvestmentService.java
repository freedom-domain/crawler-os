package com.collect.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.common.security.LoginUtils;
import com.collect.finance.dto.InvestmentAccountResponse;
import com.collect.finance.dto.InvestmentPositionResponse;
import com.collect.finance.dto.InvestmentQuote;
import com.collect.finance.dto.InvestmentTradeReq;
import com.collect.finance.dto.InvestmentTradeResponse;
import com.collect.finance.entity.PaperInvestmentAccount;
import com.collect.finance.entity.PaperInvestmentHolding;
import com.collect.finance.entity.PaperInvestmentTrade;
import com.collect.finance.mapper.PaperInvestmentAccountMapper;
import com.collect.finance.mapper.PaperInvestmentHoldingMapper;
import com.collect.finance.mapper.PaperInvestmentTradeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PaperInvestmentService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.0003");
    private static final BigDecimal MIN_COMMISSION = new BigDecimal("5.00");
    private static final BigDecimal STOCK_STAMP_TAX_RATE = new BigDecimal("0.0005");
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final PaperInvestmentAccountMapper accountMapper;
    private final PaperInvestmentHoldingMapper holdingMapper;
    private final PaperInvestmentTradeMapper tradeMapper;
    private final TencentQuoteClient quoteClient;

    @Value("${investment.initial-cash:100000.00}")
    private BigDecimal initialCash;

    public PaperInvestmentService(PaperInvestmentAccountMapper accountMapper,
                                  PaperInvestmentHoldingMapper holdingMapper,
                                  PaperInvestmentTradeMapper tradeMapper,
                                  TencentQuoteClient quoteClient) {
        this.accountMapper = accountMapper;
        this.holdingMapper = holdingMapper;
        this.tradeMapper = tradeMapper;
        this.quoteClient = quoteClient;
    }

    public InvestmentAccountResponse getAccount() {
        Long userId = LoginUtils.getUserId();
        PaperInvestmentAccount account = loadOrCreateAccount(userId);
        List<PaperInvestmentHolding> holdings = holdingMapper.selectList(
                new LambdaQueryWrapper<PaperInvestmentHolding>()
                        .eq(PaperInvestmentHolding::getUserId, userId)
                        .eq(PaperInvestmentHolding::getDeleted, 0)
                        .gt(PaperInvestmentHolding::getQuantity, 0)
                        .orderByAsc(PaperInvestmentHolding::getCode));
        Map<String, InvestmentQuote> quotes = quoteClient.getQuotes(
                holdings.stream().map(PaperInvestmentHolding::getCode).toList());

        List<InvestmentPositionResponse> positions = new ArrayList<>();
        BigDecimal marketValue = BigDecimal.ZERO;
        LocalDate today = LocalDate.now(MARKET_ZONE);
        for (PaperInvestmentHolding holding : holdings) {
            InvestmentQuote quote = quotes.get(holding.getCode());
            if (quote == null) {
                throw new BizException("无法获取持仓 " + holding.getCode() + " 的行情，请稍后重试");
            }
            InvestmentPositionResponse position = toPosition(holding, quote, today);
            positions.add(position);
            marketValue = marketValue.add(position.getMarketValue());
        }

        BigDecimal totalAssets = account.getCashBalance().add(marketValue).setScale(2, RoundingMode.HALF_UP);
        InvestmentAccountResponse response = new InvestmentAccountResponse();
        response.setInitialCash(account.getInitialCash());
        response.setCashBalance(account.getCashBalance());
        response.setMarketValue(marketValue.setScale(2, RoundingMode.HALF_UP));
        response.setTotalAssets(totalAssets);
        response.setTotalProfit(totalAssets.subtract(account.getInitialCash()).setScale(2, RoundingMode.HALF_UP));
        response.setProfitPercent(percentage(response.getTotalProfit(), account.getInitialCash()));
        response.setPositions(positions);
        response.setRecentTrades(recentTrades(userId, null));
        return response;
    }

    public InvestmentQuote getQuote(String code) {
        return quoteClient.getQuote(normalizeCode(code));
    }

    @Transactional
    public InvestmentTradeResponse trade(InvestmentTradeReq request) {
        String code = normalizeCode(request.getCode());
        String side = request.getSide().trim().toUpperCase();
        if (!"BUY".equals(side) && !"SELL".equals(side)) {
            throw new BizException("交易方向必须为买入或卖出");
        }
        int quantity = request.getQuantity();
        if (quantity < 100 || quantity % 100 != 0) {
            throw new BizException("交易数量必须为 100 的整数倍");
        }

        InvestmentQuote quote = quoteClient.getQuote(code);
        Long userId = LoginUtils.getUserId();
        accountMapper.createIfAbsent(userId, initialCash);
        PaperInvestmentAccount account = accountMapper.selectForUpdate(userId);
        if (account == null) {
            throw new BizException("模拟账户初始化失败，请重试");
        }

        LocalDate today = LocalDate.now(MARKET_ZONE);
        PaperInvestmentHolding holding = holdingMapper.selectForUpdate(userId, code);
        if (holding != null && (holding.getSettledDate() == null || holding.getSettledDate().isBefore(today))) {
            holding.setAvailableQuantity(holding.getQuantity());
            holding.setSettledDate(today);
        }

        BigDecimal amount = quote.getCurrentPrice().multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = amount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.CEILING)
                .max(MIN_COMMISSION);
        BigDecimal stampTax = "SELL".equals(side) && "股票".equals(quote.getInstrumentType())
                ? amount.multiply(STOCK_STAMP_TAX_RATE).setScale(2, RoundingMode.CEILING)
                : BigDecimal.ZERO.setScale(2);

        if ("BUY".equals(side)) {
            BigDecimal payable = amount.add(commission);
            if (account.getCashBalance().compareTo(payable) < 0) {
                throw new BizException("虚拟资金不足，需 " + payable.toPlainString() + " 元，当前余额 "
                        + account.getCashBalance().toPlainString() + " 元");
            }
            account.setCashBalance(account.getCashBalance().subtract(payable).setScale(2, RoundingMode.HALF_UP));
            updateHoldingAfterBuy(holding, userId, quote, quantity, amount.add(commission), today);
        } else {
            if (holding == null || holding.getQuantity() <= 0) {
                throw new BizException("当前没有该证券的持仓");
            }
            if (holding.getSettledDate() != null && holding.getSettledDate().isEqual(today)
                    && holding.getAvailableQuantity() < quantity) {
                throw new BizException("可卖数量不足；A 股及场内基金实行 T+1 交收");
            }
            BigDecimal proceeds = amount.subtract(commission).subtract(stampTax);
            if (proceeds.signum() < 0) {
                throw new BizException("成交金额不足以覆盖交易费用");
            }
            account.setCashBalance(account.getCashBalance().add(proceeds).setScale(2, RoundingMode.HALF_UP));
            holding.setQuantity(holding.getQuantity() - quantity);
            holding.setAvailableQuantity(Math.max(0, holding.getAvailableQuantity() - quantity));
            holding.setName(quote.getName());
            if (holding.getSettledDate() == null || holding.getSettledDate().isBefore(today)) {
                holding.setAvailableQuantity(holding.getQuantity());
                holding.setSettledDate(today);
            }
            holdingMapper.updateById(holding);
        }
        accountMapper.updateById(account);

        PaperInvestmentTrade trade = new PaperInvestmentTrade();
        trade.setUserId(userId);
        trade.setCode(code);
        trade.setName(quote.getName());
        trade.setMarket(quote.getMarket());
        trade.setInstrumentType(quote.getInstrumentType());
        trade.setSide(side);
        trade.setQuantity(quantity);
        trade.setPrice(quote.getCurrentPrice());
        trade.setAmount(amount);
        trade.setCommission(commission);
        trade.setStampTax(stampTax);
        trade.setDeleted(0);
        tradeMapper.insert(trade);
        return toTradeResponse(trade);
    }

    public List<InvestmentTradeResponse> recentTrades() {
        return recentTrades(LoginUtils.getUserId(), null);
    }

    public List<InvestmentTradeResponse> recentTrades(String code) {
        return recentTrades(LoginUtils.getUserId(), code);
    }

    private PaperInvestmentAccount loadOrCreateAccount(Long userId) {
        accountMapper.createIfAbsent(userId, initialCash);
        PaperInvestmentAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PaperInvestmentAccount>()
                        .eq(PaperInvestmentAccount::getUserId, userId)
                        .eq(PaperInvestmentAccount::getDeleted, 0));
        if (account == null) {
            throw new BizException("模拟账户初始化失败，请重试");
        }
        return account;
    }

    private void updateHoldingAfterBuy(PaperInvestmentHolding holding, Long userId, InvestmentQuote quote,
                                       int quantity, BigDecimal addedCost, LocalDate today) {
        if (holding == null) {
            holding = new PaperInvestmentHolding();
            holding.setUserId(userId);
            holding.setCode(quote.getCode());
            holding.setName(quote.getName());
            holding.setMarket(quote.getMarket());
            holding.setInstrumentType(quote.getInstrumentType());
            holding.setQuantity(quantity);
            holding.setAvailableQuantity(0);
            holding.setAverageCost(addedCost.divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP));
            holding.setSettledDate(today);
            holding.setDeleted(0);
            holdingMapper.insert(holding);
            return;
        }
        int previousQuantity = holding.getQuantity();
        BigDecimal totalCost = holding.getAverageCost().multiply(BigDecimal.valueOf(previousQuantity)).add(addedCost);
        int nextQuantity = previousQuantity + quantity;
        holding.setAverageCost(totalCost.divide(BigDecimal.valueOf(nextQuantity), 6, RoundingMode.HALF_UP));
        holding.setQuantity(nextQuantity);
        holding.setAvailableQuantity(Math.max(0, holding.getAvailableQuantity()));
        holding.setName(quote.getName());
        holding.setMarket(quote.getMarket());
        holding.setInstrumentType(quote.getInstrumentType());
        holding.setSettledDate(today);
        holdingMapper.updateById(holding);
    }

    private InvestmentPositionResponse toPosition(PaperInvestmentHolding holding, InvestmentQuote quote, LocalDate today) {
        int available = holding.getSettledDate() != null && holding.getSettledDate().isBefore(today)
                ? holding.getQuantity() : holding.getAvailableQuantity();
        BigDecimal costValue = holding.getAverageCost().multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal marketValue = quote.getCurrentPrice().multiply(BigDecimal.valueOf(holding.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal profit = marketValue.subtract(costValue).setScale(2, RoundingMode.HALF_UP);

        InvestmentPositionResponse response = new InvestmentPositionResponse();
        response.setCode(holding.getCode());
        response.setName(quote.getName());
        response.setMarket(quote.getMarket());
        response.setInstrumentType(quote.getInstrumentType());
        response.setQuantity(holding.getQuantity());
        response.setAvailableQuantity(available);
        response.setAverageCost(holding.getAverageCost());
        response.setCurrentPrice(quote.getCurrentPrice());
        response.setCostValue(costValue);
        response.setMarketValue(marketValue);
        response.setUnrealizedProfit(profit);
        response.setProfitPercent(percentage(profit, costValue));
        response.setQuoteTime(quote.getQuoteTime());
        return response;
    }

    private List<InvestmentTradeResponse> recentTrades(Long userId, String code) {
        List<PaperInvestmentTrade> trades = tradeMapper.selectList(
                new LambdaQueryWrapper<PaperInvestmentTrade>()
                        .eq(PaperInvestmentTrade::getUserId, userId)
                        .eq(PaperInvestmentTrade::getDeleted, 0)
                        .eq(code != null && !code.isBlank(), PaperInvestmentTrade::getCode, code)
                        .orderByDesc(PaperInvestmentTrade::getCreateTime)
                        .last("LIMIT 50"));
        return trades.stream().map(this::toTradeResponse).toList();
    }

    private InvestmentTradeResponse toTradeResponse(PaperInvestmentTrade trade) {
        InvestmentTradeResponse response = new InvestmentTradeResponse();
        response.setId(trade.getId());
        response.setCode(trade.getCode());
        response.setName(trade.getName());
        response.setMarket(trade.getMarket());
        response.setInstrumentType(trade.getInstrumentType());
        response.setSide(trade.getSide());
        response.setQuantity(trade.getQuantity());
        response.setPrice(trade.getPrice());
        response.setAmount(trade.getAmount());
        response.setCommission(trade.getCommission());
        response.setStampTax(trade.getStampTax());
        response.setTradeTime(trade.getCreateTime() != null ? trade.getCreateTime() : LocalDateTime.now(MARKET_ZONE));
        return response;
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String normalizeCode(String code) {
        if (code == null) {
            throw new BizException("证券代码不能为空");
        }
        String normalized = code.trim();
        if (!normalized.matches("\\d{6}")) {
            throw new BizException("证券代码必须为 6 位数字");
        }
        return normalized;
    }
}
