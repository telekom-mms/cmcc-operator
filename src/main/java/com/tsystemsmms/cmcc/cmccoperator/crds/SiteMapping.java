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

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
public class SiteMapping {
    @JsonPropertyDescription("Additional segments that are available through this hostname")
    private Set<String> additionalSegments = Collections.emptySet();

    @JsonPropertyDescription("Fully-qualified hostname of the site; defaults to the simple hostname")
    private String fqdn = "";

    @JsonPropertyDescription("Optional additional FQDNs to create ingress resources for")
    private List<String> fqdnAliases = Collections.emptyList();

    @JsonPropertyDescription("Simple hostname of the site; used as the key for site mappings")
    private String hostname;

    @JsonPropertyDescription("The site segment the root maps to")
    private String primarySegment;

    @JsonPropertyDescription("Protocol for this entry")
    private String protocol;

    @JsonPropertyDescription("TLS settings for this mapping")
    private IngressTls tls;

    @JsonPropertyDescription("URL mapper to use for this site")
    private String urlMapper;
}
