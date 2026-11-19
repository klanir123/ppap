/**
 * Own Digital Twin Registry (phase 5): registration of the twin per approval case and its submodel descriptors
 * (local outbox task {@code TWIN_REGISTRATION}), DTR client and diagnosis endpoint. The customer-side pull runs in the
 * exchange module, the AAS formats live in the semantics module.
 */
@ApplicationModule(displayName = "Digital Twin")
package com.msg.ppap.twin;

import org.springframework.modulith.ApplicationModule;
