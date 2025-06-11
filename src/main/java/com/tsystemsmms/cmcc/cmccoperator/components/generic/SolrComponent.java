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

import com.fasterxml.jackson.core.type.TypeReference;
import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.NoSuchComponentException;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SERVER_FOLLOWER;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.Ready;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.compareTo;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class SolrComponent extends AbstractComponent implements HasService {
  public static final String SOLR_REPLICAS = "replicas";
  public static final String SOLR_CORES_TO_REPLICATE = "coresToReplicate";
  public static final String KIND_LEADER = "leader";
  public static final String KIND_FOLLOWER = "follower";
  public static final String SOLR = "solr";

  private static final String SOLR_FOLLOWER_DISABLE_SYNC_URL = "http://localhost:8983/solr/live/replication?command=disablereplication";
  private static final String SOLR_FOLLOWER_DISABLE_POLL_URL = "http://localhost:8983/solr/live/replication?command=disablepoll";

  /**
   * Cores that the Operator should create in the followers. The map is modifyable so additional entries can be added
   * through the extra entry on the solr component.
   */
  private final HashMap<String, String> coresToReplicate = new HashMap<>(Map.of(
          "live", "cae"
  ));

  public SolrComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, SOLR);

    switch (componentSpec.getKind()) {
      case KIND_LEADER:
        //nothing
        break;
      case KIND_FOLLOWER:
        if (componentSpec.getExtra().containsKey(SOLR_REPLICAS)) {
          setReplicas(Integer.parseInt(componentSpec.getExtra().get(SOLR_REPLICAS)));
        }
        break;
      default:
        throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LEADER + ", or " + KIND_FOLLOWER);
    }
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    super.updateComponentSpec(newCs);
    if (newCs.getExtra().containsKey(SOLR_REPLICAS)) {
      setReplicas(Integer.parseInt(newCs.getExtra().get(SOLR_REPLICAS)));
    }
    if (newCs.getExtra().containsKey(SOLR_CORES_TO_REPLICATE)) {
      coresToReplicate.putAll(getTargetState().getYamlMapper().load(newCs.getExtra().get(SOLR_CORES_TO_REPLICATE), new TypeReference<HashMap<String, String>>() {
              },
              () -> "Unable to read \"" + SOLR_CORES_TO_REPLICATE + "\" as a map in component \"" + newCs.getName() + "\""));
    }
    return this;
  }

  @Override
  public int getCurrentReplicas() {
    if ( getTargetState().isUpgrading() && getComponentSpec().getKind().equals(KIND_FOLLOWER)) {
      // during upgrade, we want to keep the existing follower pods running
      return super.getReplicas();
    }
    return super.getCurrentReplicas();
  }

  @Override
  public List<HasMetadata> buildResources() {
    List<HasMetadata> resources = new LinkedList<>();

    if (getComponentSpec().getKind().equals(KIND_LEADER)) {
      resources.add(buildServiceLeader());
      resources.add(buildStatefulSet());
    } else if (getReplicas() > 0) {

      var partition = 0;
      if (getComponentSpec().getKind().equals(KIND_FOLLOWER)) {
        // in any case: do always deploy/keep PVCs and the most recent follower service
        resources.add(buildServiceFollower());

        if (getTargetState().isUpgrading()) {
        // create old service
          try(var __ = getVersioningTargetState().withCurrentlyDeployedVersion()) {
            resources.add(buildServiceFollower());
          }
        }

        // check if we have to be keep the previous version running
        if (getTargetState().isUpgrading()) {
          if (compareTo(getCmcc().getStatus().getMilestone(), getComponentSpec().getMilestone()) < 0) {
            partition = getReplicas();
            log.info("[{}] Keeping old versions of {} with partition:{}", getTargetState().getContextForLogging(),
                    getTargetState().getResourceNameFor(this), partition);
          } else if (compareTo(getCmcc().getStatus().getMilestone(), Ready) < 0) {
            // we reached our milestone, but we haven't reached ready
            // so new version should be deployed (but keep 1 last old version running until all are ready => partition:1)
            partition = 1;
            log.info("[{}] Deploying new version of {} with partition:1", getTargetState().getContextForLogging(),
                    getTargetState().getResourceNameFor(this));
          }
        }
      }

      resources.add(buildStatefulSet(getCurrentReplicas(), partition));
    }

    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      resources.add(buildLoggingConfigMap());
    }

    return resources;
  }

  @Override
  protected PodAntiAffinity getPodAntiAffinity() {
    var antiAffinityRules = new LinkedList<WeightedPodAffinityTerm>();

    if (getComponentSpec().getKind().equals(KIND_FOLLOWER)) {
      antiAffinityRules.add(createAffinityToComponent(SOLR, KIND_LEADER, 25));
      antiAffinityRules.add(createAffinityToComponent(SOLR, KIND_FOLLOWER, 10));
    }

    return new PodAntiAffinityBuilder()
            .withPreferredDuringSchedulingIgnoredDuringExecution(antiAffinityRules.stream().filter(Objects::nonNull).toList())
            .build();
  }

  @Override
  public List<PersistentVolumeClaim> getVolumeClaims() {
    var resourceName = getTargetState().getResourceNameFor(this);
    return List.of(getPersistentVolumeClaim(resourceName, getVolumeSize(ComponentSpec.VolumeSize::getData)));
  }

  private String getFollowerServiceName() {
    return concatOptional(getTargetState().getResourceNameFor(SOLR, KIND_FOLLOWER), getVersionSuffix());
  }

  public Service buildServiceFollower() {
    ObjectMeta metadata = getTargetState().getResourceMetadataFor(this, SOLR_CLIENT_SERVER_FOLLOWER);
    metadata.setName(getFollowerServiceName());
    return new ServiceBuilder()
            .withMetadata(metadata)
            .withSpec(new ServiceSpecBuilder()
                    .withSelector(getSelectorLabelsWithVersion())
                    .withPorts(getServicePorts())
                    .build())
            .build();
  }

  /**
   * Build a service for just the leader.
   *
   * @return the service for the leader
   */
  private Service buildServiceLeader() {
    return new ServiceBuilder()
            .withMetadata(getTargetState().getResourceMetadataFor(this))
            .withSpec(new ServiceSpecBuilder()
                    .withSelector(getSelectorLabels())
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

    if (getComponentSpec().getKind().equals(KIND_LEADER)) {
      env.add(EnvVarSimple("SOLR_LEADER", "true"));
    }
    if (getComponentSpec().getKind().equals(KIND_FOLLOWER)) {
      env.add(EnvVarSimple("SOLR_FOLLOWER", "true"));
      if (getSpec().getWith().isSolrBasicAuthEnabled()) {
        // since env variables will be sort alphabetically,
        // env variable name for username and password must come before SOLR_LEADER_BASIC_AUTH
        // (the SOLR_LEADER_BASIC_AUTH name, cannot be changed since is defined in config.sh of the solr image)
        env.add(EnvVarSimple("SOLR_LEADER_AUTH_USERNAME", "solr"));
        env.add(EnvVarSecret("SOLR_LEADER_AUTH_PASSWORD",  "solr-pw", "solr_pw"));
        env.add(EnvVarSimple("SOLR_LEADER_BASIC_AUTH", "$(SOLR_LEADER_AUTH_USERNAME):$(SOLR_LEADER_AUTH_PASSWORD)"));
      }
      env.add(EnvVarSimple("SOLR_LEADER_URL", getServiceUrl(KIND_LEADER)));
      env.add(EnvVarSimple("SOLR_FOLLOWER_AUTOCREATE_CORES", "true"));
      env.add(EnvVarSimple("SOLR_FOLLOWER_AUTOCREATE_CORES_LIST",  String.join(" ", this.coresToReplicate.keySet())));
    }

    return env;
  }

  @Override
  public List<Volume> getVolumes() {
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

  @Override
  public List<VolumeMount> getVolumeMounts() {
    LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

    volumeMounts.add(new VolumeMountBuilder()
            .withName(getTargetState().getResourceNameFor(this))
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
                    .build(),
            new ContainerPortBuilder()
                    .withName("management")
                    .withContainerPort(8199)
                    .build()
    );
  }

  @Override
  public List<ServicePort> getServicePorts() {
    return List.of(
            new ServicePortBuilder().withName("ior").withPort(8983).withNewTargetPort("http").build(),
            new ServicePortBuilder().withName("management").withPort(8199).withNewTargetPort("management").build());
  }

  @Override
  public String getServiceUrl() {
    throw new CustomResourceConfigError("Component implements multiple services, need to call getServiceUrl(variant) instead");
  }

  @Override
  public String getServiceUrl(String variant) {
    // unusual case: this could be called on leader but it must know if there are followers available
    var replicasOfFollower = 0;
    try {
      var followerComponent = getTargetState().getComponentCollection().getHasServiceComponent(SOLR, KIND_FOLLOWER);
      replicasOfFollower = followerComponent.getReplicas();
    } catch (NoSuchComponentException e) {
      // ignored, assume 0 followers
    }
    if (replicasOfFollower > 0 && !KIND_LEADER.equals(variant)) {
      return "http://" + getFollowerServiceName() + ":8983/solr";
    }
    return "http://" + getTargetState().getResourceNameFor(SOLR, KIND_LEADER) + ":8983/solr";
  }

  public void disableReplication() {
    if (!this.getComponentSpec().getKind().equals(KIND_FOLLOWER)) {
      log.warn("[{}] Called disableReplication on a non-follower component, which is not supported", getTargetState().getContextForLogging());
      return;
    }

    var pods = getTargetState().getKubernetesClient().pods()
            .inNamespace(getNamespace())
            .withLabels(getSelectorLabels())
            .resources()
            .toList();

    log.debug("[{}] Disable Solr Follower replication on pods {}, using '{}'", getTargetState().getContextForLogging(), pods.stream().map(x -> x.get().getMetadata().getName()).toList(), SOLR_FOLLOWER_DISABLE_SYNC_URL);
    var credentials = getSpec().getWith().isSolrBasicAuthEnabled() ? "${SOLR_LEADER_BASIC_AUTH}" : null;

    var results = pods.stream().map(pod -> executeWebRequest(pod, SOLR_FOLLOWER_DISABLE_SYNC_URL, credentials, true)).toList();
    results.stream().forEach(result -> {
      if (result.exitCode != 0 || result.output == null || !result.output.contains("\"status\":\"OK\"")) {
        throw new CustomResourceConfigError("Unable to stop Solr replication on pod \"" + result.pod.get().getMetadata().getName() + "\": " + result.output + ". Error output: " + result.errorOutput);
      }
    });

    results = pods.stream().map(pod -> executeWebRequest(pod, SOLR_FOLLOWER_DISABLE_POLL_URL, credentials, true)).toList();
    results.stream().forEach(result -> {
      if (result.exitCode != 0 || result.output == null || !result.output.contains("\"status\":\"OK\"")) {
        throw new CustomResourceConfigError("Unable to stop Solr replication on pod \"" + result.pod.get().getMetadata().getName() + "\": " + result.output + ". Error output: " + result.errorOutput);
      }
    });
  }
}