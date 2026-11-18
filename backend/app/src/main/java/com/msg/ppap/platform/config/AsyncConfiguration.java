package com.msg.ppap.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables asynchronous execution, used by Spring Modulith {@code @ApplicationModuleListener}s (they run after commit
 * in their own transaction; the event publication registry guarantees delivery).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {}
