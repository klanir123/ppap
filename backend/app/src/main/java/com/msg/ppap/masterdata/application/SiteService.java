package com.msg.ppap.masterdata.application;

import com.msg.ppap.identity.TenantContext;
import com.msg.ppap.masterdata.application.port.out.PartMappingRepository;
import com.msg.ppap.masterdata.application.port.out.PartnerRepository;
import com.msg.ppap.masterdata.application.port.out.SiteRepository;
import com.msg.ppap.masterdata.domain.MasterdataType;
import com.msg.ppap.masterdata.domain.Partner;
import com.msg.ppap.masterdata.domain.Site;
import com.msg.ppap.masterdata.domain.SiteValues;
import com.msg.ppap.platform.domain.Bpnl;
import com.msg.ppap.platform.domain.PpapException;
import com.msg.ppap.platform.domain.ReferencedException;
import com.msg.ppap.platform.domain.ValidationException;
import com.msg.ppap.platform.domain.ViolationCodes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for sites of the tenant and its partners (UC-01). */
@Service
// Business exceptions are raised before any write; they must not doom an enclosing import transaction.
@Transactional(noRollbackFor = PpapException.class)
public class SiteService extends MasterdataService<Site, SiteValues> {

    private final SiteRepository sites;
    private final PartnerRepository partners;
    private final PartMappingRepository mappings;
    private final TenantContext tenant;

    public SiteService(
            SiteRepository sites,
            PartnerRepository partners,
            PartMappingRepository mappings,
            TenantContext tenant,
            MasterdataEvents events) {
        super(events);
        this.sites = sites;
        this.partners = partners;
        this.mappings = mappings;
        this.tenant = tenant;
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public SiteView view(UUID id) {
        return views(List.of(get(id))).getFirst();
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public Page<SiteView> search(String text, Boolean active, String ownerBpnl, Pageable pageable) {
        Page<Site> page = sites.search(Keys.text(text), active, Keys.optionalBpnl("ownerBpnl", ownerBpnl), pageable);
        return new PageImpl<>(views(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    public SiteView create(SiteCommand command) {
        return view(doCreate(toValues(command), command.active()));
    }

    public SiteView update(UUID id, SiteCommand command, Long expectedVersion) {
        return view(doUpdate(id, toValues(command), command.active(), expectedVersion));
    }

    /** {@code PUT /sites/by-bpns/{bpns}}. */
    public UpsertResult<SiteView> upsertByBpns(String bpns, SiteCommand command, Long expectedVersion) {
        Keys.checkKeyMatches("bpns", bpns, command.bpns());
        return doUpsert(toValues(command.withBpns(bpns)), command.active(), expectedVersion)
                .map(this::view);
    }

    public UpsertResult<Site> upsert(SiteCommand command) {
        return doUpsert(toValues(command), command.active(), null);
    }

    @Transactional(readOnly = true, noRollbackFor = PpapException.class)
    public PlanResult plan(SiteCommand command) {
        return doPlan(toValues(command), command.active());
    }

    private SiteValues toValues(SiteCommand command) {
        SiteValues values = command.toValues();
        Bpnl owner = values.ownerBpnl();
        if (!tenant.isOwnTenant(owner) && partners.findByBpnl(owner).isEmpty()) {
            throw ValidationException.of(
                    "ownerBpnl", ViolationCodes.UNKNOWN_REFERENCE, "must be the own tenant or a known partner");
        }
        return values;
    }

    private SiteView view(Site site) {
        return views(List.of(site)).getFirst();
    }

    private List<SiteView> views(List<Site> list) {
        Map<Bpnl, String> names = new HashMap<>();
        names.put(tenant.bpnl(), tenant.tenantName());
        partners.findAllByBpnl(list.stream()
                        .map(site -> site.values().ownerBpnl())
                        .filter(bpnl -> !tenant.isOwnTenant(bpnl))
                        .collect(Collectors.toSet()))
                .forEach((Partner partner) ->
                        names.put(partner.values().bpnl(), partner.values().name()));
        return list.stream()
                .map(site -> new SiteView(site, names.get(site.values().ownerBpnl())))
                .toList();
    }

    @Override
    protected MasterdataType type() {
        return MasterdataType.SITE;
    }

    @Override
    protected String keyField() {
        return "bpns";
    }

    @Override
    protected Object keyOf(SiteValues values) {
        return values.bpns();
    }

    @Override
    protected Optional<Site> findById(UUID id) {
        return sites.findById(id);
    }

    @Override
    protected Optional<Site> findByKey(SiteValues values) {
        return sites.findByBpns(values.bpns());
    }

    @Override
    protected Site newAggregate(SiteValues values, boolean active) {
        return Site.create(values, active);
    }

    @Override
    protected Site persist(Site aggregate) {
        return sites.save(aggregate);
    }

    @Override
    protected void remove(Site aggregate) {
        sites.delete(aggregate);
    }

    @Override
    protected boolean isReferenced(Site aggregate) {
        return mappings.existsBySite(aggregate.values().bpns());
    }

    @Override
    protected void checkKeyChange(Site existing, SiteValues newValues) {
        if (isReferenced(existing)) {
            throw new ReferencedException(
                    "BPNS of site '" + existing.businessKey() + "' cannot change: referenced by part mappings");
        }
    }
}
