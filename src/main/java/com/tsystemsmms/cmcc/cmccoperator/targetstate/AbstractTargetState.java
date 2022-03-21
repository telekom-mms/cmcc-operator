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

import com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentCollection;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.RandomString;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient.UAPI_ADMIN_USERNAME;
import static com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient.UAPI_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public abstract class AbstractTargetState implements TargetState {
    public static int MAX_CONVERGENCE_LOOP = 5;

    private static final RandomString randomDatabasePassword = new RandomString(16);

    @Getter
    final KubernetesClient kubernetesClient;
    @Getter
    final CmccIngressGeneratorFactory cmccIngressGeneratorFactory;
    @Getter
    final CoreMediaContentCloud cmcc;
    @Getter
    final ComponentCollection componentCollection;
    @Getter
    final ResourceNamingProvider resourceNamingProvider;

    final Map<String, Map<String, ClientSecret>> clientSecrets = new HashMap<>();

    public AbstractTargetState(BeanFactory beanFactory, KubernetesClient kubernetesClient, CmccIngressGeneratorFactory cmccIngressGeneratorFactory, ResourceNamingProviderFactory resourceNamingProviderFactory, CoreMediaContentCloud cmcc) {
        this.kubernetesClient = kubernetesClient;
        this.cmccIngressGeneratorFactory = cmccIngressGeneratorFactory;
        this.cmcc = cmcc;
        componentCollection = new ComponentCollection(beanFactory, kubernetesClient, this);
        this.resourceNamingProvider = resourceNamingProviderFactory.instance(this);
    }

    /**
     * Check if all components are ready, and if so, advance to the next milestone.
     */
    public void advanceToNextMilestoneOnComponentsReady() {
        int active = 0;
        int ready = 0;
        TreeSet<String> stillWaiting = new TreeSet<>();

        for (Component component : componentCollection.getComponents()) {
            if (component.isReady().isPresent()) {
                active++;
                if (!component.isReady().get()) {
                    stillWaiting.add(component.getBaseResourceName());
                } else {
                    ready++;
                }
            }
        }
        if (ready == active) {
            cmcc.getStatus().setMilestone(cmcc.getStatus().getMilestone().getNext());
            log.info("[{}] Waiting for components, all {} ready: Advancing to milestone {}", getContextForLogging(), active, getCmcc().getStatus().getMilestone());
        } else {
            log.info("[{}] Waiting for components, {} of {} ready: still waiting for {}", getContextForLogging(),
                    ready,
                    active,
                    String.join(", ", stillWaiting));
        }
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
        resources.addAll(buildIngressResources());

        return resources;
    }

    public void requestRequiredResources() {
        for (Component component : componentCollection.getComponents()) {
            component.requestRequiredResources();
        }
    }

    public void buildClientSecretRefs() {
        // add the declared clientSecretRefs to the list of all clientSecretRefs, both declared and managed
        for (Map.Entry<String, Map<String, ClientSecretRef>> perKind : getCmcc().getSpec().getClientSecretRefs().entrySet()) {
            Map<String, ClientSecret> secrets = clientSecrets.computeIfAbsent(perKind.getKey(), k -> new HashMap<>());
            for (Map.Entry<String, ClientSecretRef> e : perKind.getValue().entrySet()) {
                secrets.put(e.getKey(), new ClientSecret(e.getValue()));
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
    public abstract boolean converge();

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

        for (Map.Entry<String, Map<String, ClientSecret>> e : clientSecrets.entrySet()) {
            for (ClientSecret clientSecret : e.getValue().values()) {
                Secret secret = clientSecret.getSecret()
                        .orElseThrow(() -> new CustomResourceConfigError("Unable to find secret for clientSecretRef \"" + clientSecret.getRef().getSecretName() + "\""));
                if (isWeOwnThis(secret))
                    resources.add(secret);
            }
        }
        return resources;
    }

    /**
     * Build ingress resources.
     *
     * @return list of resources
     */
    public LinkedList<HasMetadata> buildIngressResources() {
        final LinkedList<HasMetadata> resources = new LinkedList<>();

        // generate ingresses for CAEs
        resources.addAll(cmccIngressGeneratorFactory.instance(this, getServiceNameFor("cae", "preview")).buildPreviewResources());
        if (getCmcc().getSpec().getWith().getDelivery().getMinCae() > 0) {
            resources.addAll(cmccIngressGeneratorFactory.instance(this, getServiceNameFor("cae", "live")).buildLiveResources());
        }
        return resources;
    }


    @Override
    public String getContextForLogging() {
        return getCmcc().getMetadata().getNamespace() + "/"
                + concatOptional(getCmcc().getSpec().getDefaults().getNamePrefix(), getCmcc().getMetadata().getName()
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
    public ClientSecretRef getClientSecretRef(String kind, String schema, BiConsumer<ClientSecret, String> buildOrLoadSecret) {
        Map<String, ClientSecret> perKind = clientSecrets.computeIfAbsent(kind, k -> new HashMap<>());
        ClientSecret clientSecret = perKind.get(schema);

        // the individual HasFooClient interfaces are responsible for checking if generating a secret is desirable
        if (clientSecret == null) {
            clientSecret = new ClientSecret(ClientSecretRef.defaultClientSecretRef(getSecretName(kind, schema)));
            perKind.put(schema, clientSecret);
        }
        buildOrLoadSecret.accept(clientSecret, getClientPassword());
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

    /**
     * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
     *
     * @return list of labels
     */
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = new HashMap<>();
        labels.putAll(CoreMediaContentCloudReconciler.OPERATOR_SELECTOR_LABELS);
        labels.put("cmcc.tsystemsmms.com/cmcc", getCmcc().getMetadata().getName());
        return labels;
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
    public boolean isJobReady(String name) {
        Job job = kubernetesClient.batch().v1().jobs().inNamespace(cmcc.getMetadata().getNamespace()).withName(name).get();

        if (job == null)
            return false;

        return job.getStatus() != null && job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0;
    }

    @Override
    public boolean isStatefulSetReady(String name) {
        StatefulSet sts = kubernetesClient.apps().statefulSets().inNamespace(getCmcc().getMetadata().getNamespace()).withName(name).get();

        if (sts == null)
            return false;

        StatefulSetStatus status = sts.getStatus();
        if (status.getReplicas() == null || status.getReadyReplicas() == null)
            return false;
        return status.getReplicas() > 0 && status.getReadyReplicas().equals(status.getReplicas());
    }

    /**
     * Returns true if this resource is owned by the operator.
     *
     * @param resource to check
     * @return true if we own this
     */
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
        if (secret != null && secret.getStringData() == null) {
            Map<String, String> stringData = new HashMap<>();
            secret.setStringData(stringData);
            for (Map.Entry<String, String> e : secret.getData().entrySet()) {
                stringData.put(e.getKey(), new String(Base64.getDecoder().decode(e.getValue()), StandardCharsets.UTF_8));
            }
        }
        return secret;
    }

    /**
     * Called when a new milestone has been reached.
     */
    public void onMilestoneReached() {
    }

    /**
     * Restart a component by scaling its StatefulSet to 0. The next control loop will re-set the replicas to 1 again.
     *
     * @param name name of the StatefulSet
     */
    public void restartStatefulSet(String name) {
        RollableScalableResource<StatefulSet> sts = kubernetesClient.apps().statefulSets().
                inNamespace(cmcc.getMetadata().getNamespace())
                .withName(name);
        if (sts == null) {
            throw new IllegalArgumentException("No such StatefulSet " + name);
        }
        sts.edit(r -> new StatefulSetBuilder(r).editOrNewSpec().withReplicas(0).endSpec().build());
    }


}
