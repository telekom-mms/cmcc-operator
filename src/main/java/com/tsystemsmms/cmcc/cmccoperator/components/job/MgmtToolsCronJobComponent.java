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
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentState;
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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

/**
 * Run a Management Tools command regularly using an k8s CronJob.
 */
public class MgmtToolsCronJobComponent extends SpringBootComponent implements HasUapiClient {
  public static final String EXTRA_CONFIG = "config";

  private MgmtCronJobConfig mgmtCronJobConfig = null;

  public MgmtToolsCronJobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, "management-tools");
  }

  @Override
  public List<HasMetadata> buildResources() {
    return List.of(buildJob());
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    super.updateComponentSpec(newCs);
    if (mgmtCronJobConfig != null) {
      mgmtCronJobConfig = getMgmtCronJobConfigFromExtra();
    }
    return this;
  }

  private CronJob buildJob() {
    getMgmtCronJobConfig();
    return new CronJobBuilder()
            .withMetadata(getResourceMetadata())
            .withSpec(new CronJobSpecBuilder()
                    .withSchedule(mgmtCronJobConfig.cron)
                    .withTimeZone(mgmtCronJobConfig.timezone)
                    .withFailedJobsHistoryLimit(3)
                    .withSuccessfulJobsHistoryLimit(1)
                    .withJobTemplate(new JobTemplateSpecBuilder()
                            .withSpec(new JobSpecBuilder()
                                    .withActiveDeadlineSeconds(mgmtCronJobConfig.activeDeadlineSeconds)
                                    .withBackoffLimit(3)
                                    .withCompletions(1)
                                    .withParallelism(1)
                                    .withTemplate(new PodTemplateSpecBuilder()
                                            .withMetadata(new ObjectMetaBuilder()
                                                    .withAnnotations(getAnnotations())
                                                    .withLabels(getPodLabels())
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
  public Map<String, String> getSelectorLabels() {
    Map<String, String> labels = super.getSelectorLabels();
    labels.putAll(getJobLabels());
    return labels;
  }

  public static Map<String, String> getJobLabels() {
    return Map.of("cmcc.tsystemsmms.com/cronjob", MgmtToolsCronJobComponent.class.getSimpleName().replaceAll("Component$", ""));
  }

  public ComponentState getState() {
    // Once the object has been created, it is ready. Any execution failures are not tracked by the operator.
    return ComponentState.Ready;
  }

  private MgmtCronJobConfig  getMgmtCronJobConfigFromExtra() {
    Yaml yaml = new Yaml(new Constructor(MgmtCronJobConfig.class, new LoaderOptions()));
    if (getComponentSpec().getExtra() == null || !getComponentSpec().getExtra().containsKey(EXTRA_CONFIG))
      throw new CustomResourceConfigError("Must specify " + EXTRA_CONFIG + " with job parameters for job \"" + getSpecName() + "\"");
    return yaml.load(getComponentSpec().getExtra().get(EXTRA_CONFIG));
  }

  private MgmtCronJobConfig getMgmtCronJobConfig() {
    if (mgmtCronJobConfig == null) {
      mgmtCronJobConfig = getMgmtCronJobConfigFromExtra();
    }
    return mgmtCronJobConfig;
  }

  @Override
  public String getUapiClientDefaultUsername() {
    return UAPI_ADMIN_USERNAME;
  }

}
