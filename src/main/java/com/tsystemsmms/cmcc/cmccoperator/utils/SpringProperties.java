/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A builder-style class to produce the appropriate value for Spring Boot property JSON.
 */
public class SpringProperties {
    // make sure the serialization is reproducible, so resources don't get recreated unnecessarily
    final static ObjectMapper objectMapper;

    static {
        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    final HashMap<String, String> properties = new HashMap<>();

    public static SpringProperties builder() {
        return new SpringProperties();
    }

    public SpringProperties property(String name, String value) {
        properties.put(name, value);
        return this;
    }

    public SpringProperties properties(Map<String, String> properties) {
        this.properties.putAll(properties);
        return this;
    }

    /**
     * Returns the properties as a JSON string.
     *
     * @return JSON
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(properties);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize properties to JSON", e);
        }
    }

    /**
     * Returns an EnvVar SPRING_APPLICATION_JSON with the properties as JSON.
     *
     * @return all properties as a variable
     */
    public EnvVar toSpringApplicationJsonEnvVar() {
        EnvVar env = new EnvVar();
        env.setName("SPRING_APPLICATION_JSON");
        env.setValue(toJson());
        return env;
    }

    /**
     * Returns the properties as a list of EnvVars, with the names converted as per Spring convention.
     * See https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#features.external-config.typesafe-configuration-properties.relaxed-binding.environment-variables
     * and https://github.com/spring-projects/spring-framework/blob/95d62658ff52837a44d88d9386841e1fa2eb8171/spring-core/src/main/java/org/springframework/core/env/SystemEnvironmentPropertySource.java
     *
     * @return list of env vars
     */
    public List<EnvVar> toEnvVars() {
        return properties.entrySet().stream().map(this::entryToEnvVar).collect(Collectors.toList());
    }

    private EnvVar entryToEnvVar(Map.Entry<String, String> e) {
        EnvVar env = new EnvVar();
        env.setName(e.getKey().replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT));
        env.setValue(e.getValue());
        return env;
    }
}
