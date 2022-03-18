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

import io.fabric8.kubernetes.api.model.EnvVar;
import lombok.Builder;
import lombok.Data;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSecret;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptionalWithJoiner;

/**
 * All information necessary to reference a secret containing all detail for a client to connect to a server.
 * This allows components to create EnvVars referencing secrets for database or UAPI connections.
 */
@Data
public class ClientSecretRef {
    public static final String DEFAULT_DRIVER_KEY = "driver";
    public static final String DEFAULT_HOSTNAME_KEY = "hostname";
    public static final String DEFAULT_PASSWORD_KEY = "password";
    public static final String DEFAULT_SCHEMA_KEY = "schema";
    public static final String DEFAULT_URL_KEY = "url";
    public static final String DEFAULT_USERNAME_KEY = "username";

    String driverKey = DEFAULT_DRIVER_KEY;
    String hostnameKey = DEFAULT_HOSTNAME_KEY;
    String passwordKey = DEFAULT_PASSWORD_KEY;
    String schemaKey = DEFAULT_SCHEMA_KEY;
    String secretName;
    String urlKey = DEFAULT_URL_KEY;
    String usernameKey = DEFAULT_USERNAME_KEY;

    public ClientSecretRef() {
    }

    public static ClientSecretRefBuilder builder() {
        return new ClientSecretRefBuilder();
    }

    public ClientSecretRef(ClientSecretRef that) {
        this.driverKey = that.driverKey;
        this.hostnameKey = that.hostnameKey;
        this.passwordKey = that.passwordKey;
        this.schemaKey = that.schemaKey;
        this.secretName = that.secretName;
        this.urlKey = that.urlKey;
        this.usernameKey = that.usernameKey;
    }

    public static ClientSecretRef defaultClientSecretRef(String secretName) {
        return new ClientSecretRef(DEFAULT_DRIVER_KEY, DEFAULT_HOSTNAME_KEY, DEFAULT_PASSWORD_KEY, DEFAULT_SCHEMA_KEY, secretName, DEFAULT_URL_KEY, DEFAULT_USERNAME_KEY);
    }

    /**
     * Returns an EnvVar with the given name and the given key.
     *
     * @param name name of the var
     * @param key  key of the reference
     * @return a secret referencing env var
     */
    public EnvVar toEnvVar(String name, String key) {
        return toEnvVar("", name, key);
    }

    /**
     * Returns an EnvVar with the given prefix and name and the given key. The prefix and the name are joined by "_".
     *
     * @param name name of the var
     * @param key  key of the reference
     * @return a secret referencing env var
     */
    public EnvVar toEnvVar(String prefix, String name, String key) {
        return EnvVarSecret(concatOptionalWithJoiner("_", prefix, name), getSecretName(), key);
    }

    @Builder
    private ClientSecretRef(String driverKey, String hostnameKey, String passwordKey, String schemaKey, String secretName, String urlKey, String usernameKey) {
        this.driverKey = driverKey;
        this.hostnameKey = hostnameKey;
        this.passwordKey = passwordKey;
        this.schemaKey = schemaKey;
        this.secretName = secretName;
        this.urlKey = urlKey;
        this.usernameKey = usernameKey;
    }
}
