package com.msg.ppap.identity;

import java.util.Map;

/** Public signature keys of the own tenant (for partners: JWKS via the dataspace and {@code /.well-known}). */
public interface PublicKeys {

    /** Public JWK set {@code {"keys":[…]}}. */
    Map<String, Object> publicJwkSet();
}
