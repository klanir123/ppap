package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.application.port.out.PartnerKeyStore;
import com.msg.ppap.exchange.application.port.out.StepListener;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.identity.PartnerKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Partner signature keys: implements {@link PartnerKeys} for the identity module from the local cache and fetches a
 * partner's JWK set via the dataspace (asset {@code ppap-public-keys}, same flow as notifications, {@code GET}).
 */
@Service
public class PartnerKeyService implements PartnerKeys {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerKeyService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PartnerKeyStore keys;
    private final DataspaceConnector connector;
    private final PartnerDirectory partners;
    private final ExchangeContext context;

    public PartnerKeyService(
            PartnerKeyStore keys, DataspaceConnector connector, PartnerDirectory partners, ExchangeContext context) {
        this.keys = keys;
        this.connector = connector;
        this.partners = partners;
        this.context = context;
    }

    @Override
    public Optional<Map<String, Object>> find(String bpnl, String kid) {
        return keys.find(bpnl, kid);
    }

    /**
     * Fetches the partner's JWK set via EDC and stores its EC signature keys.
     *
     * @return number of stored keys
     * @throws DataspaceException if the partner is not reachable or answers with an error
     */
    public int fetch(String bpnl, StepListener listener) {
        PartnerEndpoint partner = partners.endpoint(bpnl, StepName.KEY_FETCH);
        EndpointDataReference edr = connector.obtainEndpoint(partner, ExchangeAssets.PUBLIC_KEYS_ASSET, listener);
        DataPlaneResponse response = connector.call(edr, "GET", null, null, Map.of());
        if (response.status() == 401 || response.status() == 403) {
            connector.invalidateEndpoint(partner, ExchangeAssets.PUBLIC_KEYS_ASSET);
        }
        if (!response.successful()) {
            throw new DataspaceException(
                    StepName.KEY_FETCH,
                    "JWKS of " + bpnl + " answered " + response.status() + ": " + response.bodySnippet(),
                    true,
                    response.status(),
                    null);
        }
        JsonNode jwks;
        try {
            jwks = JSON.readTree(response.body());
        } catch (RuntimeException ex) {
            throw new DataspaceException(StepName.KEY_FETCH, "JWKS of " + bpnl + " is not valid JSON", true, null, ex);
        }
        int stored = 0;
        for (JsonNode key : jwks.path("keys")) {
            String kid = key.path("kid").asString(null);
            if (kid == null || !"EC".equals(key.path("kty").asString(null)) || key.has("d")) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> jwk = new LinkedHashMap<>(JSON.convertValue(key, Map.class));
            keys.save(bpnl, kid, jwk, context.now());
            stored++;
        }
        LOG.info("Fetched {} signature key(s) of partner {} via EDC", stored, bpnl);
        return stored;
    }
}
