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
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
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

@Slf4j
public class StudioClientComponent extends AbstractComponent implements HasService {

    public static final String CONTAINER_PORT_KEY = "containerPort";
    public static final String SERVICE_PORT_KEY = "servicePort";
    public static final int DEFAULT_PORT = 80;

    private int containerPort;
    private int servicePort;

    public StudioClientComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "studio-client");
    }

    @Override
    public Component updateComponentSpec(ComponentSpec newCs) {
        super.updateComponentSpec(newCs);
        String containerPortString = getComponentSpec().getExtra().get(CONTAINER_PORT_KEY);
        containerPort = null != containerPortString ? Integer.parseInt(containerPortString) : DEFAULT_PORT;
        String servicePortString = getComponentSpec().getExtra().get(SERVICE_PORT_KEY);
        servicePort = null != servicePortString ? Integer.parseInt(servicePortString) : DEFAULT_PORT;
        return this;
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
        EnvVarSet env = new EnvVarSet();

        env.addAll(EnvVarSet.of(
                EnvVarSimple("PROTOCOL", ""),
                EnvVarSimple("FRAME_SRC", getTargetState().getPreviewHostname())
        ));

        return env;
    }


    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("http")
                        .withContainerPort(containerPort)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder()
                        .withName("http")
                        .withPort(servicePort)
                        .withNewTargetPort("http")
                        .build()
        );
    }

    /**
     * Defines a probe suitable for the startup check.
     *
     * @return probe definition
     */
    public Probe getStartupProbe() {
        var interval = 5;
        var timeout = Optional.ofNullable(getComponentSpec().getTimeouts().getStartup()).orElse(300);
        return new ProbeBuilder()
                .withInitialDelaySeconds(30)
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
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(10)
                .withFailureThreshold(200)
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
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(10)
                .withFailureThreshold(100)
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
                .withName("docroot")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("nginx-config")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
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
                .withName("docroot")
                .withMountPath("/usr/share/nginx/html") // must be ../html, since CoreMedia 2412.0.1
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("nginx-config")
                .withMountPath("/etc/nginx/conf.d")
                .build());
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
