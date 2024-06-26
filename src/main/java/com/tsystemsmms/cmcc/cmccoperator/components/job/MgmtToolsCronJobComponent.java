/*
 * Copyright (c) 2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.job;

import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient;
import com.tsystemsmms.cmcc.cmccoperator.components.SpringBootComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

/**
 * Run a Management Tools command regularly using an k8s CronJob.
 */
public class MgmtToolsCronJobComponent extends SpringBootComponent implements HasUapiClient {
  public static final String EXTRA_CONFIG = "config";

  long activeDeadlineSeconds = 30 * 60L;
  String cron = "0 0 * * *";
  String timezone = "";

  public MgmtToolsCronJobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, "management-tools");
    updateConfig();
  }

  @Override
  public List<HasMetadata> buildResources() {
    return List.of(buildJob());
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    super.updateComponentSpec(newCs);
    updateConfig();
    return this;
  }

  private void updateConfig() {
    var extraConfig = getComponentSpec().getExtra(EXTRA_CONFIG);
    if (extraConfig.isPresent()) {
      Yaml yaml = new Yaml(new Constructor(MgmtCronJobConfig.class));
      MgmtCronJobConfig config = yaml.load(extraConfig.get());
      cron = config.getCron();
      timezone = config.getTimezone();
    }
    getComponentSpec().getExtra("activeDeadlineSeconds")
            .ifPresent(v -> activeDeadlineSeconds = Long.parseLong(v));
    getComponentSpec().getExtra("cron")
            .ifPresent(v -> cron = v);
    getComponentSpec().getExtra("timezone")
            .ifPresent(v -> timezone = v);
  }

  private CronJob buildJob() {
    return new CronJobBuilder()
            .withMetadata(getResourceMetadata())
            .withSpec(new CronJobSpecBuilder()
                    .withSchedule(cron)
                    .withTimeZone(timezone)
                    .withFailedJobsHistoryLimit(3)
                    .withSuccessfulJobsHistoryLimit(1)
                    .withJobTemplate(new JobTemplateSpecBuilder()
                            .withSpec(new JobSpecBuilder()
                                    .withActiveDeadlineSeconds(activeDeadlineSeconds)
                                    .withBackoffLimit(3)
                                    .withCompletions(1)
                                    .withParallelism(1)
                                    .withTemplate(new PodTemplateSpecBuilder()
                                            .withMetadata(new ObjectMetaBuilder()
                                                    .withAnnotations(getAnnotations())
                                                    .withLabels(getSelectorLabels())
                                                    .build())
                                            .withSpec(new PodSpecBuilder()
                                                    .withRestartPolicy("Never")
                                                    .withContainers(buildContainers())
                                                    .withInitContainers(getInitContainers())
                                                    .withVolumes(getVolumes())
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
  }

  @Override
  public SecurityContext getSecurityContext() {
    return Utils.mergeObjects(SecurityContext.class,
            new SecurityContextBuilder()
                    .withReadOnlyRootFilesystem(false) // properties location is a mix of dynamically created files and files from the image
                    .build(),
            getCmcc().getSpec().getDefaults().getSecurityContext(),
            getComponentSpec().getSecurityContext());
  }

  @Override
  public Probe getLivenessProbe() {
    return null;
  }

  @Override
  public Probe getReadinessProbe() {
    return null;
  }

  @Override
  public Probe getStartupProbe() {
    return null;
  }

  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = super.getEnvVars();

    env.add(EnvVarSimple("JAVA_HEAP", ""));
    env.add(EnvVarSimple("JAVA_OPTS", getCmcc().getSpec().getDefaults().getJavaOpts()));

    env.add(EnvVarSimple("CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));
    env.add(EnvVarSimple("DEV_MASTER_CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "mls")));
    env.add(EnvVarSimple("DEV_MANAGEMENT_CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));

    env.addAll(getUapiClientEnvVars("TOOLS"));
    env.add(EnvVarSimple("DEBUG_ENTRYPOINT", "true"));
    env.add(EnvVarSimple("IMPORT_DIR", "/coremedia/import"));

    env.addAll(getTargetState().getComponentCollection().getHasJdbcClientComponent("content-server", "cms")
            .getJdbcClientEnvVars("DEV_MANAGEMENT_JDBC"));
    env.addAll(getTargetState().getComponentCollection().getHasJdbcClientComponent("content-server", "mls")
            .getJdbcClientEnvVars("DEV_MASTER_JDBC"));

    return env;
  }

  @Override
  public HashMap<String, String> getSelectorLabels() {
    HashMap<String, String> labels = super.getSelectorLabels();
    labels.putAll(getJobLabels());
    return labels;
  }

  public static Map<String, String> getJobLabels() {
    return Map.of("cmcc.tsystemsmms.com/cronjob", MgmtToolsCronJobComponent.class.getSimpleName().replaceAll("Component$", ""));
  }

  public Optional<Boolean> isReady() {
    // Once the object has been created, it is ready. Any execution failures are not tracked by the operator.
    return Optional.of(Boolean.TRUE);
  }

  private Optional<MgmtCronJobConfig> getMgmtCronJobConfigFromExtra() {
    Yaml yaml = new Yaml(new Constructor(MgmtCronJobConfig.class));
    if (getComponentSpec().getExtra() == null || !getComponentSpec().getExtra().containsKey(EXTRA_CONFIG))
      return Optional.empty();
    return Optional.of(yaml.load(getComponentSpec().getExtra().get(EXTRA_CONFIG)));
  }


  @Override
  public String getUapiClientDefaultUsername() {
    return UAPI_ADMIN_USERNAME;
  }

}
