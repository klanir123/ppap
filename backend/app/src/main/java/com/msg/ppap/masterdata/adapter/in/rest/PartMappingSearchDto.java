package com.msg.ppap.masterdata.adapter.in.rest;

import com.msg.ppap.masterdata.application.PartMappingSearchView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Part mapping across relations (request wizard). */
public record PartMappingSearchDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID mappingId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID relationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Partner partner,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Part part,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String partnerPartNumber) {

    /** Partner of the relation. */
    @Schema(name = "PartMappingSearchPartnerDto")
    public record Partner(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String bpnl,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            boolean exchangeReady) {}

    /** Own part. */
    @Schema(name = "PartMappingSearchPartDto")
    public record Part(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            UUID id,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String partNumber,

            String nameDe,
            String nameEn) {}

    static PartMappingSearchDto from(PartMappingSearchView view) {
        var m = view.mapping().values();
        var partner = view.partner();
        var part = view.part();
        return new PartMappingSearchDto(
                view.mapping().id(),
                m.relationId(),
                new Partner(
                        view.partnerBpnl(),
                        partner == null ? view.partnerBpnl() : partner.values().name(),
                        partner != null && partner.exchangeReady()),
                part == null
                        ? new Part(m.partId(), null, null, null)
                        : new Part(
                                part.id(),
                                part.values().partNumber().value(),
                                part.values().nameDe(),
                                part.values().nameEn()),
                m.partnerPartNumber().value());
    }
}
