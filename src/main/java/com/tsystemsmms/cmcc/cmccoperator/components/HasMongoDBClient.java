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

public interface HasMongoDBClient extends Component {
    String MONGODB_CLIENT_SECRET_REF_KIND = "mongodb";

    /**
     * Returns the default MongoDB collection prefix for this component.
     */
    String getMongoDBClientDefaultCollectionPrefix();

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
        String secretName = getTargetState().getSecretName(MONGODB_CLIENT_SECRET_REF_KIND, schemaName);
        String serviceName = getTargetState().getServiceNameFor("mongodb");

        if (!getTargetState().getCmcc().getSpec().getWith().getDatabases()) {
            throw new CustomResourceConfigError("No MongoDB client secret reference found for " + schemaName + ", and with.databases is false");
        }
        return getTargetState().getClientSecretRef(MONGODB_CLIENT_SECRET_REF_KIND, schemaName,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                        ClientSecretRef.DEFAULT_SCHEMA_KEY, schemaName,
                        ClientSecretRef.DEFAULT_URL_KEY, "mongodb://" + schemaName + ":" + password + "@" + serviceName + ":27017/" + schemaName,
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
