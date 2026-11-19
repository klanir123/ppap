package com.msg.ppap.approval.application.port.out;

/** Tenant-local case numbers {@code PPF-<year>-<5 digits>}, concurrency-safe and gap-free per transaction. */
public interface CaseNumberGenerator {

    /** Next number of the year. */
    String next(int year);

    /** Makes sure the counter of the number's year is not below the given (e.g. seeded) number. */
    void reserve(String caseNumber);
}
