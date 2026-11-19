package com.msg.ppap.exchange.application.port.out;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Cache of partner signature keys (table {@code ex_partner_key}); scoped to the current tenant. */
public interface PartnerKeyStore {

    Optional<Map<String, Object>> find(String bpnl, String kid);

    /** Inserts or replaces the key. */
    void save(String bpnl, String kid, Map<String, Object> jwk, Instant fetchedAt);
}
