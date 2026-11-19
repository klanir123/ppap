/**
 * Public API of the Exchange module for downstream modules (phase 5): extension point for local outbox tasks that
 * must run in order with the partner messages of an exchange id ({@link
 * com.msg.ppap.exchange.api.OutboxTaskHandler}, implemented by the {@code twin} module for the DTR registration).
 */
@NamedInterface("api")
package com.msg.ppap.exchange.api;

import org.springframework.modulith.NamedInterface;
