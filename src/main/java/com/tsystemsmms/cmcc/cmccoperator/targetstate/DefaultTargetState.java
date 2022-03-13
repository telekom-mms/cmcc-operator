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
import com.tsystemsmms.cmcc.cmccoperator.crds.ImportJob;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.RandomString;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

/**
 * Create the runtime config based on the CRD data.
 */
@Slf4j
public class DefaultTargetState extends AbstractTargetState {

    private static final RandomString randomDatabasePassword = new RandomString(16);

    public DefaultTargetState(BeanFactory beanFactory, KubernetesClient kubernetesClient, CmccIngressGeneratorFactory cmccIngressGeneratorFactory, CoreMediaContentCloud cmcc) {
        super(beanFactory, kubernetesClient, cmccIngressGeneratorFactory, cmcc);
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();

        if (cmcc.getSpec().getWith().getManagement()) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("cae").withKind("preview").build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withKind("live").build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withKind("preview").build(),
                    ComponentSpecBuilder.ofType("content-feeder").build(),
                    ComponentSpecBuilder.ofType("content-server").withKind("cms").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("content-server").withKind("mls").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("elastic-worker").build(),
                    ComponentSpecBuilder.ofType("solr").withKind("leader").withMilestone(Milestone.Created).build(),
                    ComponentSpecBuilder.ofType("studio-client").build(),
                    ComponentSpecBuilder.ofType("studio-server").build(),
                    ComponentSpecBuilder.ofType("user-changes").build(),
                    ComponentSpecBuilder.ofType("workflow-server").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("overview").withMilestone(Milestone.ManagementReady).build()
            ));
        }

        if (cmcc.getSpec().getWith().getDelivery().getRls() != 0
                || cmcc.getSpec().getWith().getDelivery().getMinCae() > 1
                || cmcc.getSpec().getWith().getDelivery().getMaxCae() > cmcc.getSpec().getWith().getDelivery().getMinCae()) {
            throw new RuntimeException("Unable to configure RLS and HPA, not implemented yet");
        }
        if (cmcc.getSpec().getWith().getDelivery().getMinCae() == 1) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("cae").withKind("live").build()
            ));
        }

        // allow explicitly declared components to override default ones
        componentCollection.addAll(cmcc.getSpec().getComponents());

        if (cmcc.getSpec().getWith().getDatabases()) {
            // collect the schema names from all components requiring a relational database schema
            HashMap<String, DatabaseSecret> databaseSecrets = new HashMap<>();

            for (Component component : componentCollection.getComponents()) {
                if (component instanceof HasMySQLSchema) {
                    HasMySQLSchema hds = (HasMySQLSchema) component;
                    String schema = hds.getDatabaseSchema();
                    databaseSecrets.put(schema, getDatabaseSecret(getDatabaseSecretName(schema), schema));
                }
            }
            StringBuilder sql = new StringBuilder();
            for (DatabaseSecret secret : databaseSecrets.values()) {
                resources.add(buildDatabaseSchemaSecret(secret));
                sql.append(format("CREATE SCHEMA IF NOT EXISTS {} CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;\n", secret.getSchema()));
                sql.append(format("CREATE USER IF NOT EXISTS '{}'@'%' IDENTIFIED BY '{}';\n", secret.getUsername(), secret.getPassword()));
                sql.append(format("ALTER USER '{}'@'%' IDENTIFIED BY '{}';\n", secret.getUsername(), secret.getPassword()));
                sql.append(format("GRANT ALL PRIVILEGES ON {}.* TO '{}'@'%';\n", secret.getSchema(), secret.getUsername()));
            }

            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("mongodb")
                            .withMilestone(Milestone.Created)
                            .build(),
                    ComponentSpecBuilder.ofType("mysql")
                            .withMilestone(Milestone.Created)
                            .withExtra(Map.of("create-users.sql", sql.toString()))
                            .build()
            ));
        }

        switch (cmcc.getStatus().getMilestone()) {
            case Created:
                if (cmcc.getSpec().getWith().getDatabases()) {
                    advanceToMilestoneAfterComponentsReady(List.of(
                            ComponentSpecBuilder.ofType("mongodb").build(),
                            ComponentSpecBuilder.ofType("mysql").build()
                    ), Milestone.DatabasesReady);
                } else {
                    cmcc.getStatus().setMilestone(Milestone.DatabasesReady);
                }
                break;
            case DatabasesReady:
                advanceToMilestoneAfterComponentsReady(List.of(
                        ComponentSpecBuilder.ofType("content-server").withKind("cms").build(),
                        ComponentSpecBuilder.ofType("content-server").withKind("mls").build(),
                        ComponentSpecBuilder.ofType("solr").withKind("leader").build(),
                        ComponentSpecBuilder.ofType("workflow-server").build()
                ), Milestone.ManagementReady);
                break;
            case ManagementReady:
                ImportJob importJob = getCmcc().getSpec().getImportJob();
                if (importJob != null) {
                    List<String> args;
                    if (importJob.getTasks().size() == 0) {
                        if (importJob.getContentUsersThemesPvc().isBlank()) {
                            // remote
                            args = List.of("use-remote-content-archive", "import-user", "import-themes", "import-content", "import-default-workflows");
                        } else {
                            args = List.of("unpack-content-users-frontend", "import-user", "import-themes", "import-content", "import-default-workflows");
                        }
                    } else {
                        args = importJob.getTasks();
                    }

                    componentCollection.add(ComponentSpecBuilder.ofType("tools")
                            .withName("import")
                            .withArgs(args)
                            .withEnv(importJob.getEnv())
                            .build());
                    advanceToMilestoneAfterJobSucceeds("import", Milestone.Ready);
                } else {
                    cmcc.getStatus().setMilestone(Milestone.Ready);
                }
                break;
        }

        for (Component component : componentCollection.getComponents()) {
            // if the milestone on the component is larger (later) than the current milestone, skip it
            if (component.getComponentSpec().getMilestone().compareTo(cmcc.getStatus().getMilestone()) > 0)
                continue;
            resources.addAll(component.buildResources());
        }

        // generate ingresses for CAEs
        resources.addAll(cmccIngressGeneratorFactory.instance(this, getServiceNameFor("cae", "preview")).buildPreviewResources());
        if (getCmcc().getSpec().getWith().getDelivery().getMinCae() > 0) {
            resources.addAll(cmccIngressGeneratorFactory.instance(this, getServiceNameFor("cae", "live")).buildLiveResources());
        }

        return resources;
    }

    /**
     * Check if the named job has completed successfully, then move to the desired milestone.
     *
     * @param jobName   component name of job
     * @param milestone to advance to
     */
    private void advanceToMilestoneAfterJobSucceeds(String jobName, Milestone milestone) {
        jobName = concatOptional(cmcc.getSpec().getDefaults().getNamePrefix(), jobName);
        Job job = kubernetesClient.batch().v1().jobs().inNamespace(cmcc.getMetadata().getNamespace()).withName(jobName).get();
        if (job != null && job.getStatus() != null && job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0) {
            log.debug("job {}: has succeeded, advancing milestone", jobName);
            cmcc.getStatus().setMilestone(milestone);
        } else {
            log.debug("job {}: waiting for successful completion", jobName);
        }
    }

    private void advanceToMilestoneAfterComponentsReady(Collection<ComponentSpec> components, Milestone milestone) {
        LinkedList<ComponentSpec> notReady = new LinkedList<>();
        for (ComponentSpec component : components) {
            if (!isComponentStatefulSetReady(component))
                notReady.add(component);
        }
        if (notReady.size() == 0) {
            log.debug("Advancing to {}, components ready: {}", milestone, componentCollectionToString(components));
            cmcc.getStatus().setMilestone(milestone);
        } else {
            log.debug("Waiting for components to become ready: {}", componentCollectionToString(notReady));
        }
    }

    private String componentCollectionToString(Collection<ComponentSpec> components) {
        List<String> labels = components.stream().map(c -> c.getKind().isEmpty() ? c.getType() : c.getType() + "/" + c.getKind()).collect(Collectors.toList());
        return String.join(", ", labels);
    }

    public String getDatabaseSecretName(String schema) {
        return concatOptional(cmcc.getSpec().getDefaults().getNamePrefix(), "mysql", schema);
    }

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
                        .withOwnerReferences(getOwnerReferences())
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
    public OwnerReference getOwnerReferences() {
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
                .withOwnerReferences(getOwnerReferences())
                .build();
    }


    private boolean isComponentStatefulSetReady(ComponentSpec cs) {
        HasService component = getComponentCollection().getHasServiceComponent(cs);
        List<StatefulSet> stss = kubernetesClient.apps().statefulSets().
                inNamespace(cmcc.getMetadata().getNamespace())
                .withLabels(component.getSelectorLabels())
                .list().getItems();
        return isStatefulSetReady(stss, component.getServiceName());
    }

    private boolean isComponentStatefulSetReady(String name) {
        return isStatefulSetReady(
                kubernetesClient.apps().statefulSets().inNamespace(cmcc.getMetadata().getNamespace()).withLabels(getComponentCollection().getHasServiceComponent(name).getSelectorLabels()).list().getItems(),
                name);
    }

    private boolean isComponentStatefulSetReady(String name, String kind) {
        return isStatefulSetReady(
                kubernetesClient.apps().statefulSets().inNamespace(cmcc.getMetadata().getNamespace()).withLabels(getComponentCollection().getHasServiceComponent(name, kind).getSelectorLabels()).list().getItems(),
                name + "/" + kind);
    }

    private boolean isStatefulSetReady(List<StatefulSet> stss, String label) {
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

}
