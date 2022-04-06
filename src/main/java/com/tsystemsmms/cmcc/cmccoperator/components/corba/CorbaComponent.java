/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient;
import com.tsystemsmms.cmcc.cmccoperator.components.SpringBootComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public abstract class CorbaComponent extends SpringBootComponent implements HasService, HasUapiClient {

    public CorbaComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
        super(kubernetesClient, targetState, componentSpec, imageRepository);
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildService());
        return resources;
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getUapiClientSecretRef();
    }


    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.add(EnvVarSimple("SPRING_APPLICATION_NAME", getSpecName()));
        env.add(EnvVarSimple("SPRING_BOOT_EXPLODED_APP", "true"));
        env.add(EnvVarSimple("JAVA_HEAP", ""));
        env.add(EnvVarSimple("JAVA_OPTS", "-XX:MinRAMPercentage=75 -XX:MaxRAMPercentage=90"));
        env.addAll(getUapiClientEnvVars("REPOSITORY"));

        return env;
    }

    @Override
    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(Map.of(
                // needed in many applications
                "com.coremedia.transform.blobCache.basePath", "/coremedia/cache/persistent-transformed-blobcache",
                "repository.blob-cache-path", "/coremedia/cache/uapi-blobcache",
                "repository.heap-cache-size", Integer.toString(128 * 1024 * 1024),
                "repository.url", getTargetState().getServiceUrlFor("content-server", "cms"),
                "management.health.diskspace.path", "/var/tmp"
        ));
        return properties;
    }


    public Map<String, String> getSiteMappingProperties() {
        HashMap<String, String> properties = new HashMap<>();
        String preview = "https://" + getTargetState().getPreviewHostname();

        for (SiteMapping siteMapping : getSpec().getSiteMappings()) {
            properties.put("blueprint.site.mapping." + siteMapping.getPrimarySegment(), preview);
            for (String segment : siteMapping.getAdditionalSegments()) {
                properties.put("blueprint.site.mapping." + segment, preview);
            }
        }

        return properties;
    }


    @Override
    public List<Container> getInitContainers() {
        List<Container> containers = super.getInitContainers();

        // by default, wait on the CMS
        containers.add(getContainerWaitForIor(getTargetState().getServiceNameFor("content-server", "cms"), getTargetState().getServiceUrlFor("content-server", "cms")));
        return containers;
    }

    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("ior")
                        .withContainerPort(8080)
                        .build(),
                new ContainerPortBuilder()
                        .withName("management")
                        .withContainerPort(8081)
                        .build(),
                new ContainerPortBuilder()
                        .withName("corba")
                        .withContainerPort(8083)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("ior").withPort(8080).withNewTargetPort("ior").build(),
                new ServicePortBuilder().withName("management").withPort(8081).withNewTargetPort("management").build(),
                new ServicePortBuilder().withName("corba").withPort(8083).withNewTargetPort("corba").build());
    }

    @Override
    public List<PersistentVolumeClaim> getVolumeClaims() {
        List<PersistentVolumeClaim> claims = super.getVolumeClaims();

        claims.add(getPersistentVolumeClaim("persistent-cache"));
        claims.add(getPersistentVolumeClaim("persistent-transformed-blobcache"));
        claims.add(getPersistentVolumeClaim("uapi-blobcache"));

        return claims;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName("persistent-transformed-blobcache")
                .withMountPath("/coremedia/cache/persistent-transformed-blobcache")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("uapi-blobcache")
                .withMountPath("/coremedia/cache/uapi-blobcache")
                .build());

        return volumeMounts;
    }

    @Override
    public String getServiceUrl() {
        return "http://" + getTargetState().getResourceNameFor(this) + ":8080/ior";
    }
}
