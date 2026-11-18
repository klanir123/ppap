package com.msg.ppap.masterdata.application.imports;

import java.util.List;

/**
 * A row that could not be imported.
 *
 * @param rowNumber row number (header = 1)
 * @param messages reasons
 */
public record ImportFailure(int rowNumber, List<ImportMessage> messages) {}
