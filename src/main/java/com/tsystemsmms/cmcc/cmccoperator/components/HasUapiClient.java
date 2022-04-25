/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components;

import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * A component that uses a UAPI/Corba client to connect to a Content Server.
 */
public interface HasUapiClient extends Component {
    /**
     * CoreMedia has a hard-coded admin user with this name.
     */
    String UAPI_ADMIN_USERNAME = "admin";
    String UAPI_CLIENT_SECRET_REF_KIND = "uapi";

    /**
     * Returns the name of the secret for a given UAPI client.
     *
     * @param resourceName name of the resource
     * @return secret name.
     */
    static String getUapiClientSecretName(String resourceName) {
        return concatOptional(UAPI_CLIENT_SECRET_REF_KIND, resourceName);
    }

    /**
     * Returns the default UAPI username for this component.
     */
    default String getUapiClientDefaultUsername() {
        return Objects.requireNonNull(getSchemas().get(UAPI_CLIENT_SECRET_REF_KIND), () -> "A schema name was requested for " + UAPI_CLIENT_SECRET_REF_KIND + ", but the component " + this.getSpecName() + " does not define one.");
    }

    /**
     * Returns the secret reference for the default collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getUapiClientSecretRef() {
        return getUapiClientSecretRef(getUapiClientDefaultUsername());
    }


    /**
     * Returns the secret reference for the given collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getUapiClientSecretRef(String schemaName) {
        return getTargetState().getClientSecretRef(UAPI_CLIENT_SECRET_REF_KIND, schemaName,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                        ClientSecretRef.DEFAULT_SCHEMA_KEY, schemaName,
                        ClientSecretRef.DEFAULT_USERNAME_KEY, schemaName
                ))
        );
    }

    /**
     * Return the client secret refs as the standard environment variables. The names get appended to the optional
     * prefix.
     *
     * @param prefix name prefix, or empty string or null.
     * @return a set of environment variables
     */
    default EnvVarSet getUapiClientEnvVars(String prefix) {
        EnvVarSet env = new EnvVarSet();
        ClientSecretRef csr = getUapiClientSecretRef();

        env.addAll(List.of(
                csr.toEnvVar(prefix, "PASSWORD", csr.getPasswordKey()),
                csr.toEnvVar(prefix, "USER", csr.getUsernameKey())
        ));
        return env;
    }
}
