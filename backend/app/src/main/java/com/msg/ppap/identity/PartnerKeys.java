package com.msg.ppap.identity;

import java.util.Map;
import java.util.Optional;

/**
 * Extension point: public signature keys of partners (JWK, EC P-256) by BPNL and key id. Implemented by the
 * {@code exchange} module, which retrieves the partner's JWK set via the dataspace (asset {@code ppap-public-keys})
 * and caches it. Identity only knows this interface, so there is no dependency cycle.
 */
public interface PartnerKeys {

    /**
     * Public JWK (JSON object members) of the partner with the given key id.
     *
     * @param bpnl partner BPNL
     * @param kid key id (RFC 7638 thumbprint)
     */
    Optional<Map<String, Object>> find(String bpnl, String kid);
}
