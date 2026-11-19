package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartMapping;
import com.msg.ppap.masterdata.domain.Partner;

/**
 * Part mapping with relation partner and own part (request wizard).
 *
 * @param partner partner of the relation ({@code null} if missing)
 * @param part own part ({@code null} if missing)
 */
public record PartMappingSearchView(PartMapping mapping, String partnerBpnl, Partner partner, Part part) {}
