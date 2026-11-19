package com.msg.ppap.exchange.application.port.out;

import java.nio.charset.StandardCharsets;

/** Response of a data plane call. */
public record DataPlaneResponse(int status, byte[] body) {

    public DataPlaneResponse {
        body = body == null ? new byte[0] : body.clone();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    public boolean successful() {
        return status >= 200 && status < 300;
    }

    /** Body as text, shortened for diagnostics. */
    public String bodySnippet() {
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
