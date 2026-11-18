package com.msg.ppap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the msg CX PPAP Connector backend (modular monolith, see docs/ARCHITECTURE.md §5). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PpapConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PpapConnectorApplication.class, args);
    }
}
