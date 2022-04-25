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
import io.fabric8.kubernetes.api.model.EnvVar;
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

    @JsonPropertyDescription("resource name of the secret")
    String secretName;
    @JsonPropertyDescription("entry in the secret for the driver class or name")
    String driverKey;
    @JsonPropertyDescription("entry in the secret for hostname of the server")
    String hostnameKey;
    @JsonPropertyDescription("entry in the secret for the password to authenticate with")
    String passwordKey;
    @JsonPropertyDescription("entry in the secret for the schema, database or service to use")
    String schemaKey;
    @JsonPropertyDescription("entry in the secret for the url to connect to")
    String urlKey;
    @JsonPropertyDescription("entry in the secret for the username to authenticate with")
    String usernameKey;

    /**
     * Creates a ClientSecretRef with the keys all filled with their default values.
     *
     * @param secretName name of the referenced Secret
     * @return reference
     */
    public static ClientSecretRef defaultClientSecretRef(String secretName) {
        ClientSecretRef csr = new ClientSecretRef();
        csr.secretName = secretName;
        csr.driverKey = DEFAULT_DRIVER_KEY;
        csr.hostnameKey = DEFAULT_HOSTNAME_KEY;
        csr.passwordKey = DEFAULT_PASSWORD_KEY;
        csr.schemaKey = DEFAULT_SCHEMA_KEY;
        csr.urlKey = DEFAULT_URL_KEY;
        csr.usernameKey = DEFAULT_USERNAME_KEY;
        return csr;
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

    /**
     * Return a new ClientSecretRef, with any null values replaced by their default values.
     *
     * @return a clone
     */
    public ClientSecretRef cloneWithDefaults() {
        ClientSecretRef csr = defaultClientSecretRef(this.getSecretName());

        if (this.driverKey != null)
            csr.driverKey = this.driverKey;
        if (this.hostnameKey != null)
            csr.hostnameKey = this.hostnameKey;
        if (this.passwordKey != null)
            csr.passwordKey = this.passwordKey;
        if (this.schemaKey != null)
            csr.schemaKey = this.schemaKey;
        if (this.urlKey != null)
            csr.urlKey = this.urlKey;
        if (this.usernameKey != null)
            csr.usernameKey = this.usernameKey;
        return csr;
    }
}
