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
    Map<String, Boolean> databasesOverride = new HashMap<>();

    @JsonPropertyDescription("Create default components for the delivery stage")
    WithDelivery delivery = new WithDelivery();

    @JsonPropertyDescription("Add these annotations to the managed Ingress resources")
    Map<String, String> ingressAnnotations = new HashMap<>();

    @JsonPropertyDescription("Path to handler for robots.txt and sitemap.xml")
    String ingressSeoHandler = "/blueprint/servlet/service/robots";

    @JsonPropertyDescription("Create default components for the management stage")
    Boolean management = true;

    @JsonPropertyDescription("Size of persistent caches")
    VolumeSize volumeSize = new VolumeSize();

    @JsonPropertyDescription("Enable setting resource limits and requests on all components")
    Boolean resources = true;

    @JsonPropertyDescription("Size of POST/PUT body in MB")
    UploadSize uploadSize = new UploadSize();

    @Data
    public static class WithDelivery {
        @JsonPropertyDescription("Number of RLS to create")
        IntOrString rls = new IntOrString(0);
        @JsonPropertyDescription("Minimum number of CAEs per RLS")
        IntOrString minCae = new IntOrString(0);
        @JsonPropertyDescription("Maximum number of CAEs per RLS")
        IntOrString maxCae = new IntOrString(0);
    }

    @Data
    public static class UploadSize {
        @JsonPropertyDescription("Size of POST/PUT body in MB for the live CAEs")
        IntOrString live = new IntOrString(0);
        @JsonPropertyDescription("Size of POST/PUT body in MB for the preview CAE")
        IntOrString preview = new IntOrString(0);
        @JsonPropertyDescription("Size of POST/PUT body in MB for the Studio")
        IntOrString studio = new IntOrString(0);
    }

    @Data
    public static class VolumeSize {
        @JsonPropertyDescription("Size of MongoDb data volume, in k8s quantity")
        String mongoDbData = "8Gi";
        @JsonPropertyDescription("Size of MySQL data volume, in k8s quantity")
        String mysqlData = "8Gi";
        @JsonPropertyDescription("Size of Solr data volume, in k8s quantity")
        String solrData = "8Gi";
        @JsonPropertyDescription("Size of transformed BLOB cache, in k8s quantity")
        String transformedBlobCache = "8Gi";
        @JsonPropertyDescription("Size of UAPI BLOB cache, in k8s quantity")
        String uapiBlobCache = "8Gi";
    }

    /**
     * Returns true if databases and secrets should be created for the given kind.
     *
     * @param kind kind of secret
     * @return true if databases and secrets should be created
     */
    public boolean databaseCreateForKind(String kind) {
        if (databasesOverride.containsKey(kind))
            return databasesOverride.get(kind);
        return true;
    }
}
