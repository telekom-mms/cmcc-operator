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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Creates an Overview web page. It creates an NGINX web server. The contents are mounted into the docroot from a
 * ConfigMap that includes some HTML, JS and a generated JSON with relevant information.
 */
@Slf4j
public class OverviewComponent extends AbstractComponent implements HasService {

    public OverviewComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "studio-client");
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildConfigMap());
        resources.add(buildService());
        resources.addAll(buildIngress());
        return resources;
    }

    @Override
    public String getImage() {
        return "docker.io/library/nginx:1.21";
    }

    @Override
    public long getUserId() {
        // nginx binds to port 80
        return 0L;
    }


    ConfigMap buildConfigMap() {
        HashMap<String, String> contents = new HashMap<>();

        contents.put("index.html", getResource("/overview/index.html"));
        contents.put("handlebars.js", getResource("/overview/handlebars.min-v4.7.7.js"));
        contents.put("overview.css", getResource("/overview/overview.css"));

        if (getComponentSpec().getExtra() != null)
            contents.putAll(getComponentSpec().getExtra());

        Info info = new Info();
        info.comment = getSpec().getComment();
        info.name = getCmcc().getMetadata().getName();
        info.prefix = getDefaults().getNamePrefix();
        info.previewUrl = "https://" + getTargetState().getPreviewHostname();
        info.studioUrl = "https://" + getTargetState().getStudioHostname();

        info.siteMappings = new HashSet<>();
        for (SiteMapping siteMapping : getSpec().getSiteMappings()) {
            String fqdn = concatOptional(getDefaults().getNamePrefix(), siteMapping.getHostname()) + "." + getDefaults().getIngressDomain();
            if (siteMapping.getFqdn() != null && !siteMapping.getFqdn().isEmpty())
                fqdn = siteMapping.getFqdn();
            siteMapping.setFqdn(fqdn);
            info.siteMappings.add(siteMapping);
        }

        try {
            contents.put("info.json", new ObjectMapper().writeValueAsString(info));
        } catch (JsonProcessingException e) {
            log.warn("unable to render info", e);
        }

        return new ConfigMapBuilder()
                .withMetadata(getResourceMetadata())
                .withData(contents)
                .build();
    }


    Collection<? extends HasMetadata> buildIngress() {
        String service = getTargetState().getServiceNameFor(this);
        return getTargetState().getCmccIngressGeneratorFactory().instance(getTargetState(), service)
                .builder(getTargetState().getResourceNameFor(this), getSpecName())
                .pathPrefix("/", service)
                .build();
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
                new ServicePortBuilder().withName("http").withPort(80).withNewTargetPort("http").build());
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
                .withName("docroot")
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(getTargetState().getResourceNameFor(this))
                        .build())
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
                .withMountPath("/usr/share/nginx/html") // must not be ../html, otherwise the entrypoint script doesn't work
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

    private static String getResource(String file) {
        try {
            InputStream is = OverviewComponent.class.getResourceAsStream(file);
            return new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        } catch (NullPointerException | IOException e) {
            log.warn("Unable to load {} from the classpath", file, e);
            return "";
        }
    }


    @Data
    private static class Info {
        String comment;
        String name;
        String prefix;
        String previewUrl;
        Set<SiteMapping> siteMappings;
        String studioUrl;
    }

}
