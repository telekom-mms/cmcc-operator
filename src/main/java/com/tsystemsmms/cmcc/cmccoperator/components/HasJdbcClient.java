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

/**
 * A Component that requires a JDBC client connection.
 */
public interface HasJdbcClient extends Component {
    String JDBC_CLIENT_SECRET_REF_KIND = "jdbc";

    /**
     * Returns the default MongoDB collection prefix for this component.
     */
    String getJdbcClientDefaultSchema();

    /**
     * Returns the secret reference for the default collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getJdbcClientSecretRef() {
        return getJdbcClientSecretRef(getJdbcClientDefaultSchema());
    }

    /**
     * Returns the secret reference for the given collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getJdbcClientSecretRef(String schemaName) {
        String secretName = getTargetState().getSecretName(JDBC_CLIENT_SECRET_REF_KIND, schemaName);
        String serviceName = getTargetState().getServiceNameFor("mysql");

        if (!getTargetState().getCmcc().getSpec().getWith().getDatabases()) {
            throw new CustomResourceConfigError("No MySQL client secret reference found for " + schemaName + ", and with.databases is false");
        }
        return getTargetState().getClientSecretRef(JDBC_CLIENT_SECRET_REF_KIND, schemaName,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                                ClientSecretRef.DEFAULT_DRIVER_KEY, "com.mysql.cj.jdbc.Driver",
                                ClientSecretRef.DEFAULT_HOSTNAME_KEY, serviceName,
                                ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                                ClientSecretRef.DEFAULT_SCHEMA_KEY, schemaName,
                                ClientSecretRef.DEFAULT_URL_KEY, "jdbc:mysql://" + serviceName + ":3306/" + schemaName,
                                ClientSecretRef.DEFAULT_USERNAME_KEY, schemaName
                        )
                )
        );
    }

    default EnvVarSet getJdbcClientEnvVars(String prefix) {
        EnvVarSet env = new EnvVarSet();
        ClientSecretRef csr = getJdbcClientSecretRef();

        env.addAll(List.of(
                csr.toEnvVar("MYSQL_HOST", csr.getHostnameKey()),
                csr.toEnvVar(prefix, "DRIVER", csr.getDriverKey()),
                csr.toEnvVar(prefix, "PASSWORD", csr.getPasswordKey()),
                csr.toEnvVar(prefix, "SCHEMA", csr.getSchemaKey()),
                csr.toEnvVar(prefix, "URL", csr.getUrlKey()),
                csr.toEnvVar(prefix, "USER", csr.getUsernameKey())
        ));
        return env;
    }
}
