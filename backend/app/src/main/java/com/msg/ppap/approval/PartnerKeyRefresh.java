package com.msg.ppap.approval;

/**
 * Extension point: fetches the public signature keys of a partner again (via the dataspace, asset {@code
 * ppap-public-keys}) before a manual signature verification. Implemented by the {@code exchange} module.
 */
public interface PartnerKeyRefresh {

    /**
     * Fetches the partner's keys.
     *
     * @return {@code true} if at least one key was fetched
     */
    boolean refresh(String partnerBpnl);
}
