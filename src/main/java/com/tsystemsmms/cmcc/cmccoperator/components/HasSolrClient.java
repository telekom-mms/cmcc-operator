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

public interface HasSolrClient extends Component {
    String SOLR_CLIENT_SECRET_REF_KIND = "solr";
    String SOLR_CLIENT_SERVER_LEADER = "leader";
    String SOLR_CLIENT_SERVER_FOLLOWER = "follower";

    /**
     * Returns the default MongoDB collection prefix for this component.
     */
    default String getSolrClientDefaultCollection() {
        return Objects.requireNonNull(getSchemas().get(SOLR_CLIENT_SECRET_REF_KIND),
                () -> "A schema name was requested for " + SOLR_CLIENT_SECRET_REF_KIND + ", but the component " + this.getSpecName() + " does not define one.");
    }

    /**
     * Returns the secret reference for the default collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getSolrClientSecretRef() {
        return getSolrClientSecretRef(getSolrClientDefaultCollection());
    }

    /**
     * Returns the secret reference for the given collection prefix.
     *
     * @return reference
     */
    default ClientSecretRef getSolrClientSecretRef(String csr) {
        String[] parts = csr.split("-");
        if (parts.length != 2) {
            throw new CustomResourceConfigError("Solr ClientSecretRef name \"" + csr + "\" must consist of two parts separated by -");
        }
        String collection = parts[0];
        String server = parts[1];
        String url = getTargetState().getComponentCollection().getHasServiceComponent("solr").getServiceUrl(server);
        return getTargetState().getClientSecretRef(SOLR_CLIENT_SECRET_REF_KIND, csr,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                        ClientSecretRef.DEFAULT_SCHEMA_KEY, collection,
                        ClientSecretRef.DEFAULT_URL_KEY, url,
                        ClientSecretRef.DEFAULT_USERNAME_KEY, collection
                ))
        );
    }

    /**
     * Returns the name to use for a ClientSecretRef for the given collection and server.
     *
     * @param collection Solr collection
     * @param server     type of server, leader or follower
     */
    static String getSolrClientSecretRefName(String collection, String server) {
        return concatOptional(collection, server);
    }


    /**
     * Creates a set of environment variables suitable for this component.
     *
     * @param component
     * @return
     */
    default EnvVarSet getSolrEnvVars(String component) {
        EnvVarSet env = new EnvVarSet();
        ClientSecretRef csr = getSolrClientSecretRef();

        env.addAll(List.of(
                csr.toEnvVar("SOLR_URL", csr.getUrlKey()),
                csr.toEnvVar("SOLR_" + component.toUpperCase() + "_COLLECTION", csr.getSchemaKey())
        ));
        return env;
    }
}
