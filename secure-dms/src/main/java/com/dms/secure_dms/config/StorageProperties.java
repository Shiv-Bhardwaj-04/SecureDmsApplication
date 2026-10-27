package com.dms.secure_dms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dms.storage")
public class StorageProperties {

    /** Directory for encrypted document blobs and local development key material. */
    private String root = "./data/secure-dms";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}
