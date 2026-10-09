package com.ecolchain.api;

import io.smallrye.config.ConfigMapping;
import java.time.Duration;

@ConfigMapping(prefix = "app.docs")
public interface DocumentsConfig {
    String bucket();

    Duration uploadUrlTtl();

    Duration downloadUrlTtl();

    long maxSizeBytes();
}
