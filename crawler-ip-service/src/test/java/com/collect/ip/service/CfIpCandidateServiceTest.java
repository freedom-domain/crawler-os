package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.mapper.CfIpCandidateMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
