package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.VersionConflictException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Conventions shared by the master data controllers: paging, sorting, ETag and {@code If-Match}. */
final class RestSupport {

    /** Spring Security expression for write access. */
    static final String WRITE = "hasAnyRole('MASTERDATA_MANAGER', 'SYSTEM_INTEGRATION')";

    /** Write access to partners (additionally connector admins, for DSP endpoint and DID). */
    static final String WRITE_PARTNERS = "hasAnyRole('MASTERDATA_MANAGER', 'SYSTEM_INTEGRATION', 'CONNECTOR_ADMIN')";

    static final int DEFAULT_PAGE_SIZE = 25;
    static final int MAX_PAGE_SIZE = 200;

    private RestSupport() {}

    /**
     * Builds a page request.
     *
     * @param sort {@code field,asc|desc} entries; fields must be in {@code allowed}
     */
    static Pageable pageable(int page, int size, List<String> sort, Set<String> allowed, Sort defaultSort) {
        if (page < 0) {
            throw ValidationException.of("page", ViolationCodes.INVALID_VALUE, "must be >= 0");
        }
        int boundedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        return PageRequest.of(page, boundedSize, sort(sort, allowed, defaultSort));
    }

    static Sort sort(List<String> sort, Set<String> allowed, Sort defaultSort) {
        if (sort == null || sort.isEmpty()) {
            return defaultSort;
        }
        List<Sort.Order> orders = new ArrayList<>();
        // "sort=name,asc" arrives as ["name", "asc"] when bound to a list; re-join pairs
        List<String> tokens = new ArrayList<>();
        for (String entry : sort) {
            for (String token : entry.split(",")) {
                if (!token.isBlank()) {
                    tokens.add(token.strip());
                }
            }
        }
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
        return Sort.by(orders);
    }

    /**
     * Parses {@code If-Match}: {@code "3"}, {@code W/"3"} or {@code 3}; {@code *} or absent means no check.
     *
     * @throws VersionConflictException for unparsable values
     */
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
        value = value.replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new VersionConflictException("If-Match header '" + ifMatch + "' does not match any version");
        }
    }

    /** 200 with ETag. */
    static <T> ResponseEntity<T> ok(T body, Long version) {
        return ResponseEntity.ok().eTag(etag(version)).body(body);
    }

    /** 201 with Location and ETag. */
    static <T> ResponseEntity<T> created(String path, UUID id, T body, Long version) {
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path + "/{id}")
                .buildAndExpand(id)
                .toUri();
        return ResponseEntity.created(location).eTag(etag(version)).body(body);
    }

    /** 200 or 201 for an upsert. */
    static <T> ResponseEntity<T> upserted(boolean created, String path, UUID id, T body, Long version) {
        return created ? created(path, id, body, version) : ok(body, version);
    }

    static ResponseEntity<Void> noContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    static String etag(Long version) {
        return "\"" + (version == null ? 0 : version) + "\"";
    }
}
