package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.mapper.CfIpCandidateMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class CfIpCandidateServiceTest {

    private final CfIpCandidateMapper mapper = mock(CfIpCandidateMapper.class);
    private final CfIpCandidateService service = new CfIpCandidateService(mapper, mock(CfIpProbeService.class));

    @Test
    void formatsRandomAvailableIpsWithRegionAndMegabytesPerSecond() {
        CfIpCandidate candidate = new CfIpCandidate();
        candidate.setIpAddress("141.101.121.200");
        candidate.setRegion("US");
        candidate.setSpeedMbps(new BigDecimal("157.84"));
        when(mapper.selectRandomAvailableCandidates(10)).thenReturn(List.of(candidate));

        assertEquals(List.of("141.101.121.200#US 自选官方优选 19.73MB/s"),
                service.randomAvailableIpAddresses(10));
        verify(mapper).selectRandomAvailableCandidates(10);
    }

    @Test
    void rejectsInvalidPublicIpCounts() {
        assertThrows(BizException.class, () -> service.randomAvailableIpAddresses(0));
        assertThrows(BizException.class, () -> service.randomAvailableIpAddresses(101));
    }

    @Test
    void clampsCandidatePageSizeAndNormalizesFilters() {
        when(mapper.selectCandidatePage(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new Page<>());

        service.page(0, 500, " 198.41. ", 4, " ", "SJC", "ALL", 200, null);

        verify(mapper).selectCandidatePage(argThat(page -> page.getCurrent() == 1 && page.getSize() == 100),
            eq("198.41."), eq(4),
                isNull(), eq("SJC"), isNull(), eq(200), isNull());
    }

    @Test
    void rejectsUnsupportedIpVersion() {
        assertThrows(BizException.class, () -> service.page(1, 10, null, 5,
                null, null, null, null, null));
    }
}
