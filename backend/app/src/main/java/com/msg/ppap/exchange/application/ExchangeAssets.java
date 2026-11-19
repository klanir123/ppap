package com.msg.ppap.exchange.application;

/** Deterministic ids of the provisioned EDC objects (docs/api/exchange-api.md, „Angebotene Assets“). */
public final class ExchangeAssets {

    public static final String NOTIFICATION_ASSET = "ppap-notification-receive";
    public static final String PUBLIC_KEYS_ASSET = "ppap-public-keys";
    public static final String NOTIFICATION_TYPE = "cx-taxo:PpapNotificationAPI";
    public static final String PUBLIC_KEYS_TYPE = "cx-taxo:PpapPublicKeys";
    public static final String ASSET_VERSION = "0.1";
    public static final String ACCESS_POLICY = "ppap-access-partners";
    public static final String USAGE_POLICY = "ppap-usage";
    public static final String NOTIFICATION_CONTRACT = "ppap-notification-cd";
    public static final String PUBLIC_KEYS_CONTRACT = "ppap-public-keys-cd";

    /** Phase 5: own Digital Twin Registry (supplier). */
    public static final String DTR_ASSET = "ppap-dtr";

    public static final String DTR_TYPE = "cx-taxo:DigitalTwinRegistry";
    public static final String DTR_VERSION = "3.0";
    public static final String DTR_CONTRACT = "ppap-dtr-cd";

    /** Phase 5: submodel endpoint per customer ({@code ppap-submission-<BPNL>}). */
    public static final String SUBMISSION_PREFIX = "ppap-submission-";

    public static final String SUBMODEL_TYPE = "cx-taxo:Submodel";

    /** Phase 5: documents per partner ({@code ppap-documents-<BPNL>}). */
    public static final String DOCUMENTS_PREFIX = "ppap-documents-";

    public static final String DOCUMENTS_TYPE = "cx-taxo:PpapDocuments";

    /** Access policy of the per-partner assets ({@code BusinessPartnerNumber}). */
    public static final String BPN_ACCESS_POLICY_PREFIX = "ppap-access-bpn-";

    public static final String CONTRACT_SUFFIX = "-cd";

    public static final String SUBMODELS_PATH = "/internal/edc/submodels";
    public static final String DOCUMENTS_PATH = "/internal/edc/documents";

    /** Asset of the submodel endpoint offered to a customer. */
    public static String submissionAsset(String customerBpnl) {
        return SUBMISSION_PREFIX + customerBpnl;
    }

    /** Asset of the documents offered to a partner. */
    public static String documentsAsset(String partnerBpnl) {
        return DOCUMENTS_PREFIX + partnerBpnl;
    }

    /** Backend paths the own data plane calls. */
    public static final String NOTIFICATIONS_PATH = "/internal/edc/notifications";

    public static final String JWKS_PATH = "/internal/edc/jwks";

    private ExchangeAssets() {}
}
