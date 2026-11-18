package com.msg.ppap.masterdata.adapter.out.jpa;

import com.msg.ppap.masterdata.domain.Part;
import com.msg.ppap.masterdata.domain.PartNumber;
import com.msg.ppap.masterdata.domain.PartValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "md_part")
class PartEntity extends MasterdataEntity {

    @Column(name = "part_number", nullable = false)
    private String partNumber;

    @Column(name = "name_de", nullable = false)
    private String nameDe;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "drawing_number")
    private String drawingNumber;

    @Column(name = "drawing_revision")
    private String drawingRevision;

    @Column(name = "material")
    private String material;

    @Column(name = "catena_x_id")
    private String catenaXId;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected PartEntity() {}

    PartEntity(UUID id, String tenantId) {
        super(id, tenantId);
    }

    void apply(Part part) {
        PartValues v = part.values();
        partNumber = v.partNumber().value();
        nameDe = v.nameDe();
        nameEn = v.nameEn();
        drawingNumber = v.drawingNumber() == null ? null : v.drawingNumber().value();
        drawingRevision = v.drawingRevision();
        material = v.material();
        catenaXId = v.catenaXId();
        active = part.active();
    }

    Part toDomain() {
        return Part.restore(
                getId(),
                new PartValues(
                        new PartNumber(partNumber),
                        nameDe,
                        nameEn,
                        drawingNumber == null ? null : new PartNumber(drawingNumber),
                        drawingRevision,
                        material,
                        catenaXId),
                active,
                metadata());
    }
}
