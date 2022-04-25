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
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * A component that uses a Solr client to connect to a Solr server.
 */
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
    default Optional<ClientSecretRef> getSolrClientSecretRef() {
        return getSolrClientSecretRef(getSolrClientDefaultCollection());
    }

    /**
     * Returns the secret reference for the given collection prefix.
     *
     * @return reference
     */
    default Optional<ClientSecretRef> getSolrClientSecretRef(String csr) {
        SolrCoordinates c = new SolrCoordinates(csr, getTargetState());

        return getTargetState().getClientSecretRef(SOLR_CLIENT_SECRET_REF_KIND, csr);
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
     * @param component component
     * @return one or more environment variables
     */
    default EnvVarSet getSolrEnvVars(String component) {
        EnvVarSet env = new EnvVarSet();
        Optional<ClientSecretRef> ocsr = getSolrClientSecretRef();

        if (ocsr.isPresent()) {
            ClientSecretRef csr = ocsr.get();
            env.addAll(List.of(
                    csr.toEnvVar("SOLR_URL", csr.getUrlKey()),
                    csr.toEnvVar("SOLR_" + component.toUpperCase() + "_COLLECTION", csr.getSchemaKey())
            ));
        } else {
            SolrCoordinates c = new SolrCoordinates(getSolrClientDefaultCollection(), getTargetState());
            env.addAll(List.of(
                    EnvVarSimple("SOLR_URL", c.url),
                    EnvVarSimple("SOLR_" + component.toUpperCase() + "_COLLECTION", c.collection)
            ));
        }
        return env;
    }

    class SolrCoordinates {
        public final String collection;
        public final String server;
        public final String url;

        public SolrCoordinates(String csr, TargetState targetState) {
            String[] parts = csr.split("-");
            if (parts.length != 2) {
                throw new CustomResourceConfigError("Solr ClientSecretRef name \"" + csr + "\" must consist of two parts separated by -");
            }
            collection = parts[0];
            server = parts[1];
            url = targetState.getComponentCollection().getHasServiceComponent("solr").getServiceUrl(server);
        }
    }
}
