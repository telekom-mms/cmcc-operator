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
import java.util.Set;

@Data
public class SiteMapping {
    @JsonPropertyDescription("Simple hostname of the site")
    private String hostname;

    @JsonPropertyDescription("Fully-qualified hostname of the site; defaults to the simple hostname")
    private String fqdn = "";

    @JsonPropertyDescription("The site segment the root maps to")
    private String primarySegment;

    @JsonPropertyDescription("Additional segments that are available through this hostname")
    private Set<String> additionalSegments = Collections.emptySet();
}
