/**
 * Cross-cutting platform concerns shared by all modules: error handling (RFC 9457), correlation ids, security
 * configuration, OpenAPI metadata and system status.
 */
@ApplicationModule(displayName = "Platform", type = ApplicationModule.Type.OPEN)
package com.msg.ppap.platform;

import org.springframework.modulith.ApplicationModule;
