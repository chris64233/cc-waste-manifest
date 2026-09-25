package com.chris64233.cc.wastemanifest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CcWasteManifestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcWasteManifestApplication.class, args);
    }
}
