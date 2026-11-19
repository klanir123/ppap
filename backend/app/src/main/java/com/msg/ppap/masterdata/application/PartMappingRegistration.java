package com.msg.ppap.masterdata.application;

import com.msg.ppap.masterdata.api.PartMappingRegistry;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@link PartMappingRegistry} implementation delegating to {@link PartMappingService}. */
@Service
public class PartMappingRegistration implements PartMappingRegistry {

    private final PartMappingService mappings;

    public PartMappingRegistration(PartMappingService mappings) {
        this.mappings = mappings;
    }

    @Override
    public void registerMapping(UUID relationId, UUID partId, String partnerPartNumber) {
        mappings.create(relationId, new PartMappingCommand(partId.toString(), null, partnerPartNumber, null, null));
    }
}
