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
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.SimpleExecListener;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SERVER_FOLLOWER;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public class SolrComponent extends AbstractComponent implements HasService {
    public static final String SOLR_PERSISTENT_STORAGE = "solr-persistent-storage";
    public static final String SOLR_REPLICAS = "replicas";
    public static final String SOLR_CONFIG_SETS = "configSets";
    public static final String SOLR_LEADER_COMPONENT = "leader";

    private final HashMap<String, String> configSets = new HashMap<>(Map.of(
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
            replicas = Integer.parseInt(componentSpec.getExtra().get(SOLR_REPLICAS));
    }

    @Override
    public Component updateComponentSpec(ComponentSpec newCs) {
        super.updateComponentSpec(newCs);
        if (newCs.getExtra().containsKey(SOLR_REPLICAS))
            replicas = Integer.parseInt(newCs.getExtra().get(SOLR_REPLICAS));
        if (newCs.getExtra().containsKey(SOLR_CONFIG_SETS))
            configSets.putAll(getTargetState().getYamlMapper().load(newCs.getExtra().get(SOLR_CONFIG_SETS), new TypeReference<HashMap<String, String>>() {
                    },
                    () -> "Unable to read \"" + SOLR_CONFIG_SETS + "\" as a map in component \"" + newCs.getName() + "\""));
        return this;
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();

        if (replicas < 1)
            throw new CustomResourceConfigError("component solr: extra.replicas must be 1 or higher, not " + replicas);

        resources.add(buildService());
        resources.add(buildServiceLeader());
        resources.add(buildStatefulSetLeader());
        resources.add(getPersistentVolumeClaim(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT)));
        for (int i = 1; i < replicas; i++) {
            resources.add(buildStatefulSetFollower(i));
            resources.add(getPersistentVolumeClaim(getTargetState().getResourceNameFor(this, getFollowerName(i))));
        }
        for (Map.Entry<String, ClientSecret> e : getTargetState().getClientSecrets(SOLR_CLIENT_SECRET_REF_KIND).entrySet()) {
            String[] parts = e.getKey().split("-");
            if (parts.length < 2)
                continue;
            String server = parts[parts.length-1];
            if (server.equals(SOLR_CLIENT_SERVER_FOLLOWER)) {
                // create schema on follower
                for (int i = 1; i < replicas; i++) {
                    String name = getTargetState().getResourceNameFor(this, getFollowerName(i));
                    String flag = concatOptional("solr-index", name, "created");
                    if (getTargetState().isFlag(flag))
                        continue;
                    if (!getTargetState().isStatefulSetReady(name))
                        continue;
                    createIndex(name, e.getValue().getStringData().get(e.getValue().getRef().getSchemaKey()));
                    getTargetState().setFlag(flag, true);
                }
            }
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
        env.add(EnvVarSimple("SOLR_LEADER_URL", getServiceUrl(SOLR_LEADER_COMPONENT)));
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

    @Override
    public Service buildService() {
        return new ServiceBuilder()
                .withMetadata(getTargetState().getResourceMetadataFor(this))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabelsForService())
                        .withPorts(getServicePorts())
                        .build())
                .build();
    }

    public HashMap<String, String> getSelectorLabelsForService() {
        HashMap<String, String> labels = getTargetState().getSelectorLabels();
        // do not key off the name of the component, only the type (and standard selectors), so we match any Solr pod
        labels.put("cmcc.tsystemsmms.com/type", getComponentSpec().getType());
        return labels;
    }

    /**
     * Build a service for just the leader.
     *
     * @return the service for the leader
     */
    private Service buildServiceLeader() {
        return new ServiceBuilder()
                .withMetadata(getTargetState().getResourceMetadataFor(this, SOLR_LEADER_COMPONENT))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabels(SOLR_LEADER_COMPONENT))
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
        env.add(EnvVarSimple("GC_TUNE", "-XX:+UseG1GC -XX:+PerfDisableSharedMem -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=250 -XX:+AlwaysPreTouch"));
        env.add(EnvVarSimple("SOLR_HEAP", ""));
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
     * Create a Solr index in a follower. This is a bit ugly, since exec() doesn't appear to be able to capture the
     * commands' exit code.
     *
     * <pre>curl 'http://localhost:8983/solr/admin/cores?action=CREATE&name=studio&configSet=content&dataDir=data'</pre>
     *
     * @param name name of the index to create
     */
    public void createIndex(String resource, String name) {
        String podName = concatOptional(resource, "0");
        String configSet = Objects.requireNonNull(configSets.get(name), () -> "No configSet available for Solr collection \"" + name + "\"");
        String url = UriComponentsBuilder.fromHttpUrl("http://localhost:8983/solr/admin/cores")
                .queryParam("action", "CREATE")
                .queryParam("name", name)
                .queryParam("configSet", configSet)
                .queryParam("dataDir", "data")
                .toUriString();
//        "http://localhost:8983/solr/admin/cores?action=CREATE&name=" + name + "&configSet=" + configSet + "&dataDir=data";
        log.debug("Creating Solr Core {} on pod {}, using {}", name, podName, url);
        SimpleExecListener listener = new SimpleExecListener();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        ExecWatch watch = getKubernetesClient().pods().inNamespace(getNamespace()).withName(podName)
                .inContainer("solr")
                .readingInput(InputStream.nullInputStream())
                .writingOutput(out)
                .writingError(err)
                .usingListener(listener)
                .exec("sh", "-c", "curl -fs '" + url + "' || echo \"error $?\" >&2");
        listener.awaitUninterruptable();
        watch.close();
        log.debug("Process out:\n{}", out.toString(StandardCharsets.UTF_8));
        log.debug("Process err:\n{}", err.toString(StandardCharsets.UTF_8));
        if (err.size() != 0) {
            throw new CustomResourceConfigError("Unable to create Solr core \"" + name + "\" on pod \"" + podName + "\": " + err);
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
        throw new CustomResourceConfigError("Component implements multiple services, need to call getServiceUrl(variant) instead");
    }

    @Override
    public String getServiceUrl(String variant) {
        if (replicas > 1)
            return "http://" + getTargetState().getResourceNameFor(this, variant) + ":8983/solr";
        else
            return "http://" + getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT) + ":8983/solr";
    }

    @Override
    public Optional<Boolean> isReady() {
        if (Milestone.compareTo(getCmcc().getStatus().getMilestone(), getComponentSpec().getMilestone()) < 0)
            return Optional.empty();
        return Optional.of(getTargetState().isStatefulSetReady(getTargetState().getResourceNameFor(this, SOLR_LEADER_COMPONENT)));
    }


}