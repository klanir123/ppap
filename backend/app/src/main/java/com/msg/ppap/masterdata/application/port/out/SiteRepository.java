package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.Site;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.Bpns;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of sites; all operations are scoped to the current tenant. */
public interface SiteRepository {

    Optional<Site> findById(UUID id);

    Optional<Site> findByBpns(Bpns bpns);

    Page<Site> search(String text, Boolean active, Bpnl ownerBpnl, Pageable pageable);

    boolean existsByOwner(Bpnl ownerBpnl);

    Site save(Site site);

    void delete(Site site);
}
