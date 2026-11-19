package com.msg.ppap.exchange.application.port.out;

import com.msg.ppap.exchange.domain.StepName;
import com.msg.ppap.exchange.domain.StepStatus;
import java.time.Duration;
import java.util.Map;

/** Receives the single steps of a dataspace operation as they happen. */
@FunctionalInterface
public interface StepListener {

    StepListener NONE = (step, status, duration, details) -> {};

    void onStep(StepName step, StepStatus status, Duration duration, Map<String, Object> details);
}
