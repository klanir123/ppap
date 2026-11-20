package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.AssetSpec;
import com.msg.ppap.exchange.application.port.out.CallbackSecretStore;
import com.msg.ppap.exchange.application.port.out.ContractDefinitionSpec;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.DtrOfferProperties;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.masterdata.api.MasterdataQueries.PolicyProfileInfo;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Declarative, idempotent provisioning of the own EDC (docs/api/exchange-api.md, „Angebotene Assets“): assets {@code
 * ppap-notification-receive} and {@code ppap-public-keys}, access policy (business partner group {@code
 * ppap-partners}), usage policy (FrameworkAgreement + UsagePurpose), contract definitions and the group membership
 * (active, exchange-ready partners with a relation). Runs on startup (retried until the EDC is up), after master data
 * changes and periodically.
 *
 * <p>Shared secret: written to the participant's vault and referenced by {@code secretName} in the DataAddress, so
 * it never appears in plain text in the EDC database; the provider data plane resolves it and sends it as header
 * {@code authKey} (verified in the Tractus-X EDC 0.13 data plane, {@code BaseCommonHttpParamsDecorator}). Without
 * vault the secret is set as {@code authCode} (fallback, logged as warning).
 */
@Service
public class ProvisioningService {

    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningService.class);

    private final DataspaceConnector connector;
    private final CallbackSecretStore secrets;
    private final MasterdataQueries masterdata;
    private final EdcProperties edc;
    private final ExchangeProperties exchange;
    private final ExchangeContext context;
    private final DtrOfferProperties dtr;
    private final AtomicReference<ProvisioningState> state = new AtomicReference<>(ProvisioningState.INITIAL);
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public ProvisioningService(
            DataspaceConnector connector,
            CallbackSecretStore secrets,
            MasterdataQueries masterdata,
            EdcProperties edc,
            ExchangeProperties exchange,
            ExchangeContext context,
            DtrOfferProperties dtr) {
        this.dtr = dtr;
        this.connector = connector;
        this.secrets = secrets;
        this.masterdata = masterdata;
        this.edc = edc;
        this.exchange = exchange;
        this.context = context;
    }

    /** Marks the provisioning as outdated (e.g. partner or relation changed); applied by the next scheduler run. */
    public void requestReconcile() {
        dirty.set(true);
    }

    /** Runs the reconciliation if requested, failed recently or due periodically. */
    public void reconcileIfDue() {
        ProvisioningState current = state.get();
        Instant now = context.now();
        boolean due = current.lastRun() == null
                || (!current.ok()
                        && current.lastRun().plus(exchange.provisioningRetry()).isBefore(now))
                || current.lastRun().plus(exchange.provisioningInterval()).isBefore(now);
        if (dirty.get() || due) {
            reconcile();
        }
    }

    /** Reconciles assets, policies, contract definitions and the partner group now. */
    public synchronized ProvisioningState reconcile() {
        dirty.set(false);
        Instant now = context.now();
        if (edc.callbackBaseUrl() == null || edc.callbackBaseUrl().isBlank() || !edc.callbackSecretConfigured()) {
            return update(new ProvisioningState(
                    List.of(), List.of(), false, now, "PPAP_EDC_CALLBACK_BASE_URL / PPAP_EDC_CALLBACK_SECRET not set"));
        }
        try {
            Map<String, String> auth = callbackAuthentication();
            String base = edc.callbackBaseUrl().replaceAll("/+$", "");
            connector.ensureAsset(new AssetSpec(
                    ExchangeAssets.NOTIFICATION_ASSET,
                    ExchangeAssets.NOTIFICATION_TYPE,
                    ExchangeAssets.ASSET_VERSION,
                    "PPAP notification receive API (msg CX PPAP Connector)",
                    dataAddress(base + ExchangeAssets.NOTIFICATIONS_PATH, true, auth)));
            connector.ensureAsset(new AssetSpec(
                    ExchangeAssets.PUBLIC_KEYS_ASSET,
                    ExchangeAssets.PUBLIC_KEYS_TYPE,
                    ExchangeAssets.ASSET_VERSION,
                    "Public signature keys (JWKS) of the participant (msg CX PPAP Connector)",
                    dataAddress(base + ExchangeAssets.JWKS_PATH, false, auth)));
            Set<String> partners = new TreeSet<>();
            masterdata.exchangePartners().forEach(p -> partners.add(p.bpnl()));
            PolicyProfileInfo defaultProfile = defaultProfile();
            PolicySpec access =
                    OfferPolicies.sharedAccess(defaultProfile, edc.policy().partnerGroup(), partners);
            PolicySpec usage = OfferPolicies.usage(defaultProfile);
            Set<String> usedPolicies = new TreeSet<>(Set.of(access.id(), usage.id()));
            Map<String, String> usagePolicies = new java.util.TreeMap<>();
            connector.ensurePolicy(access);
            connector.ensurePolicy(usage);
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    ExchangeAssets.NOTIFICATION_CONTRACT, access.id(), usage.id(), ExchangeAssets.NOTIFICATION_ASSET));
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    ExchangeAssets.PUBLIC_KEYS_CONTRACT, access.id(), usage.id(), ExchangeAssets.PUBLIC_KEYS_ASSET));
            Set<String> members =
                    connector.ensureBusinessPartnerGroup(edc.policy().partnerGroup(), partners);
            List<String> assets = new java.util.ArrayList<>(
                    List.of(ExchangeAssets.NOTIFICATION_ASSET, ExchangeAssets.PUBLIC_KEYS_ASSET));
            usagePolicies.put(ExchangeAssets.NOTIFICATION_ASSET, usage.id());
            usagePolicies.put(ExchangeAssets.PUBLIC_KEYS_ASSET, usage.id());
            assets.addAll(provisionPullOffers(base, auth, access, usage, partners, usedPolicies, usagePolicies));
            removeStalePolicies(usedPolicies);
            ProvisioningState done = new ProvisioningState(
                    assets,
                    List.copyOf(new TreeSet<>(members)),
                    true,
                    now,
                    null,
                    usagePolicies,
                    List.copyOf(usedPolicies),
                    usagePolicies.keySet().stream()
                            .map(ExchangeAssets::contractOf)
                            .sorted()
                            .toList());
            if (!state.get().ok()
                    || !state.get().partners().equals(done.partners())
                    || !state.get().usagePolicies().equals(done.usagePolicies())) {
                LOG.info(
                        "EDC provisioning complete: assets {}, group {} = {}, usage policies {}",
                        done.assets(),
                        edc.policy().partnerGroup(),
                        done.partners(),
                        done.usagePolicies());
            }
            return update(done);
        } catch (RuntimeException ex) {
            if (state.get().ok() || state.get().lastRun() == null) {
                LOG.warn("EDC provisioning failed (retry every {}): {}", exchange.provisioningRetry(), ex.toString());
            } else {
                LOG.debug("EDC provisioning failed again: {}", ex.toString());
            }
            return update(new ProvisioningState(
                    state.get().assets(),
                    state.get().partners(),
                    false,
                    now,
                    ex.getMessage(),
                    state.get().usagePolicies(),
                    state.get().policyIds(),
                    state.get().contractDefinitionIds()));
        }
    }

    /**
     * Phase 5 (docs/api/pull-api.md, „Angebote“): the own DTR ({@code ppap-dtr}, OAuth2 via the data plane, partner
     * group), per partner a documents asset and – towards customers, if a DTR is offered – a submodel asset, each
     * with an access policy for exactly that BPNL. Offers of partners without relation are removed.
     *
     * @return provisioned asset ids
     */
    @SuppressWarnings("java:S107")
    private List<String> provisionPullOffers(
            String base,
            Map<String, String> auth,
            PolicySpec access,
            PolicySpec usage,
            Set<String> partners,
            Set<String> usedPolicies,
            Map<String, String> usagePolicies) {
        List<String> assets = new java.util.ArrayList<>();
        if (dtr.offered()) {
            connector.ensureAsset(new AssetSpec(
                    ExchangeAssets.DTR_ASSET,
                    ExchangeAssets.DTR_TYPE,
                    ExchangeAssets.DTR_VERSION,
                    "Digital Twin Registry (msg CX PPAP Connector)",
                    dtrDataAddress()));
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    ExchangeAssets.DTR_CONTRACT, access.id(), usage.id(), ExchangeAssets.DTR_ASSET));
            assets.add(ExchangeAssets.DTR_ASSET);
            usagePolicies.put(ExchangeAssets.DTR_ASSET, usage.id());
        }
        Set<String> desired = new TreeSet<>();
        for (String bpnl : partners) {
            // phase 6: per-partner assets carry the policy profile of the relation with this partner
            PolicyProfileInfo profile = profileOf(bpnl);
            PolicySpec bpn = OfferPolicies.partnerAccess(profile, bpnl);
            PolicySpec partnerUsage = OfferPolicies.usage(profile);
            connector.ensurePolicy(bpn);
            if (!partnerUsage.id().equals(usage.id())) {
                connector.ensurePolicy(partnerUsage);
            }
            usedPolicies.add(bpn.id());
            usedPolicies.add(partnerUsage.id());
            String documents = ExchangeAssets.documentsAsset(bpnl);
            connector.ensureAsset(new AssetSpec(
                    documents,
                    ExchangeAssets.DOCUMENTS_TYPE,
                    ExchangeAssets.ASSET_VERSION,
                    "PPAP documents shared with " + bpnl + " (msg CX PPAP Connector)",
                    pathDataAddress(base + ExchangeAssets.DOCUMENTS_PATH, auth)));
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    documents + ExchangeAssets.CONTRACT_SUFFIX, bpn.id(), partnerUsage.id(), documents));
            desired.add(documents);
            usagePolicies.put(documents, partnerUsage.id());
            if (dtr.offered() && isCustomer(bpnl)) {
                String submission = ExchangeAssets.submissionAsset(bpnl);
                connector.ensureAsset(new AssetSpec(
                        submission,
                        ExchangeAssets.SUBMODEL_TYPE,
                        ExchangeAssets.ASSET_VERSION,
                        "PPAP submissions (submodel PpapSubmission) for " + bpnl + " (msg CX PPAP Connector)",
                        pathDataAddress(base + ExchangeAssets.SUBMODELS_PATH, auth)));
                connector.ensureContractDefinition(new ContractDefinitionSpec(
                        submission + ExchangeAssets.CONTRACT_SUFFIX, bpn.id(), partnerUsage.id(), submission));
                desired.add(submission);
                usagePolicies.put(submission, partnerUsage.id());
            }
        }
        assets.addAll(desired);
        removeStaleOffers(desired);
        return assets;
    }

    /**
     * Policy profile of the relation with a partner: the customer relation's profile if active, else the supplier
     * relation's; unknown or inactive profiles fall back to {@code default}.
     */
    PolicyProfileInfo profileOf(String bpnl) {
        String code = activeRelation(bpnl, "CUSTOMER")
                .or(() -> activeRelation(bpnl, "SUPPLIER"))
                .map(MasterdataQueries.RelationInfo::policyProfile)
                .orElse("default");
        return masterdata
                .findPolicyProfile(code)
                .filter(PolicyProfileInfo::active)
                .orElseGet(this::defaultProfile);
    }

    private java.util.Optional<MasterdataQueries.RelationInfo> activeRelation(String bpnl, String role) {
        return masterdata.findRelation(bpnl, role).filter(MasterdataQueries.RelationInfo::active);
    }

    /** The profile {@code default} of the master data, or the configuration as fallback. */
    PolicyProfileInfo defaultProfile() {
        return masterdata.findPolicyProfile("default").orElseGet(() -> OfferPolicies.fallback(edc.policy()));
    }

    /** Removes own policy definitions that no contract definition uses anymore (best effort). */
    private void removeStalePolicies(Set<String> usedPolicies) {
        try {
            for (Map<String, Object> policy : connector.listPolicies(1000)) {
                Object id = policy.get("@id");
                if (id instanceof String policyId
                        && (policyId.startsWith(ExchangeAssets.ACCESS_POLICY_PREFIX)
                                || policyId.startsWith(ExchangeAssets.USAGE_POLICY + "-"))
                        && !usedPolicies.contains(policyId)
                        && connector.removePolicy(policyId)) {
                    LOG.info("EDC: removed unused policy {}", policyId);
                }
            }
        } catch (RuntimeException ex) {
            LOG.debug("EDC: cleanup of unused policies skipped: {}", ex.toString());
        }
    }

    private void removeStaleOffers(Set<String> desiredAssets) {
        for (String definition : connector.listContractDefinitionIds()) {
            boolean perPartner = definition.startsWith(ExchangeAssets.SUBMISSION_PREFIX)
                    || definition.startsWith(ExchangeAssets.DOCUMENTS_PREFIX);
            if (!perPartner || !definition.endsWith(ExchangeAssets.CONTRACT_SUFFIX)) {
                continue;
            }
            String asset = definition.substring(0, definition.length() - ExchangeAssets.CONTRACT_SUFFIX.length());
            if (!desiredAssets.contains(asset)) {
                LOG.info("EDC: removing offer {} (no relation with exchange anymore)", asset);
                connector.removeOffer(definition, asset);
            }
        }
    }

    private boolean isCustomer(String bpnl) {
        return masterdata
                .findRelation(bpnl, "CUSTOMER")
                .map(MasterdataQueries.RelationInfo::active)
                .orElse(false);
    }

    /**
     * DataAddress of the DTR: path and query of the consumer are forwarded (lookup, shell descriptors); the data plane
     * authenticates with OAuth2 client credentials, the client secret is resolved from the vault ({@code
     * oauth2:clientSecretKey}). The DTR shows a consumer only the twins whose {@code externalSubjectId} matches the
     * {@code Edc-Bpn} header set by the provider data plane.
     */
    private Map<String, String> dtrDataAddress() {
        var address = new LinkedHashMap<String, String>();
        address.put("type", "HttpData");
        address.put("baseUrl", dtr.publicUrl().replaceAll("/+$", ""));
        address.put("proxyPath", "true");
        address.put("proxyQueryParams", "true");
        address.put("proxyMethod", "true");
        address.put("proxyBody", "false");
        if (dtr.tokenUrl() != null && !dtr.tokenUrl().isBlank()) {
            address.put("oauth2:tokenUrl", dtr.tokenUrl());
            address.put("oauth2:clientId", dtr.edcClientId());
            if (dtr.edcClientSecret() != null && !dtr.edcClientSecret().isBlank()) {
                if (secrets.available()) {
                    secrets.store(dtr.edcClientSecretName(), dtr.edcClientSecret());
                } else {
                    LOG.warn("No vault configured: the data plane cannot resolve the DTR client secret");
                }
            }
            address.put("oauth2:clientSecretKey", dtr.edcClientSecretName());
        }
        return address;
    }

    /** DataAddress of a backend endpoint that receives the consumer's path ({@code proxyPath}), GET only. */
    private static Map<String, String> pathDataAddress(String baseUrl, Map<String, String> auth) {
        var address = new LinkedHashMap<String, String>();
        address.put("type", "HttpData");
        address.put("baseUrl", baseUrl);
        address.put("proxyMethod", "false");
        address.put("proxyBody", "false");
        address.put("proxyPath", "true");
        address.put("proxyQueryParams", "false");
        address.putAll(auth);
        return address;
    }

    /** State of the last run. */
    public ProvisioningState state() {
        return state.get();
    }

    private ProvisioningState update(ProvisioningState next) {
        state.set(next);
        return next;
    }

    /** See {@link OfferPolicies#versioned}. */
    static PolicySpec versioned(PolicySpec spec) {
        return OfferPolicies.versioned(spec);
    }

    private Map<String, String> callbackAuthentication() {
        var auth = new LinkedHashMap<String, String>();
        auth.put("authKey", edc.callbackSecretHeader());
        if (secrets.available()) {
            secrets.store(edc.callbackSecretName(), edc.callbackSecret());
            auth.put("secretName", edc.callbackSecretName());
        } else {
            LOG.warn("No vault configured: callback secret is stored as authCode in the EDC asset (fallback)");
            auth.put("authCode", edc.callbackSecret());
        }
        return auth;
    }

    private static Map<String, String> dataAddress(String baseUrl, boolean proxyRequest, Map<String, String> auth) {
        var address = new LinkedHashMap<String, String>();
        address.put("type", "HttpData");
        address.put("baseUrl", baseUrl);
        address.put("proxyMethod", String.valueOf(proxyRequest));
        address.put("proxyBody", String.valueOf(proxyRequest));
        address.put("proxyPath", "false");
        address.put("proxyQueryParams", "false");
        if (proxyRequest) {
            address.put("contentType", "application/json");
        }
        address.putAll(auth);
        return address;
    }
}
