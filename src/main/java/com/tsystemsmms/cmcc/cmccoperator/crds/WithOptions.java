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
import io.fabric8.kubernetes.api.model.IntOrString;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WithOptions {
    @JsonPropertyDescription("Import content, users, themes and workflows")
    Boolean contentImport = true;

    @JsonPropertyDescription("Create databases and secrets for CoreMedia")
    Boolean databases = false;

    @JsonPropertyDescription("Create databases and secrets, except for these")
    Map<String, Boolean> datasesOverride = new HashMap<>();

    @JsonPropertyDescription("Create default components for the delivery stage")
    WithDelivery delivery = new WithDelivery();

    @JsonPropertyDescription("Create default components for the management stage")
    Boolean management = true;

    @Data
    public static class WithDelivery {
        @JsonPropertyDescription("Number of RLS to create")
        IntOrString rls = new IntOrString(0);
        @JsonPropertyDescription("Minimum number of CAEs per RLS")
        IntOrString minCae = new IntOrString(0);
        @JsonPropertyDescription("Maximum number of CAEs per RLS")
        IntOrString maxCae = new IntOrString(0);
    }

    /**
     * Returns true if databases and secrets should be created for the given kind.
     *
     * @param kind kind of secret
     * @return true if databases and secrets should be created
     */
    public boolean databaseCreateForKind(String kind) {
        if (datasesOverride.containsKey(kind))
            return datasesOverride.get(kind);
        return true;
    }
}
