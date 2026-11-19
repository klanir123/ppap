package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.CatalogOffer;
import com.msg.ppap.exchange.application.port.out.DataPlaneResponse;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.DataspaceException;
import com.msg.ppap.exchange.application.port.out.EndpointDataReference;
import com.msg.ppap.exchange.application.port.out.PartnerEndpoint;
import com.msg.ppap.exchange.application.port.out.StepListener;
import com.msg.ppap.exchange.config.PullProperties;
import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import com.msg.ppap.semantics.api.AasDescriptors;
import com.msg.ppap.semantics.api.AasDescriptors.ShellView;
import com.msg.ppap.semantics.api.DigitalTwinIds;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Access to a partner's Digital Twin Registry via EDC (Digital Twin KIT, customer side): the DTR offer is found in the
 * partner's catalog by {@code dct:type = cx-taxo:DigitalTwinRegistry} (cached per partner, also when missing), the EDR
 * is reused like for notifications; lookup and shell descriptor go through the partner's data plane, which adds the
 * consumer BPN ({@code Edc-Bpn}) so that the DTR only shows twins released for us ({@code externalSubjectId}).
 */
@Component
public class DtrAccess {

    private final DataspaceConnector connector;
    private final PullProperties properties;
    private final ExchangeContext context;
    private final Map<String, CachedOffer> offers = new ConcurrentHashMap<>();

    public DtrAccess(DataspaceConnector connector, PullProperties properties, ExchangeContext context) {
        this.connector = connector;
        this.properties = properties;
        this.context = context;
    }

    private record CachedOffer(Optional<CatalogOffer> offer, Instant until) {}

    /**
     * The partner's DTR offer, from the cache or the catalog (step {@code CATALOG}).
     *
     * @return empty if the partner offers no DTR to us
     */
    public Optional<CatalogOffer> offer(PartnerEndpoint partner, StepListener listener) {
        return offer(partner, listener, false);
    }

    /**
     * The partner's DTR offer.
     *
     * @param refresh bypass the cache (retry of a failed pull: the partner may have provisioned or changed its offer)
     */
    public Optional<CatalogOffer> offer(PartnerEndpoint partner, StepListener listener, boolean refresh) {
        Instant now = context.now();
        CachedOffer cached = offers.get(partner.bpnl());
        if (!refresh && cached != null && cached.until().isAfter(now)) {
            return cached.offer();
        }
        long start = System.nanoTime();
        List<CatalogOffer> found;
        try {
            found = connector.requestCatalogByType(partner, ExchangeAssets.DTR_TYPE);
        } catch (DataspaceException ex) {
            listener.onStep(
                    StepName.CATALOG,
                    StepStatus.FAILED,
                    elapsed(start),
                    Map.of("dctType", ExchangeAssets.DTR_TYPE, "error", String.valueOf(ex.getMessage())));
            throw ex;
        }
        Optional<CatalogOffer> offer = found.stream().findFirst();
        listener.onStep(
                StepName.CATALOG,
                offer.isPresent() ? StepStatus.OK : StepStatus.FAILED,
                elapsed(start),
                Map.of(
                        "dctType",
                        ExchangeAssets.DTR_TYPE,
                        "assetId",
                        offer.map(CatalogOffer::assetId).orElse(""),
                        "offers",
                        found.size()));
        // a missing offer expires quickly: the partner may still be provisioning (access group not populated yet)
        offers.put(
                partner.bpnl(),
                new CachedOffer(
                        offer,
                        now.plus(offer.isPresent() ? properties.offerCacheTtl() : properties.noOfferCacheTtl())));
        return offer;
    }

    /**
     * EDR of the partner's DTR.
     *
     * @throws DataspaceException if the partner offers no DTR or a step fails
     */
    public EndpointDataReference endpoint(PartnerEndpoint partner, StepListener listener) {
        return endpoint(partner, listener, false);
    }

    /**
     * EDR of the partner's DTR; a missing offer or a failed negotiation removes the cached offer, so the next attempt
     * asks the catalog again.
     *
     * @param refresh bypass the offer cache
     * @throws DataspaceException if the partner offers no DTR or a step fails
     */
    public EndpointDataReference endpoint(PartnerEndpoint partner, StepListener listener, boolean refresh) {
        Optional<CatalogOffer> offer = offer(partner, listener, refresh);
        if (offer.isEmpty()) {
            offers.remove(partner.bpnl());
            throw new DataspaceException(
                    StepName.CATALOG, "Partner " + partner.bpnl() + " offers no Digital Twin Registry to us");
        }
        try {
            return connector.obtainEndpoint(partner, offer.get(), listener);
        } catch (DataspaceException ex) {
            offers.remove(partner.bpnl());
            throw ex;
        }
    }

    /** Forgets the cached offer and EDRs (after the data plane rejected the token). */
    public void invalidate(PartnerEndpoint partner) {
        CachedOffer cached = offers.remove(partner.bpnl());
        if (cached != null) {
            cached.offer().ifPresent(o -> connector.invalidateEndpoint(partner, o.assetId()));
        }
    }

    /** Result of a DTR read. */
    public record Lookup<T>(T value, int httpStatus, long durationMs) {}

    /**
     * {@code GET /lookup/shells?assetIds=<ppapExchangeId>} (step {@code DTR_LOOKUP}).
     *
     * @return shell ids visible to us (empty if none)
     */
    public Lookup<List<String>> lookup(EndpointDataReference edr, UUID exchangeId) {
        long start = System.nanoTime();
        DataPlaneResponse response = connector.call(
                edr,
                "GET",
                "/lookup/shells?assetIds="
                        + AasDescriptors.lookupAssetId(DigitalTwinIds.EXCHANGE_ID_KEY, exchangeId.toString()),
                null,
                null,
                Map.of("Accept", "application/json"));
        long duration = elapsed(start).toMillis();
        if (!response.successful()) {
            throw httpFailure(StepName.DTR_LOOKUP, "DTR lookup", response);
        }
        return new Lookup<>(AasDescriptors.parseLookup(response.body()), response.status(), duration);
    }

    /**
     * {@code GET /shell-descriptors/{id}} (step {@code SHELL_DESCRIPTOR}).
     *
     * @return the descriptor, empty if not found (404)
     */
    public Lookup<Optional<ShellView>> shell(EndpointDataReference edr, String shellId) {
        long start = System.nanoTime();
        DataPlaneResponse response = connector.call(
                edr,
                "GET",
                "/shell-descriptors/" + AasDescriptors.encodeId(shellId),
                null,
                null,
                Map.of("Accept", "application/json"));
        long duration = elapsed(start).toMillis();
        if (response.status() == 404) {
            return new Lookup<>(Optional.empty(), 404, duration);
        }
        if (!response.successful()) {
            throw httpFailure(StepName.SHELL_DESCRIPTOR, "Shell descriptor", response);
        }
        try {
            return new Lookup<>(Optional.of(AasDescriptors.parseShell(response.body())), response.status(), duration);
        } catch (IllegalArgumentException ex) {
            throw new DataspaceException(
                    StepName.SHELL_DESCRIPTOR, "Shell descriptor is not valid JSON", false, response.status(), ex);
        }
    }

    static DataspaceException httpFailure(StepName step, String what, DataPlaneResponse response) {
        return new DataspaceException(
                step,
                what + " answered " + response.status() + ": " + response.bodySnippet(),
                true,
                response.status(),
                null);
    }

    static Map<String, Object> details(Object... keyValues) {
        var details = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                details.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return details;
    }

    private static Duration elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos);
    }
}
