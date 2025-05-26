/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentCollection;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentState;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.ingress.UrlMappingBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.RandomString;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient.UAPI_ADMIN_USERNAME;
import static com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient.UAPI_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.utils.KubernetesUtils.getAllResourcesMatchingLabels;
import static com.tsystemsmms.cmcc.cmccoperator.utils.KubernetesUtils.isMetadataContains;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public abstract class AbstractTargetState implements TargetState {
  public static final int MAX_CONVERGENCE_LOOP = 5;

  private static final RandomString randomDatabasePassword = new RandomString(16);

  @Getter
  final KubernetesClient kubernetesClient;
  @Getter
  final CustomResource cmcc;
  @Getter
  final ComponentCollection componentCollection;
  @Getter
  final UrlMappingBuilderFactory managementUrlMappingBuilderFactory;
  @Getter
  final ResourceNamingProvider resourceNamingProvider;
  @Getter
  final ResourceReconcilerManager resourceReconcilerManager;
  @Getter
  final Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories;
  @Getter
  final YamlMapper yamlMapper;

  final Map<String, Map<String, ClientSecret>> clientSecrets = new HashMap<>();

  protected AbstractTargetState(BeanFactory beanFactory,
                             KubernetesClient kubernetesClient,
                             ResourceNamingProviderFactory resourceNamingProviderFactory,
                             ResourceReconcilerManager resourceReconcilerManager,
                             Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories,
                             YamlMapper yamlMapper,
                             CustomResource cmcc) {
    if (cmcc == null || cmcc.getSpec() == null) {
      throw new CustomResourceConfigError("custom resource is null");
    }
    this.kubernetesClient = kubernetesClient;
    this.cmcc = cmcc;
    this.componentCollection = new ComponentCollection(beanFactory, kubernetesClient, this);
    this.resourceNamingProvider = resourceNamingProviderFactory.instance(this);
    this.resourceReconcilerManager = resourceReconcilerManager;
    this.urlMappingBuilderFactories = urlMappingBuilderFactories;
    this.yamlMapper = yamlMapper;

    String urlMapperName = getCmcc().getSpec().getDefaults().getManagementUrlMapper();
    this.managementUrlMappingBuilderFactory = urlMappingBuilderFactories.get(urlMapperName);
    if (managementUrlMappingBuilderFactory == null) {
      throw new CustomResourceConfigError("Unable to find URL Mapper \"" + urlMapperName + "\"");
    }
  }

  /**
   * Check if all components are ready, and if so, advance to the next milestone.
   */
  public void advanceToNextMilestoneOnComponentsReady() {
    int active = 0; // counting those, that are somehow relevant (sts should be deployed, particular state is ignored)
    int sleeping = 0; // means: no problems, sts may still be scaled to 0
    int ready = 0; // means: no problems, sts is scaled to > 1 and reached green state
    TreeMap<String, ComponentState> stillWaiting = new TreeMap<>();

    for (Component component : componentCollection.getComponents()) {
      var state = component.getState();
      if (state.isRelevant()) {
        active++;
        if (state.isWaiting()) {
          stillWaiting.put(component.getBaseResourceName(), state);
        } else {
          if (component.getCurrentReplicas() > 0) {
            ready++;
          } else {
            sleeping++;
          }
        }
      }
    }
    var sleepingString = sleeping > 0 ? " (+" + sleeping + " sleeping)" : "";
    if (stillWaiting.size() == 0) {
      if (cmcc.getStatus().getMilestone() != cmcc.getStatus().getMilestone().getNext()) {
        log.info("[{}] Waiting for components, {} ready{}: Advancing to milestone {}", getContextForLogging(),
                ready,
                sleepingString,
                getCmcc().getStatus().getMilestone().getNext());
        cmcc.getStatus().setMilestone(cmcc.getStatus().getMilestone().getNext());
      }
    } else {
      if (cmcc.getStatus().getMilestone() == Milestone.Ready) {
        log.info("We were ready, but now some components have become unavailable. Stepping back to {}", Milestone.Healing);
        cmcc.getStatus().setMilestone(Milestone.Healing);
      }
      var waitingEntries = stillWaiting.entrySet().stream().map(stringComponentStateEntry -> waitingEntryToString(stringComponentStateEntry)).toList();
      log.info("[{}] Waiting for components, {} of {} ready{}: still waiting for {}", getContextForLogging(),
              ready,
              active - sleeping,
              sleepingString,
              String.join(", ", waitingEntries));
    }
  }

  private static String waitingEntryToString(Map.Entry<String, ComponentState> entry) {
    return  entry.getKey().toString() + ":" + switch(entry.getValue()) {
      case Ready -> "✅";
      case NotApplicable -> "❎";
      case WaitingForDeployment -> "⌛📦";
      case ResourceNeedsUpdate -> "⌛🔧";
      case WaitingForShutdown -> "️⏳🛑";
      case WaitingForReadiness -> "⏳✅";
      case WaitingForCompletion -> "⌛✅";
    };
  }

  @Override
  public void reconcile() {
    List<HasMetadata> builtResources = buildResources();

    Set<HasMetadata> existingResources = getAllResourcesMatchingLabels(getKubernetesClient(), getCmcc().getMetadata().getNamespace(), getSelectorLabels())
            .stream().filter(this::isWeOwnThis).collect(Collectors.toSet());
    Set<HasMetadata> newResources = builtResources.stream().filter(r -> !isMetadataContains(existingResources, r)).collect(Collectors.toSet());
    Set<HasMetadata> changedResources = builtResources.stream().filter(r -> isMetadataContains(existingResources, r)).collect(Collectors.toSet());
    Set<HasMetadata> abandonedResources = existingResources.stream().filter(r -> !isMetadataContains(builtResources, r)).collect(Collectors.toSet());

    abandonedResources = abandonedResources.stream().filter(r -> mayBeRemoved(r, builtResources)).collect(Collectors.toSet());

    log.debug("[{}] Updating dependent resources: {} new, {} updated, {} abandoned resources {}",
            getContextForLogging(),
            newResources.size(), changedResources.size(), abandonedResources.size(), abandonedResources.stream().map(x -> x.getMetadata().getName()).toList());

    abandonedResources.forEach(r -> getKubernetesClient().resource(r).withPropagationPolicy(DeletionPropagation.BACKGROUND).delete());

    KubernetesList list = new KubernetesListBuilder().withItems(builtResources).build();
    getResourceReconcilerManager().createPatchUpdate(getCmcc().getMetadata().getNamespace(), list);
  }

  private boolean mayBeRemoved(HasMetadata resource, List<HasMetadata> builtResources) {
    if (resource instanceof PersistentVolumeClaim pvc &&
            builtResources.stream().filter(StatefulSet.class::isInstance)
              .anyMatch(x -> x.getMetadata().getLabels().equals(pvc.getMetadata().getLabels()))) {
      return false;
    }

    return true;
  }

  @Override
  public List<HasMetadata> buildResources() {
    LinkedList<HasMetadata> resources = new LinkedList<>();
    int convergenceLoops = MAX_CONVERGENCE_LOOP;

    buildClientSecretRefs();

    while (!converge() && convergenceLoops-- > 0) {
      log.debug("Not yet converged, {} more tries", convergenceLoops);
    }

    resources.addAll(buildComponentResources());
    resources.addAll(buildExtraResources());

    return resources;
  }

  public void requestRequiredResources() {
    for (Component component : componentCollection.getComponents()) {
      component.requestRequiredResources();
    }
  }

  /**
   * Add the declared clientSecretRefs to the list of all clientSecretRefs, both declared and managed
   */
  public void buildClientSecretRefs() {
    for (Map.Entry<String, Map<String, ClientSecretRef>> perKind : getCmcc().getSpec().getClientSecretRefs().entrySet()) {
      Map<String, ClientSecret> secrets = clientSecrets.computeIfAbsent(perKind.getKey(), k -> new HashMap<>());
      for (Map.Entry<String, ClientSecretRef> e : perKind.getValue().entrySet()) {
        secrets.put(e.getKey(), new ClientSecret(e.getValue().cloneWithDefaults()));
      }
    }

        /*
            We always need an uapi entry for admin. The management-tools will also request this clientSecretRef, but
            that component is not always there, so we might remove the autogenerated secret again if we wouldn't request it here every time.
         */
    getClientSecretRef(UAPI_CLIENT_SECRET_REF_KIND, UAPI_ADMIN_USERNAME,
            (clientSecret, password) -> loadOrBuildSecret(clientSecret, Map.of(
                    ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                    ClientSecretRef.DEFAULT_USERNAME_KEY, UAPI_ADMIN_USERNAME
            ))
    );
  }

  /**
   * Compute the new target state. Return true once the state has been completed; return false if another convergence
   * round is needed.
   *
   * @return true if converged
   */
  public boolean converge() {
    Milestone previousMilestone = getCmcc().getStatus().getMilestone();

    convergeDefaultComponents();
    componentCollection.addAll(cmcc.getSpec().getComponents());
    requestRequiredResources();
    convergeOverrideResources();
    advanceToNextMilestoneOnComponentsReady();

    if (!getCmcc().getStatus().getMilestone().equals(previousMilestone)) {
      onMilestoneReached(previousMilestone);
    }

    return previousMilestone.equals(getCmcc().getStatus().getMilestone());
  }

  /**
   * Called from the converge loop at the beginning, to create default components based on options.
   */
  public abstract void convergeDefaultComponents();

  /**
   * Called from the converge loop after the default and declared components have been added, and
   * requestRequiredResources has been called. This can be used to create additional components that use generated
   * secrets to create a database service with the appropriate accounts.
   */
  public abstract void convergeOverrideResources();

  /**
   * Based on the collection of components, build all resources.
   *
   * @return list of resources
   */
  public LinkedList<HasMetadata> buildComponentResources() {
    return componentCollection.getComponents().stream()
            .filter(Component::isBuildResources)
            .map(Component::buildResources)
            .collect(LinkedList::new, List::addAll, List::addAll);
  }


  /**
   * Build any additional resources.
   *
   * @return list of resources
   */
  public LinkedList<HasMetadata> buildExtraResources() {
    final LinkedList<HasMetadata> resources = new LinkedList<>();

    if (getCmcc().getSpec().getWith().getDatabases()) {
      for (Map.Entry<String, Map<String, ClientSecret>> e : clientSecrets.entrySet()) {
        if (cmcc.getSpec().getWith().databaseCreateForKind(e.getKey())) {
          for (ClientSecret clientSecret : e.getValue().values()) {
            if (clientSecret.getSecret().isEmpty()) {
              clientSecret.setSecret(getKubernetesClient().secrets().inNamespace(getCmcc().getMetadata().getNamespace()).withName(clientSecret.getRef().getSecretName()).get());
              if (clientSecret.getSecret().isEmpty()) {
                throw new CustomResourceConfigError("Unable to load secret \"" + clientSecret.getRef().getSecretName() + "\", required for database \"" + e.getKey() + "\"");
              }
            }
            Secret secret = clientSecret.getSecret().get();
            if (isWeOwnThis(secret))
              resources.add(secret);
          }
        }
      }
    }
    return resources;
  }

  @Override
  public String getContextForLogging() {
    return getCmcc().getMetadata().getNamespace() + "/"
            + concatOptional(getCmcc().getSpec().getDefaults().getNamePrefix(), getCmcc().getMetadata().getName(), getCmcc().getSpec().getDefaults().getNameSuffix()
            + "@" + getCmcc().getStatus().getMilestone());
  }

  public ClientSecret getClientSecret(String kind, String schema) {
    Map<String, ClientSecret> perKind = clientSecrets.get(kind);
    if (perKind == null) {
      throw new CustomResourceConfigError("No secrets requested for type \"" + kind + "\"");
    }
    ClientSecret clientSecret = perKind.get(schema);
    if (clientSecret == null) {
      throw new CustomResourceConfigError("No secret for schema \"" + schema + "\" requested for type \"" + kind + "\"");
    }
    return clientSecret;
  }

  @Override
  public Map<String, ClientSecret> getClientSecrets(String kind) {
    if (clientSecrets.get(kind) == null) {
      throw new IllegalArgumentException("Unknown clientSecretRef type \"" + kind + "\"");
    }

    // make sure secrets are loaded/created
    clientSecrets.get(kind).values().stream()
            .filter(cs -> cs.getSecret().isEmpty())
            .forEach(cs -> cs.setSecret(loadSecret(cs.getRef().getSecretName())));

    return clientSecrets.get(kind);
  }

  @Override
  public Optional<ClientSecretRef> getClientSecretRef(String kind, String schema) {
    Map<String, ClientSecret> perKind = clientSecrets.computeIfAbsent(kind, k -> new HashMap<>());
    ClientSecret clientSecret = perKind.get(schema);

    if (clientSecret != null) {
      return Optional.of(clientSecret.getRef());
    } else {
      return Optional.empty();
    }
  }


  @Override
  public ClientSecretRef getClientSecretRef(String kind, String schema, BiConsumer<ClientSecret, String> buildOrLoadSecret) {
    Map<String, ClientSecret> perKind = clientSecrets.computeIfAbsent(kind, k -> new HashMap<>());
    ClientSecret clientSecret = perKind.get(schema);

    if (clientSecret != null) {
      return clientSecret.getRef();
    }
    try {
      clientSecret = new ClientSecret(ClientSecretRef.defaultClientSecretRef(getSecretName(kind, schema)));
      perKind.put(schema, clientSecret);
      buildOrLoadSecret.accept(clientSecret, getClientPassword());
    } catch (NoSuchComponentException e) {
      throw new CustomResourceConfigError("No \"" + kind + "\" client secret reference found for \"" + schema + "\" in custom resource definition");
    }
    return clientSecret.getRef();
  }

  @Override
  public Collection<ClientSecretRef> getClientSecretRefs(String kind) {
    Map<String, ClientSecret> kindRefs = clientSecrets.get(kind);
    if (kindRefs == null) {
      throw new IllegalArgumentException("No client secret refs have been requested for kind \"" + kind + "\"");
    }
    return kindRefs.values().stream().map(ClientSecret::getRef).collect(Collectors.toList());
  }


  /**
   * Returns a password for a client connection. By default, will generate a random password. If
   * defaults.withInsecureDatabasePassword is set, returns that password.
   *
   * @return a password
   */
  public String getClientPassword() {
    String password = getCmcc().getSpec().getDefaults().getInsecureDatabasePassword();
    if (password.isEmpty())
      password = randomDatabasePassword.next();
    return password;
  }

  @Override
  public String getResourceNameFor(Component component, String... additional) {
    return resourceNamingProvider.nameFor(component, additional);
  }

  @Override
  public String getResourceNameFor(String component, String... additional) {
    return resourceNamingProvider.nameFor(component, additional);
  }

  @Override
  public OwnerReference getOurOwnerReference() {
    return new OwnerReferenceBuilder()
            .withApiVersion(cmcc.getApiVersion())
            .withKind(cmcc.getKind())
            .withName(cmcc.getMetadata().getName())
            .withUid(cmcc.getMetadata().getUid())
            .build();
  }

  @Override
  public ObjectMeta getResourceMetadataFor(String name) {
    return new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(cmcc.getMetadata().getNamespace())
            .withAnnotations(Collections.emptyMap())
            .withLabels(getSelectorLabels())
            .withOwnerReferences(getOurOwnerReference())
            .build();
  }


  @Override
  public ObjectMeta getResourceMetadataFor(Component component, String... additional) {
    return getResourceMetadataFor(getResourceNameFor(component, additional));
  }

  @Override
  public boolean isWeOwnThis(HasMetadata resource) {
    OwnerReference us = getOurOwnerReference();
    for (OwnerReference them : resource.getMetadata().getOwnerReferences()) {
      if (us.equals(them))
        return true;
    }
    return false;
  }

  /**
   * Load a secret from the cluster. The stringData map will be populated.
   *
   * @param name resource
   * @return secret
   */
  public Secret loadSecret(String name) {
    Secret secret = kubernetesClient.secrets().inNamespace(getCmcc().getMetadata().getNamespace()).withName(name).get();
    if (secret != null && (secret.getStringData() == null || secret.getStringData().size() == 0)) {
      Map<String, String> stringData = new HashMap<>();
      secret.setStringData(stringData);
      if (secret.getData() != null) {
        for (Map.Entry<String, String> e : secret.getData().entrySet()) {
          stringData.put(e.getKey(), new String(Base64.getDecoder().decode(e.getValue()), StandardCharsets.UTF_8));
        }
      }
    }
    return secret;
  }

  /**
   * Called when a new milestone has been reached.
   */
  public void onMilestoneReached(Milestone previousMilestone) {
  }

  /**
   * Restart a component by scaling its StatefulSet to 0. The next control loop will re-set the replicas to 1 again.
   *
   * @param name name of the StatefulSet
   */
  public void restartStatefulSet(String name) {
    kubernetesClient.apps().statefulSets().
            inNamespace(cmcc.getMetadata().getNamespace()).withName(name)
            .rolling().restart();
  }

}
