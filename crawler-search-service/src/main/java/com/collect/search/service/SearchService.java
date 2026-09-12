package com.collect.search.service;

import com.collect.search.es.SpiderContentDoc;
import com.collect.search.es.SpiderContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final SpiderContentRepository repository;

    public Page<SpiderContentDoc> search(String keyword, Long spiderId,
                                         int current, int size) {
        PageRequest pageRequest = PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "crawlTime"));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        return repository.search(kw, spiderId, pageRequest);
    }

    public SpiderContentDoc getById(String id) {
        return repository.findById(id).orElse(null);
    }
}
