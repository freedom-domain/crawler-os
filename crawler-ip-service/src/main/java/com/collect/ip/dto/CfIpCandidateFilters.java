package com.collect.ip.dto;

import java.util.List;

public record CfIpCandidateFilters(List<String> regions, List<String> colos) {
}
