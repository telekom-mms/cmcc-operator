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

import java.util.*;

public abstract class SpringBootComponent extends AbstractComponent {

  public SpringBootComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
    super(kubernetesClient, targetState, componentSpec, imageRepository);
  }

  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = super.getEnvVars();
    env.addAll(SpringProperties.builder().properties(getSpringBootProperties()).toEnvVars());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      env.add(new EnvVarBuilder().withName("LOGGING_CONFIG").withValue("/coremedia/logging-config.xml").build());
    }
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

  @Override
  public List<Volume> getVolumes() {
    LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      volumes.add(new VolumeBuilder()
              .withName("logging-config")
              .withConfigMap(new ConfigMapVolumeSourceBuilder()
                      .withName("logging-config")
                      .withOptional()
                      .build())
              .build());
    }
    return volumes;
  }

  @Override
  public List<VolumeMount> getVolumeMounts() {
    LinkedList<VolumeMount> volumes = new LinkedList<>(super.getVolumeMounts());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      volumes.add(new VolumeMountBuilder()
              .withName("logging-config")
              .withMountPath("/coremedia/logging-config.xml")
              .withSubPath("logback-spring.xml")
              .build());
    }
    return volumes;
  }

  /**
   * If uploadSize is larger than 0, add the appropriate properties to configure the upload size.
   *
   * @param properties spring boot properties to set
   * @param uploadSize in megabyte
   */
  public static void addUploadSizeProperties(Map<String, String> properties, int uploadSize) {
    if (uploadSize > 0) {
      String mb = uploadSize + "MB";
      String b = String.valueOf(((long) uploadSize) * 1024 * 1024);
      properties.putAll(Map.of(
              "spring.servlet.multipart.max-file-size", mb,
              "spring.servlet.multipart.max-request-size", mb,
              "server.tomcat.max-http-form-post-size", b,
              "server.tomcat.max-swallow-size", b
      ));
    }
  }

  /**
   * Defines a probe suitable for the startup check.
   *
   * @return probe definition
   */
  public Probe getStartupProbe() {
    var interval = 10;
    var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getStartup()).orElse(600);
    return new ProbeBuilder()
            .withPeriodSeconds(interval)
            .withTimeoutSeconds(interval)
            .withFailureThreshold(timeout / interval)
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
    var interval = 10;
    var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getLive()).orElse(200);
    return new ProbeBuilder()
            .withPeriodSeconds(interval)
            .withTimeoutSeconds(interval)
            .withFailureThreshold(timeout / interval)
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
    var interval = 5;
    var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getReady()).orElse(100);
    return new ProbeBuilder()
            .withPeriodSeconds(interval)
            .withTimeoutSeconds(interval)
            .withFailureThreshold(timeout / interval)
            .withHttpGet(new HTTPGetActionBuilder()
                    .withPath("/actuator/health/readiness")
                    .withPort(new IntOrString("management"))
                    .build())
            .build();
  }
}
