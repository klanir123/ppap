package com.msg.ppap.exchange.domain;

/**
 * Signature keys of a partner were fetched via the dataspace (trigger of the re-verification of {@code UNVERIFIED}
 * revisions, docs/api/cascade-transparency-api.md §7).
 *
 * @param partnerBpnl partner
 * @param keys number of stored keys
 */
public record PartnerKeysFetched(String partnerBpnl, int keys) {}
