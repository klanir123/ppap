package com.msg.ppap.platform.web;

import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * Allows URL-encoded slashes ({@code %2F}) inside path segments. Business keys used in upsert paths may contain
 * {@code /} (e.g. part numbers {@code PUT /api/v1/parts/by-number/4711%2F01}); Spring MVC decodes path variables
 * per segment, so the encoded slash never acts as a path separator.
 */
@Configuration(proxyBeanMethods = false)
public class EncodedSlashConfiguration {

    @Bean
    TomcatConnectorCustomizer encodedSolidusPassthrough() {
        return connector -> connector.setEncodedSolidusHandling("passthrough");
    }

    @Bean
    HttpFirewall httpFirewall() {
        var firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedSlash(true);
        return firewall;
    }
}
