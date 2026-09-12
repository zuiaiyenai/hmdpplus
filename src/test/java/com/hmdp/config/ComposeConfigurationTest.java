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
                    new java.util.HashSet<>(Arrays.asList(
                            "mysql", "redis-node-1", "redis-node-2", "redis-node-3",
                            "redis-sentinel-1", "redis-sentinel-2", "redis-sentinel-3",
                            "kafka", "app")),
                    services.keySet());
            Map<String, Object> app = (Map<String, Object>) services.get("app");
            Map<String, Object> dependsOn = (Map<String, Object>) app.get("depends_on");
            assertTrue(dependsOn.keySet().containsAll(Arrays.asList(
                    "mysql", "redis-sentinel-1", "redis-sentinel-2",
                    "redis-sentinel-3", "kafka")));
            Map<String, Object> environment = (Map<String, Object>) app.get("environment");
            assertEquals("${HMDP_SPRING_PROFILES_ACTIVE:-redis-sentinel}",
                    environment.get("SPRING_PROFILES_ACTIVE"));
            assertEquals(
                    "${HMDP_SPRING_REDIS_SENTINEL_NODES:-redis-sentinel-1:26379,redis-sentinel-2:26379,redis-sentinel-3:26379}",
                    environment.get("SPRING_REDIS_SENTINEL_NODES"));
        }
    }
}
