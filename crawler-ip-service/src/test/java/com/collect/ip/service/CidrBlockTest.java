package com.collect.ip.service;

import com.collect.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CidrBlockTest {

    @Test
    void samplesPublicIpv4AddressesWithoutNetworkOrBroadcastAddress() {
        CidrBlock block = CidrBlock.parse("103.21.244.0/30");

        assertEquals("103.21.244.1", block.sampleAddress(0));
        assertEquals("103.21.244.2", block.sampleAddress(1));
        assertThrows(BizException.class, () -> block.sampleAddress(2));
    }

    @Test
    void samplesPublicIpv6AddressesAfterTheNetworkAddress() {
        CidrBlock block = CidrBlock.parse("2400:cb00::/32");

        assertEquals("2400:cb00:0:0:0:0:0:1", block.sampleAddress(0));
        assertEquals(6, block.ipVersion());
    }

    @Test
    void skipsAddressesAlreadyPresentWhenSampling() {
        CidrBlock block = CidrBlock.parse("103.21.244.0/29");

        assertEquals(
                java.util.List.of("103.21.244.2", "103.21.244.4", "103.21.244.5"),
                block.sampleAddresses(3, Set.of("103.21.244.1", "103.21.244.3"))
        );
        assertThrows(
                BizException.class,
                () -> block.sampleAddresses(4, Set.of("103.21.244.1", "103.21.244.2", "103.21.244.3",
                        "103.21.244.4", "103.21.244.5", "103.21.244.6"))
        );
    }

    @Test
    void rejectsCidrWithHostBitsSet() {
        assertThrows(BizException.class, () -> CidrBlock.parse("103.21.244.1/22"));
    }
}
