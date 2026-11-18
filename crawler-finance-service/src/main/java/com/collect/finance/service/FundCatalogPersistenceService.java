package com.collect.finance.service;

import com.collect.finance.entity.FundCatalog;
import com.collect.finance.mapper.FundCatalogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FundCatalogPersistenceService {

    private static final int BATCH_SIZE = 500;

    private final FundCatalogMapper catalogMapper;

    public FundCatalogPersistenceService(FundCatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @Transactional
    public void replaceCatalog(List<FundCatalog> items) {
        catalogMapper.deleteAll();
        for (int start = 0; start < items.size(); start += BATCH_SIZE) {
            catalogMapper.insertBatch(items.subList(start, Math.min(start + BATCH_SIZE, items.size())));
        }
    }
}
