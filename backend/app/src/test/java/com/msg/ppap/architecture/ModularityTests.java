package com.msg.ppap.architecture;

import com.msg.ppap.PpapConnectorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Verifies the Spring Modulith module structure and renders module documentation (C4/PlantUML) into target. */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(PpapConnectorApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void writesDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
