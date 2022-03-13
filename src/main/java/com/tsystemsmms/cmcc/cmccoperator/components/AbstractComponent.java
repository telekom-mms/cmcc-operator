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

import com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler;
import com.tsystemsmms.cmcc.cmccoperator.crds.*;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.defaultString;

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

    public AbstractComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
        this.kubernetesClient = kubernetesClient;
        this.targetState = targetState;
        this.namespace = getCmcc().getMetadata().getNamespace();
        this.componentSpec = componentSpec;
        this.specName = componentSpec.getName() != null && !componentSpec.getName().isEmpty() ? componentSpec.getName() : componentSpec.getType();
        this.imageRepository = imageRepository;
    }

    /**
     * Returns the custom resource this component is defined in,
     *
     * @return the custom resource
     */
    public CoreMediaContentCloud getCmcc() {
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

    /**
     * Shorthand for getSpec().getImportJob().
     *
     * @return the spec
     */
    public ImportJob getImportJob() {
        return targetState.getCmcc().getSpec().getImportJob();
    }

    @Override
    public String getResourceName() {
        return getComponentName(null);
    }

    /**
     * Build the compoents name. If set, the prefix, the name and the kind will be concatenated together. The optional
     * kind parameter overrides the spec supplied one if set.
     *
     * @param kind optinal override for the getSpec().getKind()
     * @return component name
     */
    public String getComponentName(String kind) {
        if (kind == null || kind.isBlank())
            kind = getComponentSpec().getKind();
        return concatOptional(
                getDefaults().getNamePrefix(),
                getSpecName(),
                kind);
    }

    /**
     * Get the fully qualified image name for the main container of this component.
     *
     * @return the name of the image for the main container for the main pod
     */
    public String getImage() {
        ImageSpec defaultImage = Objects.requireNonNullElse(getDefaults().getImage(), new ImageSpec());
        ImageSpec csImage = Objects.requireNonNullElse(componentSpec.getImage(), new ImageSpec());
        ImageSpec spec = new ImageSpec();

        spec.setRegistry(defaultString(
                csImage.getRegistry(),
                defaultImage.getRegistry(),
                "coremedia"
        ));
        spec.setRepository(defaultString(
                csImage.getRepository(),
                defaultImage.getRepository(),
                imageRepository
        ));
        spec.setTag(defaultString(
                csImage.getTag(),
                defaultImage.getTag(),
                "latest"
        ));

        return spec.getRegistry() + "/" + spec.getRepository() + ":" + spec.getTag();
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
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = new HashMap<>();
        labels.put("cmcc.tsystemsmms.com/cmcc", getCmcc().getMetadata().getName());
        labels.put("cmcc.tsystemsmms.com/type", componentSpec.getType());
        labels.put("cmcc.tsystemsmms.com/name", getResourceName());
        labels.putAll(CoreMediaContentCloudReconciler.OPERATOR_SELECTOR_LABELS);
        return labels;
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

    /**
     * Create the StatefulSet for reconciliation.
     *
     * @return the created StatefulSet.
     */
    public StatefulSet buildStatefulSet() {
        return new StatefulSetBuilder()
                .withMetadata(getResourceMetadata())
                .withSpec(new StatefulSetSpecBuilder()
                        .withServiceName(getResourceName())
                        .withSelector(new LabelSelectorBuilder()
                                .withMatchLabels(getSelectorLabels())
                                .build())
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(getSelectorLabels())
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withContainers(buildContainers())
                                        .withInitContainers(getInitContainers())
                                        .withSecurityContext(getPodSecurityContext())
                                        .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                        .withVolumes(getVolumes())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    public ObjectMeta getResourceMetadata() {
        return getResourceMetadataForName(getResourceName());
    }

    public ObjectMeta getResourceMetadataForName(String name) {
        ObjectMeta metadata = getTargetState().getResourceMetadataForName(name);
        metadata.getLabels().putAll(getSelectorLabels());
        return metadata;
    }

    public long getTerminationGracePeriodSeconds() {
        return 5L;
    }

    public List<Container> buildContainers() {
        LinkedList<Container> containers = new LinkedList<>();
        EnvVarSet env = getEnvVars();
        env.addAll(getComponentSpec().getEnv());

        containers.add(new ContainerBuilder()
                .withName(specName)
                .withImage(getImage())
                .withImagePullPolicy(getImagePullPolicy())
                .withResources(getDefaults().getResources())
                .withSecurityContext(getSecurityContext())
                .withPorts(getContainerPorts())
                .withArgs(getComponentSpec().getArgs())
                .withEnv(env.toList())
                .withVolumeMounts(getVolumeMounts())
                .withStartupProbe(getStartupProbe())
                .withLivenessProbe(getLivenessProbe())
                .withReadinessProbe(getReadinessProbe())
                .build());

        return containers;
    }

    public PersistentVolumeClaim buildPvc() {
        return buildPvc(getResourceName());
    }

    public PersistentVolumeClaim buildPvc(String name) {
        String sc = getDefaults().getStorageClass();
        if (sc.isEmpty())
            sc = null;
        return new PersistentVolumeClaimBuilder()
                .withMetadata(getResourceMetadataForName(name))
                .withSpec(new PersistentVolumeClaimSpecBuilder()
                        .withAccessModes("ReadWriteOnce")
                        .withResources(new ResourceRequirementsBuilder()
                                .withRequests(Map.of("storage", new Quantity("8Gi")))
                                .build())
                        .withStorageClassName(sc)
                        .build())
                .build();
    }


    public EnvVarSet getEnvVars() {
        return new EnvVarSet();
    }

    public List<Container> getInitContainers() {
        return new LinkedList<>();
    }

    abstract public Probe getStartupProbe();

    abstract public Probe getLivenessProbe();

    abstract public Probe getReadinessProbe();

    /**
     * Defines volumes for the main pod.
     *
     * @return list of volumes
     */
    public List<Volume> getVolumes() {
        return List.of(
                new VolumeBuilder()
                        .withName("tmp")
                        .withEmptyDir(new EmptyDirVolumeSource())
                        .build(),
                new VolumeBuilder()
                        .withName("var-tmp")
                        .withEmptyDir(new EmptyDirVolumeSource())
                        .build()
        );
    }

    /**
     * Defines volume mounts for the main container of the main pod.
     *
     * @return list of volume mounts
     */
    public List<VolumeMount> getVolumeMounts() {
        return List.of(
                new VolumeMountBuilder()
                        .withName("tmp")
                        .withMountPath("/tmp")
                        .build(),
                new VolumeMountBuilder()
                        .withName("var-tmp")
                        .withMountPath("/var/tmp")
                        .build()
        );
    }

    /**
     * Service for this component.
     *
     * @return the service definition
     */
    public Service buildService() {
        return new ServiceBuilder()
                .withMetadata(getResourceMetadataForName(getServiceName()))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabels())
                        .withPorts(getServicePorts())
                        .build())
                .build();
    }

    /**
     * Name by which the service of this component can be reached.
     *
     * @return name
     */
    public String getServiceName() {
        return getResourceName();
    }

    /**
     * Returns the URL for the (primary) service.
     *
     * @return URL of the primary service.
     */
    public String getServiceUrl() {
        return "http://" + getResourceName() + ":8080";
    }


    /**
     * Wait for a service to become available. We can tell that the service is available by having at least one
     * endpoints resource with that name. kubectl will exit with a non-zero status if no resources were found. Requires
     * that the service account for the pod has list rights on endpoints.
     *
     * @param service name of the service to wait for.
     * @return a container spec suitable to add to InitContainers.
     */
    public Container getContainerWaitForIor(String service, String url) {
        return new ContainerBuilder()
                .withName("wait-for-" + service)
                .withImage(getDefaults().getCurlImage())
                .withCommand("sh", "-c", "until curl -fsSo/dev/null " + url + "; do echo waiting for " + service + "; sleep 10; done;")
                .build();
    }

    /**
     * Returns the security context for a container.
     *
     * @return security context
     */
    public SecurityContext getSecurityContext() {
        return new SecurityContextBuilder()
                .withReadOnlyRootFilesystem(true)
                .build();
    }

    /**
     * Returns the security context for a pod.
     *
     * @return security context
     */
    public PodSecurityContext getPodSecurityContext() {
        return new PodSecurityContextBuilder()
                .withRunAsUser(getUserId())
                .withRunAsGroup(getUserId())
                .withFsGroup(getUserId())
                .build();
    }

    /**
     * Returns the user and group ID to be used when running containers.
     *
     * @return user ID
     */
    public long getUserId() {
        return 1_000L;
    }


    /**
     * Build the hostname and secret name for the connection to a MySQL server.
     *
     * @param databaseSchema schema name
     * @return connection details
     */
    public MySQLDetails getMySQLDetails(String databaseSchema) {
        return new MySQLDetails(databaseSchema,
                getTargetState().getServiceNameFor("mysql"),
                concatOptional(getDefaults().getNamePrefix(), "mysql", databaseSchema)
        );
    }

    @Data
    public static class MySQLDetails {
        private final String databaseSchema;
        private final String hostName;
        private final String secretName;

        public String getJdbcUrl() {
            return "jdbc:mysql://" + hostName + ":3306/" + databaseSchema;
        }
    }
}
