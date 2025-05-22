/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.generic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentState;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ResourceMgmt;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SERVER_FOLLOWER;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class LegacySolrComponent extends AbstractComponent implements HasService {
  public static final String SOLR_PERSISTENT_STORAGE = "solr-persistent-storage";
  public static final String SOLR_REPLICAS = "replicas";
  public static final String SOLR_CORES_TO_REPLICATE = "coresToReplicate";
  public static final String SOLR_LEADER_COMPONENT = "leader";
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  /**
   * Cores that the Operator should create in the followers. The map is modifyable so additional entries can be added
   * through the extra entry on the solr component.
   */
  private final HashMap<String, String> coresToReplicate = new HashMap<>(Map.of(
          "live", "cae"
  ));

  private int replicas = 1;

  public LegacySolrComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, "solr");
    if (!componentSpec.getKind().isBlank())
      throw new CustomResourceConfigError("Invalid specification \"kind\" for \"solr\": there are no different kinds.");
    if (componentSpec.getExtra().containsKey(SOLR_REPLICAS))
      replicas = Integer.parseInt(componentSpec.getExtra().get(SOLR_REPLICAS));
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    super.updateComponentSpec(newCs);
    if (newCs.getExtra().containsKey(SOLR_REPLICAS))
      replicas = Integer.parseInt(newCs.getExtra().get(SOLR_REPLICAS));
    if (newCs.getExtra().containsKey(SOLR_CORES_TO_REPLICATE))
      coresToReplicate.putAll(getTargetState().getYamlMapper().load(newCs.getExtra().get(SOLR_CORES_TO_REPLICATE), new TypeReference<HashMap<String, String>>() {
              },
              () -> "Unable to read \"" + SOLR_CORES_TO_REPLICATE + "\" as a map in component \"" + newCs.getName() + "\""));
    return this;
  }

  @Override
  public List<HasMetadata> buildResources() {
    List<HasMetadata> resources = new LinkedList<>();

    if (replicas < 1) {
      throw new CustomResourceConfigError("component solr: extra.replicas must be 1 or higher, not " + replicas);
    }

    resources.add(buildService());
    resources.add(buildServiceLeader());
    resources.add(buildStatefulSetLeader());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      resources.add(buildLoggingConfigMap());
    }

    for (int i = 1; i < replicas; i++) {
      resources.add(buildStatefulSetFollower(i));
      createCoresInFollower(i);
    }

    return resources;
  }

  /**
   * Create all cores for the follower.
   *
   * @param follower index of the follower StatefulSet.
   */
  void createCoresInFollower(int follower) {
    for (Map.Entry<String, String> core : coresToReplicate.entrySet()) {
      String name = getTargetState().getResourceNameFor(this, getFollowerName(follower));
      String flag = concatOptional("solr-core", name, core.getKey(), "created");
      // the following resets the flag on every "shutdown"
      // that makes the core creation run again on a reboot (already existing cores are not harmed)
      if (!this.getStatefulSetState(name).isReady().orElse(false)) {
        if (getTargetState().isFlag(flag)) {
          getTargetState().setFlag(flag, false);
        }
        return;
      }
      if (getTargetState().isFlag(flag)) {
        continue;
      }
      createCore(name, core.getKey(), core.getValue());
      getTargetState().setFlag(flag, true);
    }

  }

  public StatefulSet buildStatefulSetLeader() {
    EnvVarSet env = getEnvVars();
    env.add(EnvVarSimple("SOLR_LEADER", "true"));
    env.addAll(getComponentSpec().getEnv());

    return new StatefulSetBuilder()
            .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT)))
            .withSpec(new StatefulSetSpecBuilder()
                    .withReplicas(getCurrentReplicas())
                    .withServiceName(getTargetState().getServiceNameFor(this))
                    .withSelector(new LabelSelectorBuilder()
                            .withMatchLabels(getSelectorLabels(SOLR_LEADER_COMPONENT))
                            .build())
                    .withTemplate(new PodTemplateSpecBuilder()
                            .withMetadata(new ObjectMetaBuilder()
                                    .withAnnotations(getAnnotations())
                                    .withLabels(getSelectorLabelsWithVersion(SOLR_LEADER_COMPONENT))
                                    .build())
                            .withSpec(new PodSpecBuilder()
                                    .withContainers(buildContainers(env, SOLR_LEADER_COMPONENT))
                                    .withInitContainers(getInitContainers())
                                    .withSecurityContext(getPodSecurityContext())
                                    .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                    .withVolumes(getVolumes(SOLR_LEADER_COMPONENT))
                                    .build())
                            .build())
                    .withVolumeClaimTemplates(getVolumeClaims(SOLR_LEADER_COMPONENT))
                    .build())
            .build();
  }

  public List<Container> buildContainers(EnvVarSet env, String name) {
    LinkedList<Container> containers = new LinkedList<>();
    ResourceRequirements resourceRequirements = getSpec().getWith().getResources() ? ResourceMgmt.withDefaults(getDefaults().getResources(), getResourceManagement()).getResources() : new ResourceRequirements();

    containers.add(new ContainerBuilder()
            .withName(getSpecName())
            .withImage(getImage())
            .withImagePullPolicy(getImagePullPolicy())
            .withResources(resourceRequirements)
            .withSecurityContext(getSecurityContext())
            .withPorts(getContainerPorts())
            .withArgs(getComponentSpec().getArgs())
            .withEnv(env.toList())
            .withVolumeMounts(getVolumeMounts(name))
            .withStartupProbe(getStartupProbe())
            .withLivenessProbe(getLivenessProbe())
            .withReadinessProbe(getReadinessProbe())
            .build());

    return containers;
  }

  public StatefulSet buildStatefulSetFollower(int i) {
    EnvVarSet env = getEnvVars();
    env.add(EnvVarSimple("SOLR_FOLLOWER", "true"));
    if (getSpec().getWith().isSolrBasicAuthEnabled()) {
      // since env variables will be sort alphabetically,
      // env variable name for username and password must come before SOLR_LEADER_BASIC_AUTH
      // (the SOLR_LEADER_BASIC_AUTH name, cannot be changed since is defined in config.sh of the solr image)
      env.add(EnvVarSimple("SOLR_LEADER_AUTH_USERNAME", "solr"));
      env.add(EnvVarSecret("SOLR_LEADER_AUTH_PASSWORD",  "solr-pw", "solr_pw"));
      env.add(EnvVarSimple("SOLR_LEADER_BASIC_AUTH", "$(SOLR_LEADER_AUTH_USERNAME):$(SOLR_LEADER_AUTH_PASSWORD)"));
    }
    env.add(EnvVarSimple("SOLR_LEADER_URL", getServiceUrl(SOLR_LEADER_COMPONENT)));
    env.addAll(getComponentSpec().getEnv());

    return new StatefulSetBuilder()
            .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this, getFollowerName(i))))
            .withSpec(new StatefulSetSpecBuilder()
                    .withServiceName(getTargetState().getServiceNameFor(this))
                    .withSelector(new LabelSelectorBuilder()
                            .withMatchLabels(getSelectorLabels(getFollowerName(i)))
                            .build())
                    .withTemplate(new PodTemplateSpecBuilder()
                            .withMetadata(new ObjectMetaBuilder()
                                    .withAnnotations(getAnnotations())
                                    .withLabels(getSelectorLabelsWithVersion(getFollowerName(i)))
                                    .build())
                            .withSpec(new PodSpecBuilder()
                                    .withContainers(buildContainers(env, getFollowerName(i)))
                                    .withInitContainers(getInitContainers())
                                    .withSecurityContext(getPodSecurityContext())
                                    .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                    .withVolumes(getVolumes(getFollowerName(i)))
                                    .build())
                            .build())
                    .withVolumeClaimTemplates(getVolumeClaims(getFollowerName(i)))
                    .build())
            .build();
  }

  private String getFollowerServiceName() {
    return concatOptional(getTargetState().getResourceNameFor(this), getVersionSuffix(null));
  }

  private String getFollowerName(int i) {
    return "follower-" + i;
  }

  @Override
  public Service buildService() {
    ObjectMeta metadata = getTargetState().getResourceMetadataFor(this, SOLR_CLIENT_SERVER_FOLLOWER);
    metadata.setName(getFollowerServiceName());
    return new ServiceBuilder()
            .withMetadata(metadata)
            .withSpec(new ServiceSpecBuilder()
                    .withSelector(getSelectorLabelsForService())
                    .withPorts(getServicePorts())
                    .build())
            .build();
  }

  public HashMap<String, String> getSelectorLabelsForService() {
    HashMap<String, String> labels = getTargetState().getSelectorLabels();
    // do not key off the name of the component, only the type (and standard selectors), so we match any Solr pod
    labels.put("cmcc.tsystemsmms.com/type", getComponentSpec().getType());
    return labels;
  }

  /**
   * Build a service for just the leader.
   *
   * @return the service for the leader
   */
  private Service buildServiceLeader() {
    return new ServiceBuilder()
            .withMetadata(getTargetState().getResourceMetadataFor(this, SOLR_LEADER_COMPONENT))
            .withSpec(new ServiceSpecBuilder()
                    .withSelector(getSelectorLabels(SOLR_LEADER_COMPONENT))
                    .withPorts(getServicePorts())
                    .build())
            .build();
  }

  @Override
  public long getTerminationGracePeriodSeconds() {
    return 30L;
  }

  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = new EnvVarSet();

    env.add(EnvVarSimple("SPRING_APPLICATION_NAME", getSpecName()));
    env.add(EnvVarSimple("SPRING_BOOT_EXPLODED_APP", "true"));
    env.add(EnvVarSimple("GC_TUNE", "-XX:+UseG1GC -XX:+PerfDisableSharedMem -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=250 -XX:+AlwaysPreTouch"));
    env.add(EnvVarSimple("SOLR_HEAP", ""));
    env.add(EnvVarSimple("SOLR_JAVA_MEM", getCmcc().getSpec().getDefaults().getJavaOpts()));
    return env;
  }

  public List<PersistentVolumeClaim> getVolumeClaims(String name) {
    var resourceName = getTargetState().getResourceNameFor(this, name);
    return List.of(getPersistentVolumeClaim(resourceName, getVolumeSize(ComponentSpec.VolumeSize::getData)));
  }

  public List<Volume> getVolumes(String name) {
    LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

    volumes.add(new VolumeBuilder()
            .withName("etc-defaults")
            .withEmptyDir(new EmptyDirVolumeSource())
            .build());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      volumes.add(new VolumeBuilder()
              .withName("logging-config")
              .withConfigMap(new ConfigMapVolumeSourceBuilder()
                      .withName("logging-config")
                      .withOptional()
                      .build())
              .build());
    }
    if (getSpec().getWith().isSolrBasicAuthEnabled()) {
      volumes.add(new VolumeBuilder()
              .withName("solr-security-config")
              .withSecret(new SecretVolumeSourceBuilder().withSecretName("solr-security")
                      .withDefaultMode(420)
                      .withOptional(false)
                      .build())
              .build());
    }

    return volumes;
  }

  /**
   * Create a Solr core in a follower. This is a bit ugly, since exec() doesn't appear to be able to capture the
   * commands' exit code.
   *
   * <pre>curl 'http://localhost:8983/solr/admin/cores?action=CREATE&name=studio&configSet=content&dataDir=data'</pre>
   *
   * @param name name of the index to create
   */
  public void createCore(String resource, String name, String configSet) {
    String podName = concatOptional(resource, "0");
    String url = UriComponentsBuilder.fromUriString("http://localhost:8983/solr/admin/cores")
            .queryParam("action", "CREATE")
            .queryParam("name", name)
            .queryParam("configSet", configSet)
            .queryParam("dataDir", "data")
            .toUriString();
    log.debug("[{}] Creating Solr Core {} on pod {}, using {}", getTargetState().getContextForLogging(), name, podName, url);

    var pod = getKubernetesClient().pods().inNamespace(getNamespace()).withName(podName);

    if (pod.get() == null) {
      log.warn("[{}] No pod with name '{}' found in namespace '{}'", getTargetState().getContextForLogging(), name, getNamespace());
      return;
    }
    var result = executeWebRequest(pod, url, false);

    // try and parse output as JSON
    try {
      SolrAdminResponse response = OBJECT_MAPPER.readValue(result.output, SolrAdminResponse.class);
      log.debug("[{}] status {}", getTargetState().getContextForLogging(), response.responseHeader.status);
      if (response.responseHeader != null && response.responseHeader.status == 0)
        return; // success
      if (response.error != null) {
        if (response.error.code == 500 && response.error.msg.equals("Core with name '" + name + "' already exists.")) {
          log.debug("[{}] Core already exists. Ok.", getTargetState().getContextForLogging());
          return; // OK if it already exists
        }
        throw new CustomResourceConfigError("Unable to create Solr core \"" + name + "\" on pod \"" + podName + "\": " + response.error.msg);
      }
    } catch (JsonProcessingException e) {
      throw new CustomResourceConfigError("Unable to create Solr core \"" + name + "\" on pod \"" + podName + "\": " + result.output, e);
    }

    if (result.exitCode != 0) {
      throw new CustomResourceConfigError("Unable to create Solr core \"" + name + "\" on pod \"" + podName + "\": " + result.errorOutput);
    }
  }

  public List<VolumeMount> getVolumeMounts(String name) {
    LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

    volumeMounts.add(new VolumeMountBuilder()
            .withName(getTargetState().getResourceNameFor(this, name))
            .withMountPath("/var/solr")
            .build());
    volumeMounts.add(new VolumeMountBuilder()
            .withName("etc-defaults")
            .withMountPath("/etc/default")
            .build());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      volumeMounts.add(new VolumeMountBuilder()
              .withName("logging-config")
              .withMountPath("/var/solr/log4j2.xml")
              .withSubPath("log4j2-solr.xml")
              .build());
    }
    if (getSpec().getWith().isSolrBasicAuthEnabled()) {
      volumeMounts.add(new VolumeMountBuilder()
              .withName("solr-security-config")
              .withMountPath("/opt/solr/server/solr/security.json")
              .withSubPath("security.json")
              .withReadOnly()
              .build());
    }

    return volumeMounts;
  }

  /**
   * Defines a probe suitable for the startup check.
   *
   * @return probe definition
   */
  public Probe getStartupProbe() {
    var interval = 10;
    var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getStartup()).orElse(300);
    return new ProbeBuilder()
            .withPeriodSeconds(interval)
            .withTimeoutSeconds(interval)
            .withFailureThreshold(timeout / interval)
            .withHttpGet(new HTTPGetActionBuilder()
                    .withPath("/solr/admin/info/health")
                    .withPort(new IntOrString("http"))
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
            .withTimeoutSeconds(15)
            .withHttpGet(new HTTPGetActionBuilder()
                    .withPath("/solr/admin/info/health")
                    .withPort(new IntOrString("http"))
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
            .withTimeoutSeconds(15)
            .withHttpGet(new HTTPGetActionBuilder()
                    .withPath("/solr/admin/info/health")
                    .withPort(new IntOrString("http"))
                    .build())
            .build();
  }

  @Override
  public List<ContainerPort> getContainerPorts() {
    return List.of(
            new ContainerPortBuilder()
                    .withName("http")
                    .withContainerPort(8983)
                    .build()
    );
  }

  @Override
  public List<ServicePort> getServicePorts() {
    return List.of(
            new ServicePortBuilder().withName("ior").withPort(8983).withNewTargetPort("http").build());
  }

  @Override
  public String getServiceUrl() {
    throw new CustomResourceConfigError("Component implements multiple services, need to call getServiceUrl(variant) instead");
  }

  @Override
  public String getServiceUrl(String variant) {
    if (replicas > 1 && !SOLR_LEADER_COMPONENT.equals(variant)) {
      return "http://" + getFollowerServiceName() + ":8983/solr";
    }
    return "http://" + getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT) + ":8983/solr";
  }

  @Override
  public ComponentState getState() {
    var result = this.getStatefulSetState(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT));
//    if (result.isRelevant() && !result.isReady().orElse(false)) {
      return result;
//    }
//    return this.getStatefulSetState(getTargetState().getResourceNameFor(this, getFollowerName(0)));
  }

  @Data
  @NoArgsConstructor
  public static class SolrAdminResponseHeader {
    Integer status;
    @JsonProperty("QTime")
    double qTime;
  }

  @Data
  @NoArgsConstructor
  public static class SolrAdminResponseError {
    List<String> metadata;
    String msg;
    Integer code;
    String trace;
  }

  @Data
  @NoArgsConstructor
  public static class SolrAdminResponse {
    SolrAdminResponseHeader responseHeader;
    SolrAdminResponseError error;
  }

}