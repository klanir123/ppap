package com.msg.ppap.exchange.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the exchange jobs (outbox worker, inbox recovery, provisioning, heartbeat). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "ppap.exchange.scheduling-enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {}
