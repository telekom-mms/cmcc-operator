/*
 * Copyright (c) 2022-2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.job;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.tsystemsmms.cmcc.cmccoperator.crds.UsernamePasswordSecretRef;
import io.fabric8.kubernetes.api.model.EnvVar;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class ImportJob {
    @JsonPropertyDescription("URL of the blob-data.zip to be imported, used by 'content-import'")
    boolean blobServer = false;

    @JsonPropertyDescription("Secret for authentication for the contentUsersUrl")
    UsernamePasswordSecretRef contentUsersAuth = new UsernamePasswordSecretRef();

    @JsonPropertyDescription("Volume containing content-users.zip and frontend.zip, used by 'unpack-content-users-frontend'")
    String contentUsersThemesPvc = "";

    @JsonPropertyDescription("URL of the content-users.zip to be imported, used by 'use-remote-content-archive'")
    String contentUsersUrl = "";

    @JsonPropertyDescription("Additional environment variables")
    private List<EnvVar> env;

    @JsonPropertyDescription("Force re-import of content and users")
    boolean forceContentImport = false;

    @JsonPropertyDescription("Force re-import of themes")
    boolean forceThemeImport = false;

    @JsonPropertyDescription("Use no volumes at all. Take everything from the image fs")
    boolean noVolumes = false;

    @JsonPropertyDescription("Override for 'active deadline' in seconds, default: 30 min.")
    long activeDeadlineSeconds = 30 * 60L;

    @JsonPropertyDescription("List of tasks (entrypoint scripts) to be run by management-tools")
    List<String> tasks = Collections.emptyList();

    @JsonPropertyDescription("Secret for authentication for the frontendUrl")
    UsernamePasswordSecretRef themesAuth = new UsernamePasswordSecretRef();

    @JsonPropertyDescription("URL of the frontend.zip to be imported, used by 'import-themes'")
    String themesUrl = "";
}
