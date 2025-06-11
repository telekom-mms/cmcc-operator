/*
 * Copyright (c) 2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.components.*;
import com.tsystemsmms.cmcc.cmccoperator.crds.*;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.MilestoneListener;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategy;
import io.fabric8.kubernetes.api.model.apps.StatefulSetUpdateStrategyBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.readiness.Readiness;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.*;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.*;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Basis for CAE, headless or similar tomcat spring boot based rendering engines (using a Content-Server and Solr)
 */
@Slf4j
public abstract class AbstractRenderingCorbaComponent extends CorbaComponent implements HasMongoDBClient, HasSolrClient, HasService, MilestoneListener {

  public static final String KIND_LIVE = "live";
  public static final String KIND_PREVIEW = "preview";
  public static final String SOLR_COLLECTION_LIVE = "live";
  public static final String SOLR_COLLECTION_PREVIEW = "preview";
  public static final String EXTRA_REPLICAS = "replicas";

  private int numOfStatefulSets = -1;
  private Integer currentStatefulSetIndex = null; // null to indicate: at this moment index is not relevant

  protected AbstractRenderingCorbaComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    this(kubernetesClient, targetState, componentSpec, concatOptional(componentSpec.getType(), componentSpec.getKind()));
  }

  protected AbstractRenderingCorbaComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepoName) {
    super(kubernetesClient, targetState, componentSpec, imageRepoName);

    String solrCsr;

    if (getComponentSpec().getKind() == null) {
      throw new CustomResourceConfigError("kind must be set to either " + KIND_LIVE + " or " + KIND_PREVIEW);
    }
    solrCsr = switch (componentSpec.getKind()) {
        case KIND_LIVE -> HasSolrClient.getSolrClientSecretRefName(SOLR_COLLECTION_LIVE, SOLR_CLIENT_SERVER_FOLLOWER);
        case KIND_PREVIEW -> HasSolrClient.getSolrClientSecretRefName(SOLR_COLLECTION_PREVIEW, SOLR_CLIENT_SERVER_LEADER);
        default -> throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LIVE + " or " + KIND_PREVIEW);
    };
    setDefaultSchemas(Map.of(
            MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
            SOLR_CLIENT_SECRET_REF_KIND, solrCsr,
            UAPI_CLIENT_SECRET_REF_KIND, "webserver"
    ));

    applyReplicas(componentSpec);
  }

  @Override
  public Component updateComponentSpec(ComponentSpec newCs) {
    super.updateComponentSpec(newCs);
    applyReplicas(newCs);
    return this;
  }

  private void applyReplicas(ComponentSpec componentSpec) {
    if (componentSpec.getExtra().containsKey(EXTRA_REPLICAS)) {
      setReplicas(Integer.parseInt(componentSpec.getExtra().get(EXTRA_REPLICAS)));
    }

    // honor and track the global scaling if necessary
    if (componentSpec.getKind().equals(KIND_LIVE) &&
            !Boolean.TRUE.equals(componentSpec.getForceMls()) &&
            getCmcc().getSpec().getScalingTarget().isType(componentSpec.getType())) {
      configureGlobalScaling(componentSpec.getType());
    }
  }

  private void configureGlobalScaling(String type) {
    var min = getInt(getCmcc().getSpec().getWith().getDelivery().getMinCae());
    var max = getInt(getCmcc().getSpec().getWith().getDelivery().getMaxCae());
    if (ScalingTarget.headless.isType(type)) {
      min = getInt(getCmcc().getSpec().getWith().getDelivery().getMinHeadless());
      max = getInt(getCmcc().getSpec().getWith().getDelivery().getMaxHeadless());
    }

    var globalScaling = getInt(getCmcc().getSpec().getScaling());
    if (globalScaling >= min && globalScaling <= max) {
      setReplicas(globalScaling);
    }

    getCmcc().getSpec().setScaling(new IntOrString(getReplicas()));
    getCmcc().getStatus().setScaling(getReplicas());
    getCmcc().getStatus().setScaledMessage(MessageFormat.format("{0}:{1} ({2}↔{3})", type, getReplicas(), min, max));

    // example scalingSelector: cmcc.tsystemsmms.com/kind=live,cmcc.tsystemsmms.com/type=cae
    getCmcc().getStatus().setScalingSelector(
            super.getSelectorLabels().entrySet()
                    .stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")));
  }

  @Override
  public void requestRequiredResources() {
    super.requestRequiredResources();
    getMongoDBClientSecretRef();
    getSolrClientSecretRef();
  }

  @Override
  public int getCurrentReplicas() {
    if (getTargetState().isUpgrading() && useRls()) {
      // upgrade path
      if (reachedRlsMilestone() && !reachedMyMilestone() &&
              hasToConsiderStsIndex() && currentStatefulSetIndex > 0) {
        // STSs above #0 are based on RLSs that are NOW being rebooted with the new version
        // take the CAEs down for now, next Milestone will bring them back (when RLSs are ready again)
        return 0;
      }

      // keep existing "old" live CAE/Headless pods running during upgrade phases
      return getReplicas();
    }
    return super.getCurrentReplicas();
  }

  @Override
  public int getReplicas() {
    if (hasToConsiderStsIndex()) {
      return getReplicasPerSts();
    }
    return super.getReplicas();
  }

  private int getReplicasPerSts() {
    int sumOfAllStsReplicas = super.getReplicas();
    int numOfSts = getNumOfStatefulSets();
    int x = sumOfAllStsReplicas % numOfSts;

    int replicasPerSts = (int) (((double)sumOfAllStsReplicas) / ((double)numOfSts));

    if (x != 0 && x<=currentStatefulSetIndex) {
      replicasPerSts=replicasPerSts + 1;
    }

    return replicasPerSts;
  }

  @Override
  public Map<String, String> getSelectorLabels() {
    return adjustNameLabelsIfNeeded(super.getSelectorLabels());
  }

  @Override
  public Map<String, String> getSelectorLabels(String... extra) {
    return adjustNameLabelsIfNeeded(super.getSelectorLabels(extra));
  }

  @Override
  public Map<String, String> getSelectorLabelsWithVersion() {
    return adjustNameLabelsIfNeeded(super.getSelectorLabelsWithVersion());
  }

  @Override
  public Map<String, String> getSelectorLabelsWithVersion(String... extra) {
    return adjustNameLabelsIfNeeded(super.getSelectorLabelsWithVersion(extra));
  }

  private Map<String, String> adjustNameLabelsIfNeeded(Map<String, String> result) {
    if (hasToConsiderStsIndex() && !result.containsKey("cmcc.tsystemsmms.com/name-of-sts")) {
      var nameWithNumber = result.get("cmcc.tsystemsmms.com/name");
      result.put("cmcc.tsystemsmms.com/name", nameWithNumber.replace(super.getBaseResourceName() + "-" + currentStatefulSetIndex, super.getBaseResourceName()));
      result.put("cmcc.tsystemsmms.com/name-of-sts", nameWithNumber);
      result.put("cmcc.tsystemsmms.com/sts-index", Integer.toString(currentStatefulSetIndex));
    }
    return result;
  }

  @Override
  public List<HasMetadata> buildResources() {

    List<HasMetadata> resources = new LinkedList<>();

    resources.add(buildService());
    resources.addAll(this.buildIngressResources());

    if (useRls()) {
      try {
        for (this.currentStatefulSetIndex = 0; currentStatefulSetIndex < getNumOfStatefulSets(); currentStatefulSetIndex++) {
          resources.add(buildStatefulSet(getCurrentReplicas(), getCurrentPartition()));
        }
      } finally {
        this.currentStatefulSetIndex = null;
      }
    } else {
      resources.add(buildStatefulSet());
    }

    if (Boolean.TRUE.equals(getCmcc().getSpec().getWith().getJsonLogging())) {
      resources.add(buildLoggingConfigMap());
    }
    return resources;
  }

  private int getCurrentPartition() {
    int partition = 0;
    if (useRls() && getTargetState().isUpgrading()) {
      // keep old CAEs alive during the first upgrade phases
      partition = getReplicas();

      if (reachedMyMilestone() && !reachedReady() && currentStatefulSetIndex > 0) {
          // except: all STSs > #0 should completely be replaced with new version
          partition = 0;
      }

      log.info("[{}] Versions of {} deployed with partition: {}", getTargetState().getContextForLogging(),
                getTargetState().getResourceNameFor(this), partition);
    }
    return partition;
  }

  // the following *two Methods* are a workaround for the missing "maxUnavailability" property
  // which would allow all PODs to be replaced simultaneously
  // currently the solution is: Add "OnDelete" strategy and then kill them all at once
  // -> new versions are deployed at once
  // -------------------------
  @Override
  protected StatefulSetUpdateStrategy getUpdateStrategy(int partition) {
    var strategy = super.getUpdateStrategy(partition);
    if (useRls() &&
            getNumOfStatefulSets() > 1 &&
            reachedMyMilestone() &&
            !reachedReady() &&
            currentStatefulSetIndex == 0) {
      return new StatefulSetUpdateStrategyBuilder().withType("OnDelete").build();
    }

    return strategy;
  }
  @Override
  public void onMilestoneReached(Milestone reachedMilestone, Milestone previousMilestone) {
    if (reachedMilestone == Ready && previousMilestone == DeliveryServicesReady &&
            getTargetState().isUpgrading() && useRls() &&
            getNumOfStatefulSets() > 1) {
      // last step of  Multi-RLS-upgrade: Kill all remaining "old" CAEs
      // -> faster than letting the RollingUpdate work on them one by one
      log.debug("[{}] Last upgrade step: killing remaining delivery pods with old version {}",
              getTargetState().getContextForLogging(), getVersioningTargetState().getVersion());
      try {
        currentStatefulSetIndex = 0;
        getKubernetesClient().pods()
                .inNamespace(getCmcc().getMetadata().getNamespace())
                .withLabels(getSelectorLabels())
                .delete();
      } finally {
        currentStatefulSetIndex = null;
      }
    }
  }
  // -------------------------

  // The following two methods are: Workaround for "Bad Gateway" issues during POD deletion and
  // de-registration in nginx ingress controller, see i.e. here:
  // https://medium.com/codecademy-engineering/kubernetes-nginx-and-zero-downtime-in-production-2c910c6a5ed8
  // -------------------------
  @Override
  public long getTerminationGracePeriodSeconds() {
    return 20L;
  }
  @Override
  protected LifecycleHandler getLifecyclePreStop() {
    return new LifecycleHandlerBuilder()
            .withExec(new ExecActionBuilder()
                    .withCommand("bash", "-ec", "sleep 10")
                    .build())
            .build();
  }
  // -------------------------

  @Override
  public String getBaseResourceName() {
    var result = super.getBaseResourceName();
    if (hasToConsiderStsIndex()) {
      result = concatOptional(result, Integer.toString(currentStatefulSetIndex));
    }
    return result;
  }

  @Override
  protected ComponentState getStatefulSetState(String name) {
    var stsList = getKubernetesClient().apps()
            .statefulSets()
            .inNamespace(getCmcc().getMetadata().getNamespace())
            .withLabel("cmcc.tsystemsmms.com/name", name)
            .list().getItems();

    if (stsList.isEmpty() || !stsList.stream().allMatch(Readiness.getInstance()::isReady)) {
      return ComponentState.WaitingForReadiness;
    }

    var stsStateList = stsList.stream()
            .map(this::getStatefulSetState)
            .toList();

    if (stsStateList.stream().allMatch(s -> s.isReady().orElse(true))) { return ComponentState.Ready; }
    return stsStateList.stream().filter(s -> !s.isReady().orElse(true)).findFirst().orElse(ComponentState.NotApplicable);
  }

  @Override
  protected ComponentState getStatefulSetState(StatefulSet sts) {
    if (isLive() && getNumOfStatefulSets() > 1) {
      try {
        var index = Optional.ofNullable(sts.getMetadata().getLabels().get("cmcc.tsystemsmms.com/sts-index"));
        this.currentStatefulSetIndex = Integer.parseInt(index.orElse("0"));
        return super.getStatefulSetState(sts);
      } finally {
        this.currentStatefulSetIndex = null;
      }
    }
    return super.getStatefulSetState(sts);
  }

  @Override
  protected PodAntiAffinity getPodAntiAffinity() {
    var antiAffinityRules = new LinkedList<WeightedPodAffinityTerm>();

    if (isLive()) {
      antiAffinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_CMS, 50));
    }

    return new PodAntiAffinityBuilder()
            .withPreferredDuringSchedulingIgnoredDuringExecution(antiAffinityRules.stream().filter(Objects::nonNull).toList())
            .build();
  }

  @Override
  protected boolean needsTransformedBlobCache() {
    return true; // CAEs and Headless instances need that cache volume mount
  }

  protected abstract Collection<? extends HasMetadata> buildIngressResources();

  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = super.getEnvVars();
    env.addAll(getMongoDBEnvVars());
    env.addAll(getSolrEnvVars(getComponentSpec().getType()));
    return env;
  }

  @Override
  public Map<String, String> getSpringBootProperties() {
    Map<String, String> properties = super.getSpringBootProperties();

    properties.putAll(Map.of(
            "server.tomcat.accesslog.enabled", "true",
            "server.tomcat.accesslog.directory", "dev",
            "server.tomcat.accesslog.prefix", "stdout",
            "server.tomcat.accesslog.suffix", "",
            "server.tomcat.accesslog.file-date-format", "",
            "server.tomcat.accesslog.pattern", "[ACCESS] %l %t %D %F %B %S",
            "server.tomcat.accesslog.rotate", "false",
            "com.coremedia.transform.blobCache.basePath", MOUNT_TRANSFORMED_BLOBCACHE
    ));

    if (isLive()) {
      if (useMls()) {
        properties.put("repository.url", getTargetState().getServiceUrlFor("content-server", "mls"));
      } else {
        String url = getTargetState().getServiceUrlFor("content-server", "rls");
        if (hasToConsiderStsIndex()) {
          url = replaceServiceWithPod();
        }
        properties.put("repository.url", url);
      }

      for (SiteMapping siteMapping : getSpec().getSiteMappings()) {
        String fqdn = concatOptional(
                isEmpty(getDefaults().getNamePrefixForIngressDomain()) ? getDefaults().getNamePrefix() : getDefaults().getNamePrefixForIngressDomain(),
                siteMapping.getHostname(),
                isEmpty(getDefaults().getNameSuffixForIngressDomain()) ? getDefaults().getNameSuffix() : getDefaults().getNameSuffixForIngressDomain()
        ) + "." + getDefaults().getIngressDomain();

        if (siteMapping.getFqdn() != null && !siteMapping.getFqdn().isEmpty()) {
          fqdn = siteMapping.getFqdn();
        }

        if (!Strings.isBlank(siteMapping.getFqdn())) {
          fqdn = siteMapping.getFqdn();
        }
        String protocol = Utils.defaultString(siteMapping.getProtocol(), getDefaults().getSiteMappingProtocol(), "//");
        properties.put("blueprint.site.mapping." + siteMapping.getPrimarySegment(), protocol + fqdn);
        for (String segment : siteMapping.getAdditionalSegments()) {
          properties.put("blueprint.site.mapping." + segment, protocol + fqdn);
        }
      }
    } else {
      properties.putAll(getSiteMappingProperties());
    }
    return properties;
  }

  private String replaceServiceWithPod() {
    var resourceName = getRlsComponent()
            .map(Component::getBaseResourceName)
            .orElse("replication-live-server");

    // change servicename to qualified hostname of the POD with index "number"
    // replication-live-server -> replication-live-server-0.replication-live-server
    return getTargetState().getServiceUrlFor("content-server", "rls")
            .replace(resourceName, resourceName + "-" + currentStatefulSetIndex + "." + resourceName);
  }

  @Override
  public List<ContainerPort> getContainerPorts() {
    return List.of(
            new ContainerPortBuilder()
                    .withName("http")
                    .withContainerPort(8080)
                    .build(),
            new ContainerPortBuilder()
                    .withName("management")
                    .withContainerPort(8081)
                    .build()
    );
  }

  @Override
  public List<ServicePort> getServicePorts() {
    return List.of(
            new ServicePortBuilder().withName("http").withPort(8080).withNewTargetPort("http").build(),
            new ServicePortBuilder().withName("management").withPort(8081).withNewTargetPort("management").build());
  }

  @Override
  public Optional<ClientSecretRef> getSolrClientSecretRef() {
      return switch (getComponentSpec().getKind()) {
          case KIND_LIVE ->
                  getSolrClientSecretRef(HasSolrClient.getSolrClientSecretRefName(SOLR_COLLECTION_LIVE, SOLR_CLIENT_SERVER_FOLLOWER));
          case KIND_PREVIEW ->
                  getSolrClientSecretRef(HasSolrClient.getSolrClientSecretRefName(SOLR_COLLECTION_PREVIEW, SOLR_CLIENT_SERVER_LEADER));
          default -> Optional.empty();
      };
  }

  private Optional<Component> getRlsComponent() {
    return getTargetState().getComponentCollection().findAllOfTypeAndKind(CONTENT_SERVER, KIND_RLS).findFirst();
  }

  private Integer getNumOfStatefulSets() {
    // The amount of stateful sets is equal to the amount of pods of the rls or 1 as default.
    if (this.numOfStatefulSets > 0) {
      return this.numOfStatefulSets;
    }
    var rls = getRlsComponent();
    var num = rls.map(Component::getReplicas).orElse(1);
    if (rls.isPresent()) {
      this.numOfStatefulSets = num;
    }
    // Fallback if no RLS is (yet) found
    return num;
  }

  private boolean hasToConsiderStsIndex() {
    return useRls() && getNumOfStatefulSets() > 1 && currentStatefulSetIndex != null;
  }

  private boolean isLive() {
    return getComponentSpec().getKind().equals(KIND_LIVE);
  }

  private boolean useMls() {
    return isLive() && !useRls();
  }

  private boolean useRls() {
    return isLive() && getInt(getCmcc().getSpec().getWith().getDelivery().getRls()) > 0 && !getComponentSpec().getForceMls();
  }

  private boolean reachedRlsMilestone() {
    var rlsMilestone = getRlsComponent().map(r -> r.getComponentSpec().getMilestone()).orElse(null);
    return compareTo(getCmcc().getStatus().getMilestone(), rlsMilestone) >= 0;
  }
}
