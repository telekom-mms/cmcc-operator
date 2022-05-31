/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class GenericClientComponent extends CorbaComponent implements HasMongoDBClient, HasSolrClient, HasService {

    public static final String SOLR_SERVER_KEY = "solr-server";
    public static final String SOLR_COLLECTION_KEY = "solr-collection";
    public static final String USE_MLS_KEY = "use-mls";
    public static final String UAPI_CLIENT_KIND_REF_KEY = "uapi-connection";
    public static final String SOLR_COLLECTION_PROPERTY_PREFIX = "solr-collection-prefix";

    String servletPathPattern;

    public GenericClientComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        //Image repo must be defined in component spec
        super(kubernetesClient, targetState, componentSpec, null);

        Map<String, String> defaultSchemas = new HashMap<>(Map.of(
                MONGODB_CLIENT_SECRET_REF_KIND, "blueprint"
        ));
        String solrCsr = getSolrClientRefName().orElseThrow(() ->
                new CustomResourceConfigError(SOLR_SERVER_KEY + " (server type, either " + SOLR_CLIENT_SERVER_FOLLOWER + " or " + SOLR_CLIENT_SERVER_LEADER + ") and " + SOLR_COLLECTION_KEY + " (solr core to access) must be set in extra section")
        );

        String uapiCsr = Optional.ofNullable(getComponentSpec().getExtra().get(UAPI_CLIENT_KIND_REF_KEY)).orElseThrow(() ->
                new CustomResourceConfigError(UAPI_CLIENT_KIND_REF_KEY + " must be set in extra section to type of uapi connection")
        );

        defaultSchemas.put(UAPI_CLIENT_SECRET_REF_KIND, uapiCsr);
        defaultSchemas.put(SOLR_CLIENT_SECRET_REF_KIND, solrCsr);
        setDefaultSchemas(defaultSchemas);
        servletPathPattern = String.join("|", getDefaults().getServletNames());
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getSolrClientSecretRef();
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildService());
        return resources;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();
        String appPropsPrefix = getComponentSpec().getExtra().getOrDefault(SOLR_COLLECTION_PROPERTY_PREFIX, getComponentSpec().getName().replace("-", "_"));

        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars(appPropsPrefix));

        return env;
    }

    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        if (getComponentSpec().getExtra().containsKey(USE_MLS_KEY)) {
            properties.put("repository.url", getTargetState().getServiceUrlFor("content-server", "mls"));
        }

        return properties;
    }

    @Override
    public Optional<ClientSecretRef> getSolrClientSecretRef() {
        Optional<String> solrClientRefName = getSolrClientRefName();

        if (solrClientRefName.isPresent()) {
            return getSolrClientSecretRef(solrClientRefName.get());
        }

        return Optional.empty();
    }

    private Optional<String> getSolrClientRefName() {
        return !hasConfiguredSolrClientRefName() ? Optional.empty() : Optional.of(
            HasSolrClient.getSolrClientSecretRefName(getComponentSpec().getExtra().get(SOLR_COLLECTION_KEY), getComponentSpec().getExtra().get(SOLR_SERVER_KEY))
        );
    }

    private boolean hasConfiguredSolrClientRefName() {
        return (getComponentSpec().getExtra().containsKey(SOLR_SERVER_KEY) && getComponentSpec().getExtra().containsKey(SOLR_COLLECTION_KEY));
    }
}
