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

import com.tsystemsmms.cmcc.cmccoperator.crds.*;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.VersioningTargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.SimpleExecListener;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.readiness.Readiness;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.Ready;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.compareTo;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
public abstract class AbstractComponent implements Component {
  @Getter
  final TargetState targetState;
  @Getter
  final ComponentSpec componentSpec;
  @Getter
  final KubernetesClient kubernetesClient;
  @Getter
  final String namespace;
  @Getter
  final String specName;
  @Getter
  @Setter
  String imageRepository;
  @Getter
  @Setter
  private int replicas = 1;

  @Getter
  final Map<String, String> schemas = new HashMap<>();

  public AbstractComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
    this.kubernetesClient = kubernetesClient;
    this.targetState = targetState;
    this.namespace = getCmcc().getMetadata().getNamespace();
    this.componentSpec = new ComponentSpec(componentSpec);
    this.specName = componentSpec.getName() != null && !componentSpec.getName().isEmpty() ? componentSpec.getName() : componentSpec.getType();
    this.imageRepository = imageRepository;
    schemas.putAll(componentSpec.getSchemas());
  }

  @Override
  public boolean isBuildResources() {
    return Milestone.compareTo(Milestone.Never, getComponentSpec().getMilestone()) != 0;
  }

  @Override
  public int getCurrentReplicas() {
    return Milestone.compareTo(getCmcc().getStatus().getMilestone(), getComponentSpec().getMilestone()) >= 0 ?
            getReplicas() :
            0;
  }

  public VersioningTargetState getVersioningTargetState() {
    if (targetState instanceof VersioningTargetState state) {
      return state;
    }
    return null;
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    if (!ComponentCollection.equalsSpec(getComponentSpec(), newCs)) {
      throw new IllegalArgumentException("Internal error: cannot update existing component because type/kind/name do not match");
    }
    componentSpec.update(newCs);
    schemas.putAll(componentSpec.getSchemas());

    return this;
  }

  /**
   * Returns the custom resource this component is defined in,
   *
   * @return the custom resource
   */
  public CustomResource getCmcc() {
    return targetState.getCmcc();
  }

  /**
   * Shorthand for getCmcc().getSpec(),
   *
   * @return the spec
   */
  public CoreMediaContentCloudSpec getSpec() {
    return targetState.getCmcc().getSpec();
  }

  /**
   * Shorthand for getSpec().getDefaults().
   *
   * @return the spec
   */
  public ComponentDefaults getDefaults() {
    return targetState.getCmcc().getSpec().getDefaults();
  }

  @Override
  public String getBaseResourceName() {
    return getComponentName(null);
  }

  /**
   * Build the components name. If set, the prefix, the name and the kind will be concatenated together. The optional
   * kind parameter overrides the spec supplied one if set.
   *
   * @param kind optional override for the getSpec().getKind()
   * @return component name
   */
  public String getComponentName(String kind) {
    if (kind == null || kind.isBlank())
      kind = getComponentSpec().getKind();
    return concatOptional(
            getSpecName(),
            kind);
  }

  /**
   * Get the fully qualified image name for the main container of this component.
   *
   * @return the name of the image for the main container for the main pod
   */
  public String getImage() {
    ImageSpec componentDefault = getDefaultImage();
    ImageSpec defaultImage = Objects.requireNonNullElse(getDefaults().getImage(), new ImageSpec());
    ImageSpec csImage = Objects.requireNonNullElse(componentSpec.getImage(), new ImageSpec());
    ImageSpec spec = new ImageSpec();

    spec.setRegistry(defaultString(
            csImage.getRegistry(),
            componentDefault.getRegistry(),
            defaultImage.getRegistry(),
            "coremedia"
    ));
    spec.setRepository(defaultString(
            csImage.getRepository(),
            componentDefault.getRepository(),
            defaultImage.getRepository(),
            imageRepository
    ));
    spec.setTag(defaultString(
            csImage.getTag(),
            componentDefault.getTag(),
            defaultImage.getTag(),
            "latest"
    ));

    return spec.getRegistry() + "/" + spec.getRepository() + ":" + spec.getTag();
  }

  public Map<String, String> getAnnotations() {
    HashMap<String, String> annotations = new HashMap<>(getSpec().getDefaults().getAnnotations());
    annotations.putAll(getComponentSpec().getAnnotations());
    return annotations;
  }

  @Override
  public ResourceMgmt getResourceManagement() {
    return getComponentSpec().getResources();
  }

  /**
   * Get the image pull policy for the images of the main container of this component.
   *
   * @return image pull policy
   */
  public String getImagePullPolicy() {
    ImageSpec defaultImage = Objects.requireNonNullElse(getDefaults().getImage(), new ImageSpec());
    ImageSpec csImage = Objects.requireNonNullElse(componentSpec.getImage(), new ImageSpec());

    return defaultString(
            csImage.getPullPolicy(),
            defaultImage.getPullPolicy(),
            "IfNotPresent");
  }

  /**
   * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
   *
   * @return list of labels
   */
  public Map<String, String> getSelectorLabels() {
    HashMap<String, String> labels = getTargetState().getSelectorLabels();
    labels.put("cmcc.tsystemsmms.com/name", getTargetState().getResourceNameFor(this));
    labels.put("cmcc.tsystemsmms.com/type", getComponentSpec().getType());
    if (!isEmpty(getComponentSpec().getKind())) {
      labels.put("cmcc.tsystemsmms.com/kind", getComponentSpec().getKind());
    }
    return labels;
  }

  /**
   * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
   *
   * @return list of labels
   */
  public Map<String, String> getSelectorLabelsWithVersion() {
    HashMap<String, String> labels = getTargetState().getSelectorLabelsWithVersion();
    labels.putAll(getSelectorLabels());
    return labels;
  }

  /**
   * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
   *
   * @return list of labels
   */
  public Map<String, String> getSelectorLabels(String... extra) {
    HashMap<String, String> labels = getTargetState().getSelectorLabels();
    labels.put("cmcc.tsystemsmms.com/type", componentSpec.getType());
    labels.put("cmcc.tsystemsmms.com/name", getTargetState().getResourceNameFor(this, extra));
    return labels;
  }

  /**
   * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
   *
   * @return list of labels
   */
  public Map<String, String> getSelectorLabelsWithVersion(String... extra) {
    HashMap<String, String> labels = getTargetState().getSelectorLabelsWithVersion();
    labels.putAll(getSelectorLabels(extra));
    return labels;
  }

  public Map<String, String> getPodLabels() {
    return getSelectorLabelsWithVersion();
  }

  /**
   * List of ports that the main container of the main pod provides.
   *
   * @return list of ports
   */
  public List<ContainerPort> getContainerPorts() {
    return Collections.emptyList();
  }

  /**
   * List of ports that the service for the component.T his is likely a mirror of getContainerPorts.
   *
   * @return list of ports
   */
  public List<ServicePort> getServicePorts() {
    return Collections.emptyList();
  }

  protected String getServiceName() {
    return getTargetState().getServiceNameFor(this);
  }

  /**
   * Create the StatefulSet for reconciliation.
   *
   * @return the created StatefulSet.
   */
  public StatefulSet buildStatefulSet() {
    return buildStatefulSet(getCurrentReplicas());
  }

  /**
   * Create the StatefulSet for reconciliation with the given replicas.
   *
   * @return the created StatefulSet.
   */
  public StatefulSet buildStatefulSet(int replicas) {
    return buildStatefulSet(replicas, 0);
  }

  /**
   * Create the StatefulSet for reconciliation with the given replicas and env vars.
   *
   * @return the created StatefulSet.
   */
  public StatefulSet buildStatefulSet(int replicas, EnvVarSet env) {
    return buildStatefulSet(replicas, env, 0);
  }

  /**
   * Create the StatefulSet for reconciliation with the given replicas and setting the partition of its UpdateStrategy.
   *
   * @return the created StatefulSet.
   */
  public StatefulSet buildStatefulSet(int replicas, int partition) {
    EnvVarSet env = getEnvVars();
    env.addAll(getComponentSpec().getEnv());
    return buildStatefulSet(replicas, env, partition);
  }

  /**
   * Create the StatefulSet for reconciliation with the given replicas and setting the partition of its UpdateStrategy.
   * Also overrides the given version in pod labels (if param is not null).
   *
   * @return the created StatefulSet.
   */
  public StatefulSet buildStatefulSet(int replicas, EnvVarSet env, int partition) {
    return new StatefulSetBuilder()
            .withMetadata(getResourceMetadata())
            .withSpec(new StatefulSetSpecBuilder()
                    .withReplicas(replicas)
                    .withServiceName(getServiceName())
                    .withSelector(new LabelSelectorBuilder()
                            .withMatchLabels(getSelectorLabels())
                            .build())
                    .withTemplate(new PodTemplateSpecBuilder()
                            .withMetadata(new ObjectMetaBuilder()
                                    .withAnnotations(getAnnotations())
                                    .withLabels(getPodLabels())
                                    .build())
                            .withSpec(new PodSpecBuilder()
                                    .withContainers(buildContainers(env))
                                    .withInitContainers(getInitContainers())
                                    .withSecurityContext(getPodSecurityContext())
                                    .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                    .withVolumes(getVolumes())
                                    .withAffinity(getAffinity())
                                    .build())
                            .build())
                    .withVolumeClaimTemplates(getVolumeClaims())
                    .withPodManagementPolicy("Parallel")
                    .withUpdateStrategy(getUpdateStrategy(partition))
                    .build())
            .build();
  }

  protected StatefulSetUpdateStrategy getUpdateStrategy(int partition) {
    return new StatefulSetUpdateStrategyBuilder().withRollingUpdate(
            new RollingUpdateStatefulSetStrategyBuilder()
                    .withPartition(partition)
                    // currently alpha, https://kubernetes.io/docs/reference/command-line-tools-reference/feature-gates/
                    // needs the feature flag MaxUnavailableStatefulSet
                    .withMaxUnavailable(new IntOrString("100%"))
                    .build())
            .build();
  }

  protected Affinity getAffinity() {
    Affinity affinity = getComponentSpec().getAffinity();
    if (affinity == null && getCmcc().getSpec().getWith().getDefaultAffinityRules()) {
      PodAffinity podAffinity = getPodAffinity();
      PodAntiAffinity podAntiAffinity = getPodAntiAffinity();
      NodeAffinity nodeAffinity = getNodeAffinity();
      if (null != podAffinity || null != podAntiAffinity || null != nodeAffinity) {
        return new AffinityBuilder()
                .withPodAffinity(podAffinity)
                .withPodAntiAffinity(podAntiAffinity)
                .withNodeAffinity(nodeAffinity)
                .build();
      }
    }
    affinity = handleLabelReferences(affinity);
    return affinity;
  }

  protected NodeAffinity getNodeAffinity() {
    return null;
  }

  protected PodAntiAffinity getPodAntiAffinity() {
    return null;
  }

  protected PodAffinity getPodAffinity() {
    return null;
  }

  protected WeightedPodAffinityTerm createAffinityToComponent(String type, String kind, int weight) {
    var component = getTargetState().getComponentCollection().findAllOfTypeAndKind(type, kind).findFirst().orElse(null);
    return createAffinityToComponent(component, weight);
  }

  protected WeightedPodAffinityTerm createAffinityToComponent(String type, int weight) {
    var component = getTargetState().getComponentCollection().findAllOfType(type).findFirst().orElse(null);
    return createAffinityToComponent(component, weight);
  }

  protected WeightedPodAffinityTerm createAffinityToComponent(Component component, int weight) {
    if (component != null) {
      return new WeightedPodAffinityTermBuilder()
              .withPodAffinityTerm(new PodAffinityTermBuilder()
                      .withLabelSelector(new LabelSelectorBuilder()
                              .withMatchLabels(component.getSelectorLabels())
                              .build())
                      .withTopologyKey(getSpec().getDefaults().getAffinityTopology())
                      .build())
              .withWeight(weight)
              .build();
    }
    return null;
  }

  public long getTerminationGracePeriodSeconds() {
    return 5L;
  }

  public List<Container> buildContainers() {
    EnvVarSet env = getEnvVars();
    env.addAll(getComponentSpec().getEnv());
    return buildContainers(env);
  }

  public List<Container> buildContainers(EnvVarSet env) {
    LinkedList<Container> containers = new LinkedList<>();
    ResourceRequirements resourceRequirements = getSpec().getWith().getResources() ? ResourceMgmt.withDefaults(getDefaults().getResources(), getResourceManagement()).getResources() : new ResourceRequirements();

    containers.add(new ContainerBuilder()
            .withName(specName)
            .withImage(getImage())
            .withImagePullPolicy(getImagePullPolicy())
            .withResources(resourceRequirements)
            .withSecurityContext(getSecurityContext())
            .withPorts(getContainerPorts())
            .withArgs(getComponentSpec().getArgs())
            .withEnv(env.toList())
            .withVolumeMounts(getVolumeMounts())
            .withStartupProbe(getStartupProbe())
            .withLivenessProbe(getLivenessProbe())
            .withReadinessProbe(getReadinessProbe())
            .withLifecycle(new LifecycleBuilder().withPreStop(getLifecyclePreStop()).build())
            .build());

    return containers;
  }

  protected LifecycleHandler getLifecyclePreStop() {
    return null;
  }

  public PersistentVolumeClaim getPersistentVolumeClaim(String name, Quantity size) {
    String sc = getDefaults().getStorageClass();
    if (sc.isEmpty()) {
      sc = null;
    }
    return new PersistentVolumeClaimBuilder()
            .withMetadata(getResourceMetadataForName(name))
            .withSpec(new PersistentVolumeClaimSpecBuilder()
                    .withAccessModes("ReadWriteOnce")
                    .withResources(new VolumeResourceRequirementsBuilder()
                            .withRequests(Map.of("storage", size))
                            .build())
                    .withStorageClassName(sc)
                    .build())
            .build();
  }

  /**
   * @return the version info "resource-name-ready" (no dots, spaces, etc.).
   */
  protected String getVersionSuffix() {
    return getVersionSuffix(null);
  }

  /**
   * @return the version info "resource-name-ready" (no dots, spaces, etc.).
   */
  protected String getVersionSuffix(String version) {
    if (!targetState.isVersioning()) {
      return "";
    }

    version = version == null ? getTargetState().getVersion() : version;

    return version
            .replace('.', '-')
            .replace(' ', '_');
  }

  public EnvVarSet getEnvVars() {
    EnvVarSet env = new EnvVarSet();
    env.addAll(getCmcc().getSpec().getDefaults().getEnv());
    return env;
  }

  public List<Container> getInitContainers() {
    return new LinkedList<>();
  }

  public abstract Probe getStartupProbe();

  public abstract Probe getLivenessProbe();

  public abstract Probe getReadinessProbe();

  /**
   * Defines volumes for the main pod.
   *
   * @return list of volumes
   */
  public List<Volume> getVolumes() {
    List<Volume> volumes = new LinkedList<>();

    volumes.add(new VolumeBuilder()
                    .withName("tmp")
                    .withEmptyDir(new EmptyDirVolumeSource())
                    .build());
    volumes.add(new VolumeBuilder()
                    .withName("var-tmp")
                    .withEmptyDir(new EmptyDirVolumeSource())
                    .build());

    volumes.addAll(getComponentSpec().getVolumes());

    return volumes;
  }

  /**
   * Returns the list of persistent volume claims. This can either be used to build resources directly, or as a
   * template in a StatefulSet.
   *
   * @return List of PersistentVolumeClaims
   */
  public List<PersistentVolumeClaim> getVolumeClaims() {
    return new LinkedList<>();
  }

  /**
   * Defines volume mounts for the main container of the main pod.
   *
   * @return list of volume mounts
   */
  public List<VolumeMount> getVolumeMounts() {
    List<VolumeMount> volumeMounts = new LinkedList<>();

    volumeMounts.add(new VolumeMountBuilder()
            .withName("tmp")
            .withMountPath("/tmp")
            .build());
    volumeMounts.add(new VolumeMountBuilder()
            .withName("var-tmp")
            .withMountPath("/var/tmp")
            .build());

    volumeMounts.addAll(getComponentSpec().getVolumeMounts());

    return volumeMounts;
  }

  /**
   * Convenience method to access one of the volume size specifications.
   *
   * @param getter lambda returning one of the volume sizes
   * @return volume size string converted to a Quantity
   */
  public Quantity getVolumeSize(Function<ComponentSpec.VolumeSize, String> getter) {
    return getVolumeSizeOrDefault(getter);
  }

  /**
   * Returns a Quantity as selected by the lambda, either from the component spec, or if that isn't set, from
   * the component defaults, or if that isn't set, a hardcoded 8Gi.
   *
   * @param getter lambda returning one of the volume sizes
   * @return volume size string converted to a Quantity
   */
  public Quantity getVolumeSizeOrDefault(Function<ComponentSpec.VolumeSize, String> getter) {
    return new Quantity(Objects.requireNonNullElse(getter.apply(getComponentSpec().getVolumeSize()), Objects.requireNonNullElse(getter.apply(getDefaults().getVolumeSize()), "8Gi")));
  }

  /**
   * Convenience method to access one of the volume size specifications and convert it to a size limit. The margin is
   * hardcoded as 10%.
   *
   * @param getter lambda returning one of the volume sizes
   * @return limit in bytes
   */
  public long getVolumeSizeLimit(Function<ComponentSpec.VolumeSize, String> getter) {
    return (long) (Quantity.getAmountInBytes(getVolumeSize(getter)).longValue() * 0.9);
  }

  /**
   * Returns the security context for a container.
   *
   * @return security context
   */
  public SecurityContext getSecurityContext() {
    return Utils.mergeObjects(SecurityContext.class,
            new SecurityContextBuilder()
                    .withReadOnlyRootFilesystem(true)
                    .build(),
            getCmcc().getSpec().getDefaults().getSecurityContext(),
            getComponentSpec().getSecurityContext());
  }

  /**
   * Returns the security context for a pod.
   *
   * @return security context
   */
  @SneakyThrows
  public PodSecurityContext getPodSecurityContext() {
    return Utils.mergeObjects(PodSecurityContext.class,
            new PodSecurityContextBuilder()
                    .withRunAsUser(getUserId())
                    .withRunAsGroup(getUserId())
                    .withFsGroup(getUserId())
                    .build(),
            getCmcc().getSpec().getDefaults().getPodSecurityContext(),
            getComponentSpec().getPodSecurityContext());
  }

  /**
   * Returns the user and group ID to be used when running containers.
   *
   * @return user ID
   */
  public long getUserId() {
    return 1_000L;
  }

  @Override
  public ComponentState getState() {
    if (getComponentSpec().getMilestone() == Milestone.Never) {
      return ComponentState.NotApplicable;
    }

    return this.getStatefulSetState();
  }

  protected ComponentState getStatefulSetState() {
    return getStatefulSetState(getTargetState().getResourceNameFor(this));
  }

  protected ComponentState getStatefulSetState(String name) {
    var stsResource = kubernetesClient.apps().statefulSets().inNamespace(getCmcc().getMetadata().getNamespace()).withName(name);

    // isReady also implies get(), ergo: check get() first
    var sts = stsResource.get();
    if (sts == null) {
      return ComponentState.NotApplicable;
    }

    if (!Readiness.getInstance().isReady(sts)) { // similar to stsResource.isReady() but saves a REST call
      return ComponentState.WaitingForReadiness;
    }

    return getStatefulSetState(sts);
  }

  protected ComponentState getStatefulSetState(StatefulSet sts) {
    var status = sts.getStatus();
    var spec = sts.getSpec();

    if (status == null) {
      if (getCurrentReplicas() > 0) {
        return ComponentState.WaitingForReadiness;
      } else {
        return ComponentState.WaitingForDeployment;
      }
    }

    if (status.getReplicas() == null) {
      if (getCurrentReplicas() > 0) {
        return ComponentState.WaitingForReadiness;
      } else {
        return ComponentState.NotApplicable;
      }
    }

    var specReplicas = spec.getReplicas() == null ? 0 : spec.getReplicas();
    var stsReplicas = status.getReplicas() == null ? 0 : status.getReplicas();
    var stsReadyReplicas = status.getReadyReplicas() == null ? 0 : status.getReadyReplicas();

    if (specReplicas != getCurrentReplicas()) {
      return ComponentState.ResourceNeedsUpdate;
    }
    if (specReplicas != stsReplicas) {
      return ComponentState.WaitingForDeployment;
    }
    if (specReplicas != stsReadyReplicas) {
      return ComponentState.WaitingForReadiness;
    }
    return ComponentState.Ready;
  }

  @Override
  public void requestRequiredResources() {
    // default component does not reference any resources.
  }

  /**
   * Set default values for schemas. The defaults are set if schemas does not contain an entry for the respective key.
   *
   * @param defaults teh default entries
   */
  public void setDefaultSchemas(Map<String, String> defaults) {
    for (Map.Entry<String, String> e : defaults.entrySet()) {
      if (!schemas.containsKey(e.getKey())) {
        schemas.put(e.getKey(), e.getValue());
      }
    }
  }

  protected ConfigMap buildLoggingConfigMap() {
    if (Boolean.TRUE.equals(getCmcc().getSpec().getWith().getJsonLogging())) {
      var configMap = kubernetesClient.configMaps()
              .inNamespace(getCmcc().getMetadata().getNamespace())
              .withName("logging-config")
              .get();

      if (configMap == null) {
        try {
          var logbackConfig = new String(new ClassPathResource("logback-config-json.xml").getInputStream().readAllBytes());
          var log4jSolrConfig = new String(new ClassPathResource("log4j2-solr-config-json.xml").getInputStream().readAllBytes());

          configMap = new ConfigMapBuilder()
                  .withMetadata(getTargetState().getResourceMetadataFor("logging-config"))
                  .withData(Map.of("logback-spring.xml", logbackConfig,
                          "log4j2-solr.xml", log4jSolrConfig))
                  .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
      }

      return configMap;
    }

    return null;
  }

  protected boolean reachedReady() {
    return compareTo(getCmcc().getStatus().getMilestone(), Ready) >= 0;
  }

  protected boolean reachedMyMilestone() {
    return compareTo(getCmcc().getStatus().getMilestone(), getComponentSpec().getMilestone()) >= 0;
  }

  protected ExecutionResult executeWebRequest(PodResource pod, String url) {
    return this.executeWebRequest(pod, url, true);
  }

  protected ExecutionResult executeWebRequest(PodResource pod, String url, boolean failOnError) {
    return this.executeWebRequest(pod, url, null, failOnError);
  }

  protected ExecutionResult executeWebRequest(PodResource pod, String url, String authCredentials, boolean failOnError) {
    return this.executeCommand(pod,   // -s = silent | -f = fail on error -> exit code
            Utils.format("curl {} -s {} '{}'", authCredentials != null ? "-u " + authCredentials : "", failOnError ? "-f" : "" , url));
  }

  protected ExecutionResult executePostWebRequest(PodResource pod, String url, String header, String data, boolean failOnError) {
    return this.executeCommand(pod,   // -s = silent | -f = fail on error -> exit code
            Utils.format("curl -X POST -s {} {} {} '{}'",
                    failOnError ? "-f" : "" ,
                    header != null ? "--header '" + header  + "'" : "",
                    data != null ? "--data '" + data + "'"  : "",
                    url));
  }

  protected ExecutionResult executeCommand(PodResource pod, String command) {
    SimpleExecListener listener = new SimpleExecListener();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ByteArrayOutputStream err = new ByteArrayOutputStream();

    var watch = pod.writingOutput(out)
            .writingError(err)
            .usingListener(listener)
            .exec("sh", "-c", command);

    listener.awaitUninterruptable();

    String stdout = out.toString(StandardCharsets.UTF_8);
    String stderr = err.toString(StandardCharsets.UTF_8).trim();

    log.info("[{}] Command finished on pod {}", getTargetState().getContextForLogging(), pod.get().getMetadata().getName());
    log.trace("[{}] Process out:\n{}", getTargetState().getContextForLogging(), stdout);
    if (!stderr.isEmpty()) {
        log.debug("[{}] Process err:\n{}", getTargetState().getContextForLogging(), stderr);
    }
    watch.close();

    return new ExecutionResult(pod, watch, stdout, stderr, watch.exitCode().getNow(null));
  }

  @AllArgsConstructor
  @SuppressWarnings("java:S1104")
  public static class ExecutionResult {
    public PodResource pod;
    public ExecWatch watch;
    public String output;
    public String errorOutput;
    public Integer exitCode;
  }

  private Affinity handleLabelReferences(Affinity affinity) {
    if (affinity != null) {
      affinity = Utils.deepClone(affinity, Affinity.class);
      handleLabelReferences(affinity.getPodAffinity());
      handleLabelReferences(affinity.getPodAntiAffinity());
      handleLabelReferences(affinity.getNodeAffinity());
    }
    return affinity;
  }

  private void handleLabelReferences(PodAffinity podAffinity) {
    if (podAffinity != null) {
      handleLabelReferences(podAffinity.getRequiredDuringSchedulingIgnoredDuringExecution());
      handleLabelReferences(podAffinity.getPreferredDuringSchedulingIgnoredDuringExecution());
    }
  }
  private void handleLabelReferences(PodAntiAffinity podAntiAffinity) {
    if (podAntiAffinity != null) {
      handleLabelReferences(podAntiAffinity.getRequiredDuringSchedulingIgnoredDuringExecution());
      handleLabelReferences(podAntiAffinity.getPreferredDuringSchedulingIgnoredDuringExecution());
    }
  }

  private void handleLabelReferences(NodeAffinity nodeAffinity) {
    if (nodeAffinity != null) {
      handleLabelReferences(nodeAffinity.getRequiredDuringSchedulingIgnoredDuringExecution());
      handleLabelReferences(nodeAffinity.getPreferredDuringSchedulingIgnoredDuringExecution());
    }
  }

  private void handleLabelReferences(List<? extends KubernetesResource> terms) {
    if (terms != null) {
      terms.forEach(this::handleLabelReferences);
    }
  }

  private void handleLabelReferences(KubernetesResource term) {
    if (term instanceof WeightedPodAffinityTerm weightedTerm) {
      handleLabelReferences(weightedTerm.getPodAffinityTerm());
    }
    if (term instanceof PodAffinityTerm podAffinityTerm) {
      handleLabelReferences(podAffinityTerm.getLabelSelector());
    }
  }

  private void handleLabelReferences(LabelSelector labelSelector) {
    if (labelSelector != null) {
      labelSelector.getMatchLabels().entrySet().forEach(entry -> {
        var value = entry.getValue();
        if (value != null && value.startsWith("{podLabel:") && value.endsWith("}")) {
          var labelKey = value.substring(10 /* length of {podLabel: */, value.length() - 1);
          entry.setValue(getSelectorLabelsWithVersion().getOrDefault(labelKey, ""));
        }
      });
    }
  }
}
