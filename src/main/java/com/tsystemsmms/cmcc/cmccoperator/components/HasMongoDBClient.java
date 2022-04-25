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

public interface HasMongoDBClient extends Component {
    String MONGODB_CLIENT_SECRET_REF_KIND = "mongodb";

    /**
     * Returns the default MongoDB collection prefix for this component.
     */
    default String getMongoDBClientDefaultCollectionPrefix() {
        return Objects.requireNonNull(getSchemas().get(MONGODB_CLIENT_SECRET_REF_KIND), () -> "A schema name was requested for " + MONGODB_CLIENT_SECRET_REF_KIND + ", but the component " + this.getSpecName() + " does not define one.");
    }

    /**
     * Returns the secret reference for the default collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getMongoDBClientSecretRef() {
        return getMongoDBClientSecretRef(getMongoDBClientDefaultCollectionPrefix());
    }

    /**
     * Returns the secret reference for the given collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getMongoDBClientSecretRef(String schemaName) {
        return getTargetState().getClientSecretRef(MONGODB_CLIENT_SECRET_REF_KIND, schemaName,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                        ClientSecretRef.DEFAULT_SCHEMA_KEY, schemaName,
                        ClientSecretRef.DEFAULT_URL_KEY, "mongodb://" + schemaName + ":" + password + "@" + getTargetState().getServiceNameFor("mongodb") + ":27017/" + schemaName,
                        ClientSecretRef.DEFAULT_USERNAME_KEY, schemaName
                ))
        );
    }

    default EnvVarSet getMongoDBEnvVars() {
        EnvVarSet env = new EnvVarSet();
        ClientSecretRef csr = getMongoDBClientSecretRef();

        env.addAll(List.of(
                csr.toEnvVar("MONGODB_CLIENTURI", csr.getUrlKey()),
                csr.toEnvVar("MONGODB_PREFIX", csr.getSchemaKey())
        ));
        return env;
    }
}
