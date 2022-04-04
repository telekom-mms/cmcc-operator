/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.generic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

public class SolrComponent extends AbstractComponent implements HasService {
    public static final String SOLR_PERSISTENT_STORAGE = "solr-persistent-storage";
    public static final String SOLR_REPLICAS = "replicas";
    public static final String SOLR_CONFIG_SETS = "configSets";
    public static final String SOLR_LEADER_COMPONENT = "leader";

    private HashMap<String, String> configSets = new HashMap<>(Map.of(
            "live", "cae",
            "preview", "cae",
            "studio", "studio"
    ));

    private int replicas = 1;

    public SolrComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "solr");
        if (!componentSpec.getKind().isBlank())
            throw new CustomResourceConfigError("Invalid specification \"kind\" for \"solr\": there are no different kinds.");
        if (componentSpec.getExtra().containsKey(SOLR_REPLICAS))
            replicas = Integer.parseInt(componentSpec.getExtra().get(SOLR_REPLICAS).toString());
    }

    @Override
    public Component updateComponentSpec(ComponentSpec newCs) {
        super.updateComponentSpec(newCs);
        if (newCs.getExtra().containsKey(SOLR_REPLICAS))
            replicas = Integer.parseInt(newCs.getExtra().get(SOLR_REPLICAS).toString());
        if (newCs.getExtra().containsKey(SOLR_CONFIG_SETS))
            configSets.putAll(getTargetState().getYamlMapper().load(newCs.getExtra().get(SOLR_CONFIG_SETS), new TypeReference<HashMap<String, String>>() {
                    },
                    () -> "Unable to read \"" + SOLR_CONFIG_SETS + "\" as a map in component \"" + newCs.getName() + "\""));
        return this;
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildService());
        resources.add(buildServiceLeader());
        resources.add(buildStatefulSetLeader());
        resources.add(buildPvc(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT)));
        for (int i = 1; i < replicas; i++) {
            resources.add(buildStatefulSetFollower(i));
            resources.add(buildPvc(getTargetState().getResourceNameFor(this, getFollowerName(i))));
        }

        for (ClientSecret secret : getTargetState().getClientSecrets(SOLR_CLIENT_SECRET_REF_KIND).values()) {

        }

        return resources;
    }

    public StatefulSet buildStatefulSetLeader() {
        EnvVarSet env = getEnvVars();
        env.add(EnvVarSimple("SOLR_LEADER", "true"));
        env.addAll(getComponentSpec().getEnv());

        return new StatefulSetBuilder()
                .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT)))
                .withSpec(new StatefulSetSpecBuilder()
                        .withServiceName(getTargetState().getServiceNameFor(this))
                        .withSelector(new LabelSelectorBuilder()
                                .withMatchLabels(getSelectorLabels(SOLR_LEADER_COMPONENT))
                                .build())
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(getSelectorLabels(SOLR_LEADER_COMPONENT))
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withContainers(buildContainersWithEnv(env))
                                        .withInitContainers(getInitContainers())
                                        .withSecurityContext(getPodSecurityContext())
                                        .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                        .withVolumes(getVolumes(SOLR_LEADER_COMPONENT))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    public StatefulSet buildStatefulSetFollower(int i) {
        EnvVarSet env = getEnvVars();
        env.add(EnvVarSimple("SOLR_FOLLOWER", "true"));
        env.add(EnvVarSimple("SOLR_LEADER_URL", getServiceUrl()));
        env.addAll(getComponentSpec().getEnv());

        return new StatefulSetBuilder()
                .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this, getFollowerName(i))))
                .withSpec(new StatefulSetSpecBuilder()
                        .withServiceName(getTargetState().getServiceNameFor(this))
                        .withSelector(new LabelSelectorBuilder()
                                .withMatchLabels(getSelectorLabels(getFollowerName(i)))
                                .build())
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(getSelectorLabels(getFollowerName(i)))
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withContainers(buildContainersWithEnv(env))
                                        .withInitContainers(getInitContainers())
                                        .withSecurityContext(getPodSecurityContext())
                                        .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                        .withVolumes(getVolumes(getFollowerName(i)))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private String getFollowerName(int i) {
        return "follower-" + i;
    }

    /**
     * Build a service for just the leader.
     *
     * @return
     */
    private Service buildServiceLeader() {
        return new ServiceBuilder()
                .withMetadata(getTargetState().getResourceMetadataFor(this, SOLR_LEADER_COMPONENT))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabels())
                        .withPorts(getServicePorts())
                        .build())
                .build();
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = new EnvVarSet();

        env.add(EnvVarSimple("SPRING_APPLICATION_NAME", getSpecName()));
        env.add(EnvVarSimple("SPRING_BOOT_EXPLODED_APP", "true"));
        env.add(EnvVarSimple("JAVA_HEAP", ""));
        env.add(EnvVarSimple("GC_TUNE", "-XX:+UseG1GC -XX:+PerfDisableSharedMem -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=250 -XX:+AlwaysPreTouch"));
        env.add(EnvVarSimple("SOLR_JAVA_MEM", "-XX:MinRAMPercentage=80 -XX:MaxRAMPercentage=95"));
        return env;
    }

    public List<Volume> getVolumes(String name) {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName(SOLR_PERSISTENT_STORAGE)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this, name))
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("etc-defaults")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());

        return volumes;
    }

    /**
     * Create a Solr index in a follower.
     *
     * @param name name of the index to create
     */
    public void createIndex(String name) {
        URI uri = URI.create("http://solr/foo");
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new CustomResourceConfigError("Unable to create Solr index for \"" + name + "\" on Solr \"" + uri.toString() + "\": code " + response.statusCode() + ", " + response.body());
            }
        } catch (IOException e) {
            throw new CustomResourceConfigError("Unable to create Solr index for \"" + name + "\" on Solr \"" + uri.toString() + "\": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            // ignore?
        }
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName(SOLR_PERSISTENT_STORAGE)
                .withMountPath("/var/solr")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("etc-defaults")
                .withMountPath("/etc/default")
                .build());

        return volumeMounts;
    }

    /**
     * Defines a probe suitable for the startup check.
     *
     * @return probe definition
     */
    public Probe getStartupProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(30)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/solr")
                        .withPort(new IntOrString("http"))
                        .build())
                .build();
    }

    /**
     * Defines a probe suitable for the liveness check.
     *
     * @return probe definition
     */
    public Probe getLivenessProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(20)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/solr")
                        .withPort(new IntOrString("http"))
                        .build())
                .build();
    }

    /**
     * Defines a probe suitable for the readiness check.
     *
     * @return probe definition
     */
    public Probe getReadinessProbe() {
        return new ProbeBuilder()
                .withPeriodSeconds(10)
                .withFailureThreshold(10)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/solr")
                        .withPort(new IntOrString("http"))
                        .build())
                .build();
    }

    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("http")
                        .withContainerPort(8983)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("ior").withPort(8983).withNewTargetPort("http").build());
    }

    @Override
    public String getServiceUrl() {
        return "http://" + getTargetState().getResourceNameFor(this) + ":8983/solr";
    }

    public String getServiceUrlLeader() {
        return "http://" + getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT) + ":8983/solr";
    }

}