package com.collect.finance.service;

import com.collect.finance.entity.StockCatalog;
import com.collect.finance.mapper.StockCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StockCatalogPersistenceService {

    private static final int BATCH_SIZE = 500;

    private final StockCatalogMapper catalogMapper;

    public StockCatalogPersistenceService(StockCatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Transactional
    public void replaceCatalog(List<StockCatalog> items) {
        catalogMapper.deleteAll();
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            catalogMapper.insertBatch(items.subList(start, Math.min(start + BATCH_SIZE, items.size())));
        }
    }
}
