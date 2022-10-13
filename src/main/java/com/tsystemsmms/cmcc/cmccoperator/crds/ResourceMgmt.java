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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceMgmt {
    @JsonPropertyDescription("Limits to resources for this components pods")
    private Map<String, String> limits = new HashMap<>();
    @JsonPropertyDescription("Requests for resources for this components pods")
    private Map<String, String> requests = new HashMap<>();

    /**
     * Returns a ResourceRequirements object based on the limits and requests. The quantities specified as String
     * are converted to an Operator Framework Quantity.
     *
     * @return the resources requirements
     */
    @JsonIgnore
    public ResourceRequirements getResources() {
        return new ResourceRequirementsBuilder()
                .withRequests(requests.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))))
                .withLimits(limits.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new Quantity(e.getValue()))))
                .build();
    }

    public static ResourceMgmt withDefaults(ResourceMgmt defaults, ResourceMgmt specific) {
        HashMap<String, String> limits = defaults == null ? new HashMap<>() : new HashMap<>(defaults.limits);
        if (specific != null)
            limits.putAll(specific.limits);
        HashMap<String, String> requests = defaults == null ? new HashMap<>() : new HashMap<>(defaults.requests);
        if (specific != null)
            requests.putAll(specific.requests);
        return new ResourceMgmt(limits, requests);
    }
}
