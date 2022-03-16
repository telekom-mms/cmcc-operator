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
import com.tsystemsmms.cmcc.cmccoperator.components.*;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.RandomString;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public abstract class AbstractTargetState implements TargetState {
    public static final String FLAG_INITIAL_PASSWORDS = "initialPasswords";
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


    public AbstractTargetState(BeanFactory beanFactory, KubernetesClient kubernetesClient, CmccIngressGeneratorFactory cmccIngressGeneratorFactory, CoreMediaContentCloud cmcc) {
        this.kubernetesClient = kubernetesClient;
        this.cmccIngressGeneratorFactory = cmccIngressGeneratorFactory;
        this.cmcc = cmcc;
        componentCollection = new ComponentCollection(beanFactory, kubernetesClient, this);
    }

    @Override
    public List<HasMetadata> buildResources() {
        LinkedList<HasMetadata> resources = new LinkedList<>();
        int convergenceLoops = MAX_CONVERGENCE_LOOP;

        while (!converge() && convergenceLoops-- > 0) {
            log.debug("Not yet converged, {} more tries", convergenceLoops);
        }

        resources.addAll(buildComponentResources());
        resources.addAll(buildExtraResources());
        resources.addAll(buildIngressResources());

        return resources;
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
    public abstract LinkedList<HasMetadata> buildExtraResources();

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


    /**
     * Get the name for the secret for a component name.
     *
     * @param schema
     * @return
     */
    public String getDatabaseSecretName(String schema) {
        return concatOptional(cmcc.getSpec().getDefaults().getNamePrefix(), "mysql", schema);
    }


    /**
     * @param name   name of the secret
     * @param schema the schema and username to use when creating the new value
     * @return
     */
    public DatabaseSecret getDatabaseSecret(String name, String schema) {
        Secret secret = kubernetesClient.secrets().inNamespace(getCmcc().getMetadata().getNamespace()).withName(name).get();
        if (secret == null) {
            String password = getCmcc().getSpec().getDefaults().getInsecureDatabasePassword();
            if (password.isEmpty())
                password = randomDatabasePassword.next();
            return new DatabaseSecret(schema, schema, password);
        }
        return new DatabaseSecret(schema,
                getDecodedData(secret, DATABASE_SECRET_USERNAME_KEY).orElseThrow(),
                getDecodedData(secret, DATABASE_SECRET_PASSWORD_KEY).orElseThrow());
    }


    /**
     * Build a secret if necessary. If a secret already exists, and we're not the owner, return nothing.
     *
     * @param ref
     * @param buildDefaultSecret a function that returns a secret with the given password
     * @return zero or one Secret
     */
    public Collection<HasMetadata> buildClientSecret(ClientSecretRef ref, Function<String, HasMetadata> buildDefaultSecret) {
        Secret secret = getKubernetesClient().secrets().inNamespace(getCmcc().getMetadata().getNamespace())
                .withName(ref.getSecretName()).get();
        if (secret == null) {
            String password = getCmcc().getSpec().getDefaults().getInsecureDatabasePassword();
            if (password.isEmpty())
                password = randomDatabasePassword.next();
            return Collections.singletonList(buildDefaultSecret.apply(password));
        } else {
            // return the already generated resource; otherwise the reconciler would delete it.
            if (isWeOwnThis(secret))
                return Collections.singletonList(secret);
        }
        return Collections.emptyList();
    }


    public boolean isWeOwnThis(HasMetadata resource) {
        OwnerReference ourRefs = getOutOwnerReference();
        for (OwnerReference ownerRef : resource.getMetadata().getOwnerReferences()) {
            if (ourRefs.equals(ownerRef))
                return true;
        }
        return false;
    }


    /**
     * Collect the schema names from all components requiring a relational database schema
     *
     * @return map of schema name and database secrets
     */
    public HashMap<String, DatabaseSecret> getDatabaseSecrets() {
        HashMap<String, DatabaseSecret> databaseSecrets = new HashMap<>();

        for (Component component : componentCollection.getComponents()) {
            if (component instanceof HasMySQLSchema) {
                HasMySQLSchema hds = (HasMySQLSchema) component;
                String schema = hds.getDatabaseSchema();
                databaseSecrets.put(schema, getDatabaseSecret(getDatabaseSecretName(schema), schema));
            }
        }
        return databaseSecrets;
    }

    public static Optional<String> getDecodedData(Secret secret, String key) {
        String v = secret.getData().get(key);
        if (v == null)
            return Optional.empty();
        return Optional.of(decode64(v));
    }

    /**
     * Build a secret for a database user.
     *
     * @param name   name of the resource
     * @param secret username and password
     * @return resource for the secret
     */
    public Secret buildDatabaseSecret(String name, DatabaseSecret secret) {
        return new SecretBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(name)
                        .withNamespace(cmcc.getMetadata().getNamespace())
                        .withOwnerReferences(getOutOwnerReference())
                        .withLabels(getDatabaseSecretSelectorLabels(secret.getSchema()))
                        .build())
                .withStringData(Map.of(
                        DATABASE_SECRET_USERNAME_KEY, secret.getUsername(),
                        DATABASE_SECRET_PASSWORD_KEY, secret.getPassword()
                ))
                .build();
    }


    /**
     * Build a secret for a database schema and user.
     *
     * @param secret the schema, username and password
     * @return resource for the secret
     */
    public HasMetadata buildDatabaseSchemaSecret(DatabaseSecret secret) {
        return buildDatabaseSecret(getDatabaseSecretName(secret.getSchema()), secret);
    }

    /**
     * Get a set of labels suitable to distinguish pods, services, etc. of this component from others.
     *
     * @return list of labels
     */
    public Map<String, String> getDatabaseSecretSelectorLabels(String schema) {
        HashMap<String, String> labels = new HashMap<>();
        labels.put("cmcc.tsystemsmms.com/cmcc", cmcc.getMetadata().getName());
        labels.put("cmcc.tsystemsmms.com/schema", schema);
        return labels;
    }

    @Override
    public OwnerReference getOutOwnerReference() {
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
    public ObjectMeta getResourceMetadataForName(String name) {
        return new ObjectMetaBuilder()
                .withName(name)
                .withNamespace(cmcc.getMetadata().getNamespace())
                .withAnnotations(Collections.emptyMap())
                .withLabels(getSelectorLabels())
                .withOwnerReferences(getOutOwnerReference())
                .build();
    }


    @Override
    public boolean isInitialPasswords() {
        return booleanOf(getCmcc().getStatus().getFlags().get(FLAG_INITIAL_PASSWORDS), true);
    }


    public void scaleComponent(String name) {
        RollableScalableResource<StatefulSet> sts = kubernetesClient.apps().statefulSets().
                inNamespace(cmcc.getMetadata().getNamespace())
                .withName(name);
        if (sts == null) {
            throw new IllegalArgumentException("No such StatefulSet " + name);
        }
        sts.edit(r -> new StatefulSetBuilder(r).editOrNewSpec().withReplicas(0).endSpec().build());
    }


    public List<StatefulSet> getStatefulSetFor(HasService component) {
        return kubernetesClient.apps().statefulSets().
                inNamespace(cmcc.getMetadata().getNamespace())
                .withLabels(component.getSelectorLabels())
                .list().getItems();
    }

    public List<StatefulSet> getStatefulSetFor(ComponentSpec cs) {
        return getStatefulSetFor(getComponentCollection().getHasServiceComponent(cs));
    }


    /**
     * Checks if a statefulset is ready.
     *
     * @param cs component to check
     * @return true if the stateful set has as many ready pods as specified
     */
    public boolean isComponentStatefulSetReady(ComponentSpec cs) {
        HasService component = getComponentCollection().getHasServiceComponent(cs);
        return isStatefulSetReady(getStatefulSetFor(component), component.getServiceName());
    }

    public boolean isComponentStatefulSetReady(String name) {
        return isStatefulSetReady(
                kubernetesClient.apps().statefulSets().inNamespace(cmcc.getMetadata().getNamespace()).withLabels(getComponentCollection().getHasServiceComponent(name).getSelectorLabels()).list().getItems(),
                name);
    }

    public boolean isComponentStatefulSetReady(String name, String kind) {
        return isStatefulSetReady(
                kubernetesClient.apps().statefulSets().inNamespace(cmcc.getMetadata().getNamespace()).withLabels(getComponentCollection().getHasServiceComponent(name, kind).getSelectorLabels()).list().getItems(),
                name + "/" + kind);
    }

    public boolean isStatefulSetReady(List<StatefulSet> stss, String label) {
        if (stss.size() == 0) {
            log.debug("Can't find StatefulSet " + label);
            return false;
        }
        if (stss.size() > 1) {
            log.debug("Found more than one StatefulSet " + label);
            return false;
        }
        StatefulSetStatus status = stss.get(0).getStatus();
        log.debug("sts {}: replicas {} / available {}", stss.get(0).getMetadata().getName(), status.getReplicas(), status.getReadyReplicas());
        if (status.getReplicas() == null || status.getReadyReplicas() == null)
            return false;
        return status.getReplicas() > 0 && status.getReadyReplicas().equals(status.getReplicas());
    }

    /**
     * Return a compact string representation of the list of components, mainly for logging purposes.
     *
     * @param components the components to represent
     * @return short representation
     */
    public String componentCollectionToString(Collection<ComponentSpec> components) {
        List<String> labels = components.stream().map(c -> c.getKind().isEmpty() ? c.getType() : c.getType() + "/" + c.getKind()).collect(Collectors.toList());
        return String.join(", ", labels);
    }

    public Secret buildSecret(String name, Map<String,String> entries) {
        HashMap<String, String> b64 = new HashMap<>();

        for (Map.Entry<String, String> e : entries.entrySet()) {
            b64.put(e.getKey(), encode64(e.getValue()));
        }

        return new SecretBuilder()
                .withMetadata(getResourceMetadataForName(name))
                .withType("Opaque")
                .withData(b64)
                .build();
    }
}
