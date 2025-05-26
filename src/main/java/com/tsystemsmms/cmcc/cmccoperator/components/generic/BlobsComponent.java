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
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

/**
 * A simple static web server for the blobs of the demo content.
 */
@Slf4j
public class BlobsComponent extends AbstractComponent implements HasService {

    public BlobsComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "coremedia-blobs");
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildService());
        return resources;
    }

    @Override
    public long getUserId() {
        // nginx binds to port 80
        return 0L;
    }


    @Override
    public EnvVarSet getEnvVars() {
        return EnvVarSet.of(EnvVarSimple("PROTOCOL", ""));
    }


    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("http")
                        .withContainerPort(80)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("http").withPort(8080).withNewTargetPort("http").build());
    }

    @Override
    public String getServiceUrl() {
        return "http://" + getTargetState().getResourceNameFor(this) + ":8080/";
    }


    /**
     * Defines a probe suitable for the startup check.
     *
     * @return probe definition
     */
    public Probe getStartupProbe() {
        var interval = 10;
        var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getStartup()).orElse(300);
        return new ProbeBuilder()
                .withPeriodSeconds(interval)
                .withTimeoutSeconds(interval)
                .withFailureThreshold(timeout / interval)
                .withHttpGet(new HTTPGetActionBuilder()
                        .withPath("/")
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
                        .withPath("/")
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
                        .withPath("/")
                        .withPort(new IntOrString("http"))
                        .build())
                .build();
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName("var-cache-nginx")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("var-run")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName("var-cache-nginx")
                .withMountPath("/var/cache/nginx")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("var-run")
                .withMountPath("/var/run")
                .build());

        return volumeMounts;
    }

}
