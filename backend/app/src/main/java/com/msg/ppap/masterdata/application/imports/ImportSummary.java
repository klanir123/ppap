package com.msg.ppap.masterdata.application.imports;

/** Counts over all rows of an import preview. */
public record ImportSummary(int total, int ok, int warnings, int errors, int create, int update, int unchanged) {}
