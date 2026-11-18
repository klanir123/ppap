package com.msg.ppap.masterdata.application.imports;

import com.msg.ppap.platform.domain.PpapException;

/** Import aborted because rows failed and {@code skipErrors} was not set; nothing was imported. */
public class ImportFailedException extends PpapException {

    public static final String CODE = "IMPORT_FAILED";

    private final transient ImportResult result;

    public ImportFailedException(ImportResult result) {
        super(Kind.UNPROCESSABLE, CODE, result.failed() + " row(s) failed, nothing was imported");
        this.result = result;
    }

    /** Report with the failed rows. */
    public ImportResult result() {
        return result;
    }
}
