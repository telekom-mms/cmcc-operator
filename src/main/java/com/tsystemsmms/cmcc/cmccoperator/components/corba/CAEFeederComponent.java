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

import com.tsystemsmms.cmcc.cmccoperator.components.HasJdbcClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public class CAEFeederComponent extends CorbaComponent implements HasJdbcClient, HasMongoDBClient, HasSolrClient {

    public static final String KIND_LIVE = "live";
    public static final String KIND_PREVIEW = "preview";
    public static final String EXTRA_DATABASE_SCHEMA = "databaseSchema";

    public CAEFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "cae-feeder");

        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either " + KIND_LIVE + " or " + KIND_PREVIEW);
        switch (componentSpec.getKind()) {
            case KIND_LIVE:
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, "mcaefeeder",
                        MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                        SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName("live", SOLR_CLIENT_SERVER_LEADER),
                        UAPI_CLIENT_SECRET_REF_KIND, "feeder"
                ));
                break;
            case KIND_PREVIEW:
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, "caefeeder",
                        MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                        SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName("preview", SOLR_CLIENT_SERVER_LEADER),
                        UAPI_CLIENT_SECRET_REF_KIND, "feeder"
                ));
                break;
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LIVE + " or " + KIND_PREVIEW);
        }
        if (getComponentSpec().getExtra().containsKey(EXTRA_DATABASE_SCHEMA))
            getSchemas().put(JDBC_CLIENT_SECRET_REF_KIND, getComponentSpec().getExtra().get(EXTRA_DATABASE_SCHEMA));
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getJdbcClientSecretRef();
        getSolrClientSecretRef();
    }

    @Override
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = super.getSelectorLabels();
        labels.put("cmcc.tsystemsmms.com/kind", getComponentSpec().getKind());
        return labels;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getJdbcClientEnvVars("JDBC"));
        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars("cae"));

        if (getComponentSpec().getKind().equals(KIND_LIVE))
            env.add(EnvVarSimple("REPOSITORY_URL", getTargetState().getServiceUrlFor("content-server", "mls")));

        return env;
    }
}
