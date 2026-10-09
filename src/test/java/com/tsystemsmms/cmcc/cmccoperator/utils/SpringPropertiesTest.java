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

import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpringPropertiesTest {

    @Test
    public void emptyJson() {
        assertEquals("{}", SpringProperties.builder().toJson());
    }

    @Test
    public void oneJson() {
        assertEquals("{\"foo\":\"bar\"}", SpringProperties.builder().property("foo", "bar").toJson());
    }

    @Test
    public void twoJson() {
        assertEquals("{\"a\":\"1\",\"b\":\"2\"}", SpringProperties.builder().property("a", "1").property("b", "2").toJson());
    }

    @Test
    public void emptyEnv() {
        List<EnvVar> env = SpringProperties.builder()
                .toEnvVars();
        assertEquals(0,env.size());
    }

    @Test
    public void oneEnv() {
        List<EnvVar> env = SpringProperties
                .builder()
                .property("hierarchical.value[1].withIndex", "bar")
                .toEnvVars();
        assertEquals(1,env.size());
        assertEquals("HIERARCHICAL_VALUE_1_WITHINDEX", env.get(0).getName());
        assertEquals("bar", env.get(0).getValue());
    }

    @Test
    public void twoEnv() {
        List<EnvVar> env = SpringProperties
                .builder()
                .property("a", "1")
                .property("b", "2")
                .toEnvVars();
        assertEquals(2,env.size());
        assertEquals("A", env.get(0).getName());
        assertEquals("1", env.get(0).getValue());
        assertEquals("B", env.get(1).getName());
        assertEquals("2", env.get(1).getValue());
    }

    @Test
    public void dashesAreRemovedInEnvVarNames() {
        List<EnvVar> env = SpringProperties
                .builder()
                .property("repository.heap-cache-size", "134217728")
                .toEnvVars();
        assertEquals(1, env.size());
        assertEquals("REPOSITORY_HEAPCACHESIZE", env.get(0).getName());
        assertEquals("134217728", env.get(0).getValue());
    }

    @Test
    public void indexedPropertiesRemainValidEnvVarNames() {
        List<EnvVar> env = SpringProperties
                .builder()
                .property("cae.preview.pbe.studio-url-whitelist[0]", "https://studio.example.com")
                .toEnvVars();
        assertEquals(1, env.size());
        assertEquals("CAE_PREVIEW_PBE_STUDIOURLWHITELIST_0", env.get(0).getName());
    }

    @Test
    public void canonicalEnvVarBindsToDashedProperty() {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> vars = new HashMap<>();
        vars.put("REPOSITORY_HEAPCACHESIZE", "2");
        environment.getPropertySources().replace("systemEnvironment",
                new SystemEnvironmentPropertySource("systemEnvironment", vars));
        ConfigurationPropertySources.attach(environment);
        assertEquals("2", environment.getProperty("repository.heap-cache-size"));
    }
}