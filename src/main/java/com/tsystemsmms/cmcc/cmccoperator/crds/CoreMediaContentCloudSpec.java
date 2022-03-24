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
import lombok.Data;

import java.util.*;

@Data
public class CoreMediaContentCloudSpec {
    @JsonPropertyDescription("An arbitrary comment; can be used to force an update to the object")
    String comment;

    @JsonPropertyDescription("List of components (CMS, Solr, CAE, etc.)")
    List<ComponentSpec> components = Collections.emptyList();

    @JsonPropertyDescription("References to existing secrets for components connecting as clients to services")
    Map<String, Map<String, ClientSecretRef>> clientSecretRefs = new HashMap<>();

    @JsonPropertyDescription("Ingress TLS defaults")
    IngressTls defaultIngressTls = new IngressTls();

    @JsonPropertyDescription("Default values for components")
    ComponentDefaults defaults;

    @JsonPropertyDescription("Names of the secrets containing the license")
    LicenseSecrets licenseSecrets = new LicenseSecrets();

    @JsonPropertyDescription("Site mappings")
    Set<SiteMapping> siteMappings = Collections.emptySet();

    @JsonPropertyDescription("Optional special components and configurations")
    WithOptions with = new WithOptions();

    @JsonPropertyDescription("Run this job component once from Ready")
    String job = "";
}
