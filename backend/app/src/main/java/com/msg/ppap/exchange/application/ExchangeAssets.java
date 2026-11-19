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

    /** Backend paths the own data plane calls. */
    public static final String NOTIFICATIONS_PATH = "/internal/edc/notifications";

    public static final String JWKS_PATH = "/internal/edc/jwks";

    private ExchangeAssets() {}
}
