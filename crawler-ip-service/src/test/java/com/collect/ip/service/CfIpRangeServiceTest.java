package com.collect.ip.service;

import com.collect.common.exception.BizException;
import com.collect.ip.entity.CfIpCandidate;
import com.collect.ip.entity.CfIpRange;
import com.collect.ip.mapper.CfIpCandidateMapper;
import com.collect.ip.mapper.CfIpRangeMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CfIpRangeServiceTest {

    private final CfIpRangeMapper rangeMapper = mock(CfIpRangeMapper.class);
    private final CfIpCandidateMapper candidateMapper = mock(CfIpCandidateMapper.class);
    private final CfIpRangeService service = new CfIpRangeService(
            rangeMapper, candidateMapper, mock(CfIpProbeService.class), mock(ObjectMapper.class));

    @Test
    void importsAllUsableAddressesFromEnabledIpv4RangesAndSkipsIpv6() {
        CfIpRange ipv4 = range("103.21.244.0/30", 4, 12L);
        ipv4.setRegion("APAC");
        CfIpRange ipv6 = range("2400:cb00::/32", 6, 13L);
        when(rangeMapper.selectEnabled()).thenReturn(List.of(ipv4, ipv6));
        List<CfIpCandidate> inserted = new ArrayList<>();
        when(candidateMapper.insertIgnoreBatch(anyList())).thenAnswer(invocation -> {
            List<CfIpCandidate> batch = invocation.getArgument(0);
            inserted.addAll(batch);
            return batch.size() - 1;
        });

        int added = service.importEnabledIpv4Addresses();

        assertEquals(1, added);
        assertEquals("103.21.244.1", inserted.get(0).getIpAddress());
        assertEquals("103.21.244.2", inserted.get(1).getIpAddress());
        assertEquals("APAC", inserted.get(0).getRegion());
        assertEquals(12L, inserted.get(0).getSourceRangeId());
        verify(candidateMapper).insertIgnoreBatch(anyList());
    }

    @Test
    void rejectsImportWhenNoIpv4RangeIsEnabled() {
        when(rangeMapper.selectEnabled()).thenReturn(List.of(range("2400:cb00::/32", 6, 13L)));

        assertThrows(BizException.class, service::importEnabledIpv4Addresses);
    }

    @Test
    void insertsLargeRangeInBatchesOfAtMostFiveHundred() {
        when(rangeMapper.selectEnabled()).thenReturn(List.of(range("103.21.244.0/22", 4, 12L)));
        List<Integer> batchSizes = new ArrayList<>();
        when(candidateMapper.insertIgnoreBatch(anyList())).thenAnswer(invocation -> {
            int batchSize = ((List<?>) invocation.getArgument(0)).size();
            batchSizes.add(batchSize);
            return batchSize;
        });

        int added = service.importEnabledIpv4Addresses();

        assertEquals(1022, added);
        assertEquals(List.of(500, 500, 22), batchSizes);
        verify(candidateMapper, times(3)).insertIgnoreBatch(anyList());
    }

    private CfIpRange range(String cidr, int ipVersion, long id) {
        CfIpRange range = new CfIpRange();
        range.setId(id);
        range.setCidr(cidr);
        range.setIpVersion(ipVersion);
        range.setRegion("全球任播");
        range.setEnabled(true);
        return range;
    }
}
