package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of parts; all operations are scoped to the current tenant. */
public interface PartRepository {

    Optional<Part> findById(UUID id);

    Optional<Part> findByPartNumber(PartNumber partNumber);

    List<Part> findAllById(Collection<UUID> ids);

    /**
     * Searches parts.
     *
     * @param text full-text filter (part number, names, drawing number, material) or {@code null}
     * @param active active filter or {@code null}
     * @param partnerBpnl only parts mapped to this partner, or {@code null}
     */
    Page<Part> search(String text, Boolean active, Bpnl partnerBpnl, Pageable pageable);

    /** Saves and flushes; returns the persisted state including the new version. */
    Part save(Part part);

    void delete(Part part);
}
