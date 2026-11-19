package com.msg.ppap.exchange.application;

import com.msg.ppap.exchange.application.port.out.AssetSpec;
import com.msg.ppap.exchange.application.port.out.CallbackSecretStore;
import com.msg.ppap.exchange.application.port.out.ContractDefinitionSpec;
import com.msg.ppap.exchange.application.port.out.DataspaceConnector;
import com.msg.ppap.exchange.application.port.out.PolicySpec;
import com.msg.ppap.exchange.config.EdcProperties;
import com.msg.ppap.exchange.config.ExchangeProperties;
import com.msg.ppap.exchange.domain.ProvisioningState;
import com.msg.ppap.masterdata.api.MasterdataQueries;
import com.msg.ppap.semantics.api.CanonicalJson;
import com.msg.ppap.semantics.api.PayloadHash;
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
    private final AtomicReference<ProvisioningState> state = new AtomicReference<>(ProvisioningState.INITIAL);
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public ProvisioningService(
            DataspaceConnector connector,
            CallbackSecretStore secrets,
            MasterdataQueries masterdata,
            EdcProperties edc,
            ExchangeProperties exchange,
            ExchangeContext context) {
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
            PolicySpec access = versioned(new PolicySpec(
                    ExchangeAssets.ACCESS_POLICY,
                    "access",
                    List.of(new PolicySpec.Constraint(
                            "BusinessPartnerGroup",
                            "isAnyOf",
                            List.of(edc.policy().partnerGroup())))));
            PolicySpec usage = versioned(new PolicySpec(
                    ExchangeAssets.USAGE_POLICY,
                    "use",
                    List.of(
                            new PolicySpec.Constraint(
                                    "FrameworkAgreement", "eq", edc.policy().frameworkAgreement()),
                            new PolicySpec.Constraint(
                                    "UsagePurpose",
                                    "isAnyOf",
                                    List.of(edc.policy().usagePurpose())))));
            connector.ensurePolicy(access);
            connector.ensurePolicy(usage);
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    ExchangeAssets.NOTIFICATION_CONTRACT, access.id(), usage.id(), ExchangeAssets.NOTIFICATION_ASSET));
            connector.ensureContractDefinition(new ContractDefinitionSpec(
                    ExchangeAssets.PUBLIC_KEYS_CONTRACT, access.id(), usage.id(), ExchangeAssets.PUBLIC_KEYS_ASSET));
            Set<String> partners = new TreeSet<>();
            masterdata.exchangePartners().forEach(p -> partners.add(p.bpnl()));
            Set<String> members =
                    connector.ensureBusinessPartnerGroup(edc.policy().partnerGroup(), partners);
            ProvisioningState done = new ProvisioningState(
                    List.of(ExchangeAssets.NOTIFICATION_ASSET, ExchangeAssets.PUBLIC_KEYS_ASSET),
                    List.copyOf(new TreeSet<>(members)),
                    true,
                    now,
                    null);
            if (!state.get().ok() || !state.get().partners().equals(done.partners())) {
                LOG.info(
                        "EDC provisioning complete: assets {}, group {} = {}",
                        done.assets(),
                        edc.policy().partnerGroup(),
                        done.partners());
            }
            return update(done);
        } catch (RuntimeException ex) {
            if (state.get().ok() || state.get().lastRun() == null) {
                LOG.warn("EDC provisioning failed (retry every {}): {}", exchange.provisioningRetry(), ex.toString());
            } else {
                LOG.debug("EDC provisioning failed again: {}", ex.toString());
            }
            return update(
                    new ProvisioningState(state.get().assets(), state.get().partners(), false, now, ex.getMessage()));
        }
    }

    /** State of the last run. */
    public ProvisioningState state() {
        return state.get();
    }

    private ProvisioningState update(ProvisioningState next) {
        state.set(next);
        return next;
    }

    /**
     * Policy with a content-addressed id ({@code <base>-<8 hex of SHA-256 over the canonical spec>}): policy
     * definitions referenced by a contract definition cannot be changed in the EDC, so a changed policy (e.g. another
     * UsagePurpose) gets a new id and the contract definitions are switched to it.
     */
    static PolicySpec versioned(PolicySpec spec) {
        String hash = PayloadHash.sha256Hex(CanonicalJson.canonicalize(Map.of(
                "action",
                spec.action(),
                "constraints",
                spec.constraints().stream()
                        .map(c -> Map.of(
                                "leftOperand", c.leftOperand(),
                                "operator", c.operator(),
                                "rightOperand", c.rightOperand()))
                        .toList())));
        return new PolicySpec(spec.id() + "-" + hash.substring(0, 8), spec.action(), spec.constraints());
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
