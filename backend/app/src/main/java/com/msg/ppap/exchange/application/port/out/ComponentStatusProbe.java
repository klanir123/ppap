package com.msg.ppap.exchange.application.port.out;

/** Health status of an infrastructure component of the own participant (e.g. the DTR), for the cockpit overview. */
public interface ComponentStatusProbe {

    /**
     * Status of the component.
     *
     * @return {@code UP}, {@code DOWN}, {@code UNKNOWN} (not configured) or {@code null} if there is no such component
     */
    String status(String component);
}
