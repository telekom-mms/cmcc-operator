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

import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.LinkedList;
import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

public class SolrComponent extends AbstractComponent implements HasService {
    public static final String SOLR_PERSISTENT_STORAGE = "solr-persistent-storage";

    public SolrComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "solr");
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildService());
        resources.add(buildPvc());
        return resources;
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
        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either master or replica");
        switch (getComponentSpec().getKind()) {
            case "leader":
                env.add(EnvVarSimple("SOLR_LEADER", "true"));
                break;
            case "follower":
                throw new CustomResourceConfigError("not yet implemented");
            default:
                throw new CustomResourceConfigError("kind must be set to either leader or follower, not " + getComponentSpec().getKind());
        }

        return env;
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName(SOLR_PERSISTENT_STORAGE)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this))
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("etc-defaults")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());

        return volumes;
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
}