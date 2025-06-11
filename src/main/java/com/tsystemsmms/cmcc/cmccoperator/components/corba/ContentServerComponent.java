/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.HasJdbcClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.generic.MySQLComponent.MYSQL;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public class ContentServerComponent extends CorbaComponent implements HasJdbcClient, HasService {
  public static final String CONTENT_SERVER = "content-server";

  public static final String KIND_CMS = "cms";
  public static final String KIND_MLS = "mls";
  public static final String KIND_RLS = "rls";

  public static final String MANAGEMENT_SCHEMA = "management";
  public static final String MASTER_SCHEMA = "master";

  public static final String LICENSE_VOLUME_NAME = "license";
  private static final String REPLICATOR_STATUS_URL = "http://localhost:8081/actuator/replicator";
  private static final String RLS_STATUS_HEADER_CONTENT_TYPE = "Content-Type: application/json";

  String licenseSecretName;

  public ContentServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, CONTENT_SERVER);
    if (getComponentSpec().getKind() == null)
      throw new CustomResourceConfigError("kind must be set to either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
    switch (componentSpec.getKind()) {
      case KIND_CMS:
        licenseSecretName = getSpec().getLicenseSecrets().getCMSLicense();
        setDefaultSchemas(Map.of(
                JDBC_CLIENT_SECRET_REF_KIND, MANAGEMENT_SCHEMA,
                UAPI_CLIENT_SECRET_REF_KIND, "publisher"
        ));
        break;
      case KIND_MLS:
        licenseSecretName = getSpec().getLicenseSecrets().getMLSLicense();
        setDefaultSchemas(Map.of(
                JDBC_CLIENT_SECRET_REF_KIND, MASTER_SCHEMA,
                UAPI_CLIENT_SECRET_REF_KIND, "publisher"
        ));
        break;
      case KIND_RLS:
        checkRLSMilestone(componentSpec);
        checkRLSMilestone(getComponentSpec());
        checkRLSReplicas();
        if (getReplicas() > 0) {
          licenseSecretName = getSpec().getLicenseSecrets().getRLSLicense();
          setDefaultSchemas(Map.of(
                  UAPI_CLIENT_SECRET_REF_KIND, "publisher"
          ));
        }
        break;
      default:
        throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
    }
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    if (newCs.getKind().equals(KIND_RLS)) {
      checkRLSMilestone(newCs);
    }
    super.updateComponentSpec(newCs);
    if (newCs.getKind().equals(KIND_RLS)) {
      checkRLSReplicas();
    }
    return this;
  }

  private void checkRLSMilestone(ComponentSpec componentSpec) {
    if (componentSpec.getMilestone() == null) {
      // edge case: RLS component has been added to component list but
      // without a milestone (default from DefaultTargetState does not apply then)
      componentSpec.setMilestone(Milestone.ManagementReady);
    }
  }

  private void checkRLSReplicas() {
    var rls = getInt(getCmcc().getSpec().getWith().getDelivery().getRls());
    if (this.getReplicas() < rls) {
      setReplicas(rls);
    }
  }

  @Override
  public int getCurrentReplicas() {
    if ( getTargetState().isUpgrading() && getComponentSpec().getKind().equals(KIND_RLS)) {
      // during upgrade, we want to keep the existing RLS pods running
      return super.getReplicas();
    }
    return super.getCurrentReplicas();
  }

  @Override
  public List<HasMetadata> buildResources() {
    List<HasMetadata> resources = new LinkedList<>();

    if (!getComponentSpec().getKind().equals(KIND_RLS)) {
      // non RLS are simply deployed, even in an upgrade
      resources.add(buildStatefulSet());
      resources.add(buildService());
    } else if (getReplicas() > 0) {
      // RLS
      resources.add(buildService());

      var partition = 0;
      // check if we have to be keep the previous version running
      if (getTargetState().isUpgrading()) {
        partition = getReplicas();
        if (reachedMyMilestone() && !reachedReady()) {
          // so new version should be deployed (but keep 1 last old version running until all are ready => partition:1)
          partition = 1;
        }
        log.info("[{}] Deploying new version of {} with partition: {}", getTargetState().getContextForLogging(), getTargetState().getResourceNameFor(this), partition);
      }

      resources.add(buildStatefulSet(getCurrentReplicas(), partition));
    }

    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      resources.add(buildLoggingConfigMap());
    }

    return resources;
  }

  @Override
  public String getServiceUrl() {
    if (getComponentSpec().getKind().equals(KIND_RLS)) {
      return "http://" + getServiceName() + ":8080/ior";
    }
    return super.getServiceUrl();
  }

  @Override
  public void requestRequiredResources() {
    switch (getComponentSpec().getKind()) {
      case KIND_CMS, KIND_MLS:
        super.requestRequiredResources();
        getJdbcClientSecretRef();
        break;
      case KIND_RLS:
        if (getReplicas() > 0) {
          super.requestRequiredResources();
          for (int i = 0; i < getReplicas(); i++) {
            getJdbcClientSecretRef(jdbcSecretName(i));
          }
        }
        break;
    }
  }

  private String jdbcSecretName(int i) {
    return "rls" + i;
  }

  @Override
  public String getBaseResourceName() {
    switch (getComponentSpec().getKind()) {
      case KIND_CMS:
        return "content-management-server";
      case KIND_MLS:
        return "master-live-server";
      case KIND_RLS:
        return "replication-live-server";
      default:
        throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
    }
  }

  @Override
  public long getTerminationGracePeriodSeconds() {
    return 30L;
  }

  @Override
  protected PodAffinity getPodAffinity() {
    var affinityRules = new LinkedList<WeightedPodAffinityTerm>();

    if (getComponentSpec().getKind().equals(KIND_CMS)) {
      affinityRules.add(createAffinityToComponent(MYSQL, 25));
      affinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_MLS, 25));
    }
    if (getComponentSpec().getKind().equals(KIND_MLS)) {
      affinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_CMS, 25));
    }

    return new PodAffinityBuilder()
            .withPreferredDuringSchedulingIgnoredDuringExecution(affinityRules.stream().filter(Objects::nonNull).toList())
            .build();
  }

  @Override
  protected PodAntiAffinity getPodAntiAffinity() {
    var antiAffinityRules = new LinkedList<WeightedPodAffinityTerm>();

    if (getComponentSpec().getKind().equals(KIND_RLS)) {
      antiAffinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_CMS, 35));
      antiAffinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_MLS, 10));
      antiAffinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_RLS, 10));
    }

    return new PodAntiAffinityBuilder()
            .withPreferredDuringSchedulingIgnoredDuringExecution(antiAffinityRules.stream().filter(Objects::nonNull).toList())
            .build();
  }

  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = super.getEnvVars();

    switch (getComponentSpec().getKind()) {
      case KIND_CMS -> {
        env.addAll(getJdbcClientEnvVars("SQL_STORE"));
        env.addAll(getUapiClientEnvVars("PUBLISHER_LOCAL"));
        env.addAll(getUapiClientEnvVars("PUBLISHER_TARGET_0"));
      }
      case KIND_MLS -> {
        env.addAll(getJdbcClientEnvVars("SQL_STORE"));
      }
      case KIND_RLS -> {
        env.add(new EnvVarBuilder()
                .withName("POD_INDEX")
                .withValueFrom(new EnvVarSourceBuilder()
                        .withFieldRef(new ObjectFieldSelectorBuilder()
                                .withFieldPath("metadata.labels['apps.kubernetes.io/pod-index']").build()).build()).build());
        env.add(new EnvVarBuilder()
                .withName("POD_IP")
                .withValueFrom(new EnvVarSourceBuilder()
                        .withFieldRef(new ObjectFieldSelectorBuilder()
                                .withFieldPath("status.podIP").build()).build()).build());
        env.add(EnvVarSimple("CMCC_REDIRECT_DATABASE_PROPERTIES", "true"));
        for (int i = 0; i < getReplicas(); i++) {
            env.addAll(getJdbcClientEnvVars("MAPPED_" + i + "_SQL_STORE", getJdbcClientSecretRef(jdbcSecretName(i))));
            if (env.get("MYSQL_HOST").isPresent())
                env.add(EnvVarRenamed("MAPPED_" + i + "_MYSQL_HOST", env.get("MYSQL_HOST").get()));
        }
        env.addAll(getUapiClientEnvVars("REPLICATOR_PUBLICATION"));

        // POD_INDEX is resolved by k8s during POD setup, the outer var "MAPPED..." is resolved by spring boot during runtime
        env.add(EnvVarSimple("SQL_STORE_DRIVER", "${MAPPED_$(POD_INDEX)_SQL_STORE_DRIVER}"));
        env.add(EnvVarSimple("SQL_STORE_PASSWORD", "${MAPPED_$(POD_INDEX)_SQL_STORE_PASSWORD}"));
        env.add(EnvVarSimple("SQL_STORE_SCHEMA", "${MAPPED_$(POD_INDEX)_SQL_STORE_SCHEMA}"));
        env.add(EnvVarSimple("SQL_STORE_URL", "${MAPPED_$(POD_INDEX)_SQL_STORE_URL}"));
        env.add(EnvVarSimple("SQL_STORE_USER", "${MAPPED_$(POD_INDEX)_SQL_STORE_USER}"));
      }
    }

    for (ClientSecret cs : getTargetState().getClientSecrets(UAPI_CLIENT_SECRET_REF_KIND).values()) {
      Map<String, String> secret = cs.getStringData();
      String username = secret.get(cs.getRef().getUsernameKey());
      if (cs.getRef().getUsernameKey() == null || username == null) {
        //noinspection OptionalGetWithoutIsPresent
        throw new CustomResourceConfigError("Secret \"" + cs.getSecret().get().getMetadata().getName()
                + "\" does not contain the field \"" + cs.getRef().getUsernameKey()
                + "\" for the username, or it is null");
      }
      env.add(EnvVarSecret("CAP_SERVER_INITIALPASSWORD_" + username.toUpperCase(Locale.ROOT),
              cs.getRef().getSecretName(), cs.getRef().getPasswordKey()));
    }

    return env;
  }

  @Override
  public Map<String, String> getSpringBootProperties() {
    Map<String, String> properties = super.getSpringBootProperties();

    properties.putAll(Map.of(
            "cap.server.license", "/coremedia/licenses/license.zip",
            "com.coremedia.corba.server.host", getTargetState().getResourceNameFor(this),
            "cap.server.cache.resource-cache-size", "5000"
    ));
    if (getComponentSpec().getKind().equals(KIND_CMS)) {
      properties.put("publisher.target[0].iorUrl", getTargetState().getServiceUrlFor("content-server", "mls"));
      properties.put("publisher.target[0].ior-url", getTargetState().getServiceUrlFor("content-server", "mls"));
      properties.put("publisher.target[0].name", "mls");
    }
    if (getComponentSpec().getKind().equals(KIND_RLS)) {
      properties.remove("repository.url"); // avoid irritations
      properties.remove("com.coremedia.corba.server.host"); // let it set the IP directly, see below
      properties.put("com.coremedia.corba.server.singleIp", "${POD_IP}");
      properties.put("replicator.publication-ior-url", getTargetState().getServiceUrlFor("content-server", "mls"));
      properties.put("management.endpoint.health.group.startup.include", "readinessState,replicator");
    }

    return properties;
  }

  @Override
  public Probe getStartupProbe() {
    if (getComponentSpec().getKind().equals(KIND_RLS)) {
      var interval = 10;
      var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getStartup()).orElse(600);
      return new ProbeBuilder()
              .withPeriodSeconds(interval)
              .withTimeoutSeconds(interval)
              .withFailureThreshold(timeout / interval)
              .withHttpGet(new HTTPGetActionBuilder()
                      .withPath("/actuator/health/startup")
                      .withPort(new IntOrString("management"))
                      .build())
              .build();
    }
    return super.getStartupProbe();
  }

  @Override
  public List<Volume> getVolumes() {
    LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

    volumes.add(new VolumeBuilder()
            .withName(LICENSE_VOLUME_NAME)
            .withSecret(new SecretVolumeSourceBuilder()
                    .withSecretName(licenseSecretName)
                    .build())
            .build());

    return volumes;
  }

  @Override
  public List<VolumeMount> getVolumeMounts() {
    LinkedList<VolumeMount> volumes = new LinkedList<>(super.getVolumeMounts());

    VolumeMount licenseVolumeMount = new VolumeMountBuilder()
            .withName(LICENSE_VOLUME_NAME)
            .withMountPath("/coremedia/licenses")
            .build();
    volumes.add(licenseVolumeMount);
    volumes.add(new VolumeMountBuilder()
            .withName("coremedia-var-tmp")
            .withMountPath("/coremedia/var/tmp")
            .build());

    return volumes;
  }

  public void disableRlsReplication() {
    if (!this.getComponentSpec().getKind().equals(KIND_RLS)) {
      log.warn("[{}] Called disableRlsReplication on a non-RLS component, which is not supported", getTargetState().getContextForLogging());
      return;
    }

    var pods = getTargetState().getKubernetesClient().pods()
            .inNamespace(getNamespace())
            .withLabels(getSelectorLabels())
            .resources()
            .toList();


    log.debug("[{}] Disable RLS replication on pods {}, using '{}'", getTargetState().getContextForLogging(),
            pods.stream().map(x -> x.get().getMetadata().getName()).toList(), REPLICATOR_STATUS_URL);
    var results = pods.stream().map(pod -> executePostWebRequest(pod, REPLICATOR_STATUS_URL, RLS_STATUS_HEADER_CONTENT_TYPE, getRlsStatusData(false), true)).toList();

    results.stream().forEach(result -> {
      if (result.exitCode != 0 || result.output == null || !result.output.contains("\"ACCEPTED\":\"Replicator has been disabled.")) {
        throw new CustomResourceConfigError("Unable to stop RLS replication on pod \"" + result.pod.get().getMetadata().getName() + "\": " + result.output);
      }
    });
  }

  public void enableRlsReplication() {
    if (!this.getComponentSpec().getKind().equals(KIND_RLS)) {
      log.warn("[{}] Called enableRlsReplication on a non-RLS component, which is not supported", getTargetState().getContextForLogging());
      return;
    }

    var pods = getTargetState().getKubernetesClient().pods()
            .inNamespace(getNamespace())
            .withLabels(getSelectorLabels())
            .resources()
            .toList();


    log.debug("[{}] Enable RLS replication on pods {}, using '{}'", getTargetState().getContextForLogging(),
            pods.stream().map(x -> x.get().getMetadata().getName()).toList(), REPLICATOR_STATUS_URL);
    var results = pods.stream().map(pod -> executePostWebRequest(pod, REPLICATOR_STATUS_URL, RLS_STATUS_HEADER_CONTENT_TYPE, getRlsStatusData(true), true)).toList();

    results.stream().forEach(result -> {
      if (result.exitCode != 0 || result.output == null || !result.output.contains("\"ACCEPTED\":\"Replicator has been enabled.")) {
        throw new CustomResourceConfigError("Unable to stop RLS replication on pod \"\": " + result.output);
      }
    });
  }

  private String getRlsStatusData(boolean enable) {
    return "{\"enable\": " + enable + "}";
  }
}
