package com.msg.ppap.exchange.application.port.out;

/** Contract definition offering one asset under an access and a usage policy. */
public record ContractDefinitionSpec(String id, String accessPolicyId, String contractPolicyId, String assetId) {}
