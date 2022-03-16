/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import lombok.Builder;
import lombok.Data;

/**
 * All information necessary to reference a secret containing all detail for a client to connect to a server.
 * This allows components to create EnvVars referencing secrets for database or UAPI connections.
 */
@Data
public class ClientSecretRef {
    String passwordKey = "";
    String schemaKey = "";
    String secretName = "";
    String urlKey = "";
    String usernameKey = "";

    public ClientSecretRef() {

    }

    @Builder
    private ClientSecretRef(String passwordKey, String schemaKey, String secretName, String urlKey, String usernameKey) {
        this.passwordKey = passwordKey;
        this.schemaKey = schemaKey;
        this.secretName = secretName;
        this.urlKey = urlKey;
        this.usernameKey = usernameKey;
    }
}
