package com.collect.ip.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CfIpProbeServiceTest {

    @Test
    void extractsCloudflareColoCodeFromRayHeader() {
        assertEquals("HKG", CfIpProbeService.coloCodeFromCfRay("9f2e3d4c5b6a7081-HKG"));
        assertEquals("LAX", CfIpProbeService.coloCodeFromCfRay("9F2E3D4C5B6A7081-LAX"));
        assertEquals("FRA", CfIpProbeService.coloCodeFromCfRay("9f2e3d4c5b6a7081-fra"));
    }

    @Test
    void ignoresMissingOrMalformedRayHeader() {
        assertNull(CfIpProbeService.coloCodeFromCfRay(null));
        assertNull(CfIpProbeService.coloCodeFromCfRay("HKG"));
        assertNull(CfIpProbeService.coloCodeFromCfRay("not-a-ray-id"));
    }
}
