package com.ecolchain.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConfigGuardTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    private static Properties load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            p.load(in);
        }
        return p;
    }

    private static String resolve(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null) {
            fail("missing property " + key);
        }
        Matcher m = PLACEHOLDER.matcher(v);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(p.getProperty(m.group(1), "")));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @Test
    void devAndProdProfilesAreSeparated() throws IOException {
        Properties p = load();
        assertEquals("MGMT_DEV", p.getProperty("%dev.quarkus.datasource.username"));
        assertEquals("MGMT_PROD", p.getProperty("%prod.quarkus.datasource.username"));
        assertEquals("ecolchain-bucket-docs-dev", p.getProperty("%dev.app.docs.bucket"));
        assertEquals("ecolchain-bucket-docs-prod", p.getProperty("%prod.app.docs.bucket"));
    }

    @Test
    void jdbcUrlIsSharedAndPointsToAdbTls1521() throws IOException {
        Properties p = load();
        String devUrl = resolve(p, "%dev.quarkus.datasource.jdbc.url");
        String prodUrl = resolve(p, "%prod.quarkus.datasource.jdbc.url");
        assertEquals(devUrl, prodUrl);
        assertTrue(devUrl.contains("port=1521"), devUrl);
        assertTrue(devUrl.contains("ecolmgmt_tp"), devUrl);
    }

    @Test
    void devAndProdPasswordsDiffer() throws IOException {
        Properties p = load();
        String dev = p.getProperty("%dev.quarkus.datasource.password");
        String prod = p.getProperty("%prod.quarkus.datasource.password");
        assertTrue(dev != null && !dev.isBlank(), "dev password missing");
        assertTrue(prod != null && !prod.isBlank(), "prod password missing");
        assertNotEquals(dev, prod);
        assertFalse(p.containsKey("quarkus.datasource.password"),
                "unprofiled quarkus.datasource.password must not exist");
    }

    @Test
    void s3CredentialsOnlyUnderTestProfile() throws IOException {
        Properties p = load();
        for (String name : p.stringPropertyNames()) {
            if (name.contains("access-key") || name.contains("secret")) {
                assertTrue(name.startsWith("%test."),
                        "credential property outside %test: " + name);
            }
        }
    }
}
