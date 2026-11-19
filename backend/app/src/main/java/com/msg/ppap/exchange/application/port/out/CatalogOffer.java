package com.msg.ppap.exchange.application.port.out;

import java.util.Map;

/**
 * Offer of an asset in the partner's catalog.
 *
 * @param offerId ODRL offer id
 * @param policy offer as JSON-LD object (compacted), used for the contract request
 */
public record CatalogOffer(String assetId, String offerId, Map<String, Object> policy) {

    public CatalogOffer {
        policy = Map.copyOf(policy);
    }
}
