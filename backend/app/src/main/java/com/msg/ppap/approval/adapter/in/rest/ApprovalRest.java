package com.msg.ppap.approval.adapter.in.rest;

import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.VersionConflictException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

/** REST conventions of the approval endpoints (paging, ETag = case version, If-Match), as in master data. */
final class ApprovalRest {

    /** Customer side write roles. */
    static final String CUSTOMER = "hasAnyRole('CUSTOMER_QUALITY_ENGINEER', 'SYSTEM_INTEGRATION')";

    /** Supplier side write roles. */
    static final String SUPPLIER = "hasAnyRole('SUPPLIER_QUALITY_MANAGER', 'SYSTEM_INTEGRATION')";

    /** Roles that act on cases of either side (exact check by the state machine). */
    static final String ANY_QUALITY =
            "hasAnyRole('CUSTOMER_QUALITY_ENGINEER', 'SUPPLIER_QUALITY_MANAGER', 'SYSTEM_INTEGRATION')";

    static final int MAX_PAGE_SIZE = 200;

    private ApprovalRest() {}

    static Pageable pageable(int page, int size, List<String> sort, Set<String> allowed, Sort defaultSort) {
        if (page < 0) {
            throw ValidationException.of("page", ViolationCodes.INVALID_VALUE, "must be >= 0");
        }
        return PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE), sort(sort, allowed, defaultSort));
    }

    static Sort sort(List<String> sort, Set<String> allowed, Sort defaultSort) {
        if (sort == null || sort.isEmpty()) {
            return defaultSort;
        }
        List<String> tokens = new ArrayList<>();
        for (String entry : sort) {
            for (String token : entry.split(",")) {
                if (!token.isBlank()) {
                    tokens.add(token.strip());
                }
            }
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String field = tokens.get(i);
            if (!allowed.contains(field)) {
                throw ValidationException.of("sort", ViolationCodes.INVALID_VALUE, "cannot sort by '" + field + "'");
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (i + 1 < tokens.size()) {
                String next = tokens.get(i + 1).toLowerCase(Locale.ROOT);
                if (next.equals("asc") || next.equals("desc")) {
                    direction = Sort.Direction.fromString(next);
                    i++;
                }
            }
            orders.add(new Sort.Order(direction, field));
        }
        return Sort.by(orders).and(Sort.by("id"));
    }

    /** Parses {@code If-Match} ({@code "3"}, {@code W/"3"}, {@code 3}; {@code *} or absent = no check). */
    static Long expectedVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.split(",")[0].strip();
        if (value.equals("*")) {
            return null;
        }
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        try {
            return Long.parseLong(value.replace("\"", ""));
        } catch (NumberFormatException ex) {
            throw new VersionConflictException("If-Match header '" + ifMatch + "' does not match any version");
        }
    }

    static <T> ResponseEntity<T> ok(T body, Long version) {
        return ResponseEntity.ok().eTag(etag(version)).body(body);
    }

    static String etag(Long version) {
        return "\"" + (version == null ? 0 : version) + "\"";
    }
}
