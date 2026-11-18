package com.msg.ppap.masterdata.application.port.out;

import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.platform.domain.Bpnl;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Persistence of partners; all operations are scoped to the current tenant. */
public interface PartnerRepository {

    Optional<Partner> findById(UUID id);

    Optional<Partner> findByBpnl(Bpnl bpnl);

    List<Partner> findAllByBpnl(Collection<Bpnl> bpnls);

    Page<Partner> search(String text, Boolean active, Pageable pageable);

    Partner save(Partner partner);

    void delete(Partner partner);
}
