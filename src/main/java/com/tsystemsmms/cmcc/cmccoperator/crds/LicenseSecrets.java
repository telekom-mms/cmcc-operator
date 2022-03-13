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

@Data
public class LicenseSecrets {
    @JsonPropertyDescription("Name of the secret that has the license.zip for the Content Management Server")
    private String CMSLicense = "license-cms";
    @JsonPropertyDescription("Name of the secret that has the license.zip for the Master Live Server")
    private String MLSLicense = "license-mls";
    @JsonPropertyDescription("Name of the secret that has the license.zip for the Replication Live Server")
    private String RLSLicense = "license-rls";
}
