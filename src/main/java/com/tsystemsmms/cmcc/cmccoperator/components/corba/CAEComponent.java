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

import com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public class CAEComponent extends CorbaComponent implements HasMongoDBClient, HasService {

    public static final String KIND_LIVE = "live";
    public static final String KIND_PREVIEW = "preview";

    String solrCollection;
    String servletPathPattern;

    public CAEComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "cae-preview");
        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either " + KIND_LIVE + " or " + KIND_PREVIEW);
        switch (componentSpec.getKind()) {
            case KIND_LIVE:
                solrCollection = "live";
                setImageRepository("cae-live");
                break;
            case KIND_PREVIEW:
                solrCollection = "preview";
                break;
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LIVE + " or " + KIND_PREVIEW);
        }
        setDefaultSchemas(Map.of(
                MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                UAPI_CLIENT_SECRET_REF_KIND, "webserver"
        ));
        servletPathPattern = String.join("|", getDefaults().getServletNames());
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
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
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = super.getSelectorLabels();
        labels.put("cmcc.tsystemsmms.com/kind", getComponentSpec().getKind());
        return labels;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars("cae", solrCollection));

        return env;
    }

    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(Map.of(
                "server.tomcat.accesslog.enabled", "true",
                "server.tomcat.accesslog.directory", "dev",
                "server.tomcat.accesslog.prefix", "stdout",
                "server.tomcat.accesslog.suffix", "",
                "server.tomcat.accesslog.file-date-format", "",
                "server.tomcat.accesslog.pattern", "[ACCESS] %l %t %D %F %B %S",
                "server.tomcat.accesslog.rotate", "false",
                "com.coremedia.transform.blobCache.basePath", "/coremedia/persistent-cache/transformed-blob"
        ));

        if (getComponentSpec().getKind().equals(KIND_LIVE)) {
            properties.put("repository.url", getTargetState().getServiceUrlFor("content-server", "mls"));

            for (SiteMapping siteMapping : getSpec().getSiteMappings()) {
                String fqdn = concatOptional(getDefaults().getNamePrefix(), siteMapping.getHostname()) + "." + getDefaults().getIngressDomain();
                if (siteMapping.getFqdn() != null && !siteMapping.getFqdn().isEmpty())
                    fqdn = siteMapping.getFqdn();

                if (!siteMapping.getFqdn().isBlank())
                    fqdn = siteMapping.getFqdn();
                properties.put("blueprint.site.mapping." + siteMapping.getPrimarySegment(), "//" + fqdn);
                for (String segment : siteMapping.getAdditionalSegments()) {
                    properties.put("blueprint.site.mapping." + segment, "//" + fqdn);
                }
            }
        } else {
            properties.putAll(getSiteMappingProperties());
        }
        return properties;
    }

    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("http")
                        .withContainerPort(8080)
                        .build(),
                new ContainerPortBuilder()
                        .withName("management")
                        .withContainerPort(8081)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("http").withPort(8080).withNewTargetPort("http").build(),
                new ServicePortBuilder().withName("management").withPort(8081).withNewTargetPort("management").build());
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName("coremedia-persistent-cache")
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this))
                        .build())
                .build());

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName("coremedia-persistent-cache")
                .withMountPath("/coremedia/persistent-cache")
                .build());

        return volumeMounts;
    }
}
