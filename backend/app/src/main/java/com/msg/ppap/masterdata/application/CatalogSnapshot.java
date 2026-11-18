package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.domain.CatalogElement;
import com.msg.ppap.masterdata.domain.SubmissionLevel;
import com.msg.ppap.masterdata.domain.SubmissionReason;
import java.util.List;

/** Complete submission catalog of the tenant (export). */
public record CatalogSnapshot(
        List<CatalogElement> elements, List<SubmissionLevel> levels, List<SubmissionReason> reasons) {}
