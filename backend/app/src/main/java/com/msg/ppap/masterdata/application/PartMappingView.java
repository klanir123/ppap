package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.PartMapping;

/**
 * Part mapping with derived data.
 *
 * @param mapping the mapping
 * @param partNumber own part number
 * @param partNameDe German name of the own part
 */
public record PartMappingView(PartMapping mapping, String partNumber, String partNameDe) {}
