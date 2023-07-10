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

    @JsonPropertyDescription("Use this password for all database accounts, instead of a random one.")
    private String insecureDatabasePassword = "";

    @JsonPropertyDescription("For Java components, use these JAVA_OPTS.")
    private String javaOpts = "-XX:MinRAMPercentage=75 -XX:MaxRAMPercentage=90";

    @JsonPropertyDescription("Default security context for a pod")
    PodSecurityContext podSecurityContext = new PodSecurityContext();

    @JsonPropertyDescription("Default resource management (limits, requests)")
    private ResourceMgmt resources;

    @JsonPropertyDescription("Prefix resources with this name plus '-'. Empty string means no prefix")
    String namePrefix = "";

    @JsonPropertyDescription("Hostname of the preview CAE. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String previewHostname = "preview";

    @JsonPropertyDescription("Default security context for containers in a pod")
    SecurityContext securityContext = new SecurityContext();

    @JsonPropertyDescription("Default protocol for all site mapping entries")
    private String siteMappingProtocol = "https://";

    @JsonPropertyDescription("Name of StorageClass to be used for PersistentVolumeClaims")
    private String storageClass = "";

    @JsonPropertyDescription("Hostname of the Studio. If short, will be prefixed with the prefix and the ingressDomain appended")
    private String studioHostname = "studio";

    @JsonPropertyDescription("List of servlets the CAE serves")
    private List<String> servletNames = List.of("action", "assets", "blob", "dynamic", "preview", "resource", "service", "static");

    @JsonPropertyDescription("Size of persistent data/cache volumes")
    ComponentSpec.VolumeSize volumeSize = new ComponentSpec.VolumeSize("8Gi");
}
