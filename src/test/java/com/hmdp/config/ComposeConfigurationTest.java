package com.hmdp.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComposeConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void definesApplicationAndHealthyDependencies() throws IOException {
        try (InputStream input = Files.newInputStream(Paths.get("compose.yaml"))) {
            Map<String, Object> compose = new Yaml().load(input);
            Map<String, Object> services = (Map<String, Object>) compose.get("services");
            assertEquals(
                    new java.util.HashSet<>(Arrays.asList("mysql", "redis", "kafka", "app")),
                    services.keySet());
            Map<String, Object> app = (Map<String, Object>) services.get("app");
            Map<String, Object> dependsOn = (Map<String, Object>) app.get("depends_on");
            assertTrue(dependsOn.keySet().containsAll(Arrays.asList("mysql", "redis", "kafka")));
        }
    }
}
