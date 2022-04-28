/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.SpringProperties;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.HashMap;
import java.util.Map;

public abstract class SpringBootComponent extends AbstractComponent {

    public SpringBootComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
        super(kubernetesClient, targetState, componentSpec, imageRepository);
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();
        env.addAll(SpringProperties.builder().properties(getSpringBootProperties()).toEnvVars());
        return env;
    }

    /**
     * Returns Spring Boot properties as a map.
     *
     * @return properties
     */
    public Map<String, String> getSpringBootProperties() {
        return new HashMap<>(Map.of(
                "management.health.probes.enabled", "true" // enable support for k8s compatible probe endpoints
        ));
    }

    /**
     * Defines a probe suitable for the startup check.
     *
     * @return probe definition
     */
    public Probe getStartupProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(60)
                .withTimeoutSeconds(10)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/actuator/health/readiness")
                        .withPort(new IntOrString("management"))
                        .build())
                .build();
    }

    /**
     * Defines a probe suitable for the liveness check.
     *
     * @return probe definition
     */
    public Probe getLivenessProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(20)
                .withTimeoutSeconds(10)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/actuator/health/liveness")
                        .withPort(new IntOrString("management"))
                        .build())
                .build();
    }

    /**
     * Defines a probe suitable for the readiness check.
     *
     * @return probe definition
     */
    public Probe getReadinessProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(10)
                .withTimeoutSeconds(10)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/actuator/health/readiness")
                        .withPort(new IntOrString("management"))
                        .build())
                .build();
    }
}
