package com.msg.ppap.masterdata.api;

import java.util.UUID;

/**
 * Extension point: downstream modules report whether they reference master data. Master data objects that are
 * referenced are never deleted physically, only deactivated (codes and keys can then not change either).
 *
 * <p>Implemented by e.g. the {@code approval} module (cases reference parts, partners and catalogue entries by key);
 * master data only knows this interface, so there is no dependency cycle.
 */
public interface MasterdataUsage {

    boolean isPartUsed(UUID partId);

    boolean isPartnerUsed(String bpnl);

    boolean isRelationUsed(UUID relationId);

    boolean isElementUsed(String code);

    boolean isLevelUsed(String code);

    boolean isReasonUsed(String code);
}
