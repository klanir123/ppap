package com.msg.ppap.masterdata.application;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.application.port.out.RelationRepository;
import com.msg.ppap.masterdata.application.port.out.SiteRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.PartnerValues;
import com.msg.ppap.platform.domain.FieldViolation;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ReferencedException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for business partners (UC-01). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class PartnerService extends MasterdataService<Partner, PartnerValues> {

    private final PartnerRepository partners;
    private final SiteRepository sites;
    private final RelationRepository relations;
    private final TenantContext tenant;

    public PartnerService(
            PartnerRepository partners,
            SiteRepository sites,
            RelationRepository relations,
            TenantContext tenant,
            MasterdataEvents events) {
        super(events);
        this.partners = partners;
        this.sites = sites;
        this.relations = relations;
        this.tenant = tenant;
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Partner get(UUID id) {
        return super.get(id);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Page<Partner> search(String text, Boolean active, Pageable pageable) {
        return partners.search(Keys.text(text), active, pageable);
    }

    public Partner create(PartnerCommand command) {
        return doCreate(command.toValues(tenant.bpnl()), command.active());
    }

    public Partner update(UUID id, PartnerCommand command, Long expectedVersion) {
        return doUpdate(id, command.toValues(tenant.bpnl()), command.active(), expectedVersion);
    }

    /** {@code PUT /partners/by-bpnl/{bpnl}}. */
    public UpsertResult<Partner> upsertByBpnl(String bpnl, PartnerCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("bpnl", bpnl, command.bpnl());
        return doUpsert(command.withBpnl(bpnl).toValues(tenant.bpnl()), command.active(), expectedVersion);
    }

    public UpsertResult<Partner> upsert(PartnerCommand command) {
        return doUpsert(command.toValues(tenant.bpnl()), command.active(), null);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PlanResult plan(PartnerCommand command) {
        return doPlan(command.toValues(tenant.bpnl()), command.active());
    }

    @Override
    protected List<FieldViolation> warnings(PartnerValues values) {
        if (values.addressable()) {
            return List.of();
        }
        return List.of(new FieldViolation(
                values.dspEndpoint() == null ? "dspEndpoint" : "did",
                ViolationCodes.NOT_EXCHANGE_READY,
                "partner cannot be used for exchange until DSP endpoint and DID are set"));
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.PARTNER;
    }

    @Override
    protected String keyField() {
        return "bpnl";
    }

    @Override
    protected Object keyOf(PartnerValues values) {
        return values.bpnl();
    }

    @Override
    protected Optional<Partner> findById(UUID id) {
        return partners.findById(id);
    }

    @Override
    protected Optional<Partner> findByKey(PartnerValues values) {
        return partners.findByBpnl(values.bpnl());
    }

    @Override
    protected Partner newAggregate(PartnerValues values, boolean active) {
        return Partner.create(values, active);
    }

    @Override
    protected Partner persist(Partner aggregate) {
        return partners.save(aggregate);
    }

    @Override
    protected void remove(Partner aggregate) {
        partners.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(Partner aggregate) {
        var bpnl = aggregate.values().bpnl();
        return sites.existsByOwner(bpnl) || relations.existsByPartner(bpnl);
    }

    @Override
    protected void checkKeyChange(Partner existing, PartnerValues newValues) {
        if (isReferenced(existing)) {
            throw new ReferencedException(
                    "BPNL of partner '" + existing.businessKey() + "' cannot change: referenced by sites or relations");
        }
    }
}
