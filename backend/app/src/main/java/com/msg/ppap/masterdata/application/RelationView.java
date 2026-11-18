package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.Relation;

/**
 * Relation with derived data.
 *
 * @param relation the relation
 * @param partnerName partner name
 * @param mappingCount number of part mappings
 */
public record RelationView(Relation relation, String partnerName, long mappingCount) {}
