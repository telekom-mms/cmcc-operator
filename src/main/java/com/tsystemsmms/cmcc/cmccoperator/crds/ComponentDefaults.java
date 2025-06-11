/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.crds;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.*;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class ComponentDefaults {
    @JsonPropertyDescription("Additional annotations for all component pods")
    private Map<String, String> annotations = new HashMap<>();

    @JsonPropertyDescription("Docker image with curl available")
    private String curlImage = "docker.io/alpine/curl:latest";

    @JsonPropertyDescription("Defaults for the image specification")
    private ImageSpec image;

    @JsonPropertyDescription("Default domain name for Ingresses")
    private String ingressDomain;

    @JsonPropertyDescription("Prefix ingress host-domains with this name plus '-'. Empty string means no prefix. Overrides namePrefix for ingress domain name generation")
    String namePrefixForIngressDomain = "";

    @JsonPropertyDescription("Suffix ingress host-domains with '-' plus this name. Empty string means no suffix. Overrides nameSuffix for ingress domain name generation")
    String nameSuffixForIngressDomain = "";

    @JsonPropertyDescription("Use this password for all database accounts, instead of a random one.")
    private String insecureDatabasePassword = "";

    @JsonPropertyDescription("Default environment variables for all pods")
    private List<EnvVar> env = new LinkedList<>();

    @JsonPropertyDescription("For Java components, use these JAVA_OPTS.")
    private String javaOpts = "-XX:MinRAMPercentage=75 -XX:MaxRAMPercentage=90";

    @JsonPropertyDescription("Default security context for a pod")
    PodSecurityContext podSecurityContext = new PodSecurityContext();

    @JsonPropertyDescription("Default resource management (limits, requests)")
    private ResourceMgmt resources;

    @JsonPropertyDescription("Name of the URL Mapper to use by default for live URL mappings")
    String liveUrlMapper = "blueprint";

    @JsonPropertyDescription("Name of the URL Mapper to use to create preview and studio URL mappings")
    String managementUrlMapper = "blueprint";

    @JsonPropertyDescription("Name of the URL Mapper to use to create headless URL mappings")
    String headlessUrlMapper = "headless";

    @JsonPropertyDescription("Prefix resources with this name plus '-'. Empty string means no prefix")
    String namePrefix = "";

    @JsonPropertyDescription("Suffix resources with '-' plus this name. Empty string means no suffix")
    String nameSuffix = "";

    @JsonPropertyDescription("Hostname of the preview CAE. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String previewHostname = "preview";

    @JsonPropertyDescription("Hostname of the headless server preview. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String headlessServerPreviewHostname = "headless-preview";

    @JsonPropertyDescription("Hostname of the headless server live. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String headlessServerLiveHostname = "headless";

    @JsonPropertyDescription("Default security context for containers in a pod")
    SecurityContext securityContext = new SecurityContext();

    @JsonPropertyDescription("Default protocol for all site mapping entries")
    private String siteMappingProtocol = "https://";

    @JsonPropertyDescription("Name of StorageClass to be used for PersistentVolumeClaims")
    private String storageClass = "";

    @JsonPropertyDescription("Hostname of the Studio. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String studioHostname = "studio";

    @JsonPropertyDescription("Default topology string to be used for PodAffinity rules")
    private String affinityTopology = "kubernetes.io/hostname";

    @JsonPropertyDescription("List of servlets the CAE serves")
    private List<String> servletNames = List.of("action", "assets", "blob", "dynamic", "preview", "resource", "service", "static");

    @JsonPropertyDescription("Size of persistent data/cache volumes")
    ComponentSpec.VolumeSize volumeSize = new ComponentSpec.VolumeSize("8Gi");
}
