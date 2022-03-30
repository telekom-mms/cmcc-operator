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
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public class CAEFeederComponent extends CorbaComponent implements HasMongoDBClient, HasJdbcClient {

    public static final String KIND_LIVE = "live";
    public static final String KIND_PREVIEW = "preview";
    public static final String EXTRA_DATABASE_SCHEMA = "databaseSchema";

    @Getter
    String databaseSchema;
    String solrCollection;

    public CAEFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "cae-feeder");
        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either " + KIND_LIVE + " or " + KIND_PREVIEW);
        switch (componentSpec.getKind()) {
            case KIND_LIVE:
                databaseSchema = "mcaefeeder";
                solrCollection = "live";
                break;
            case KIND_PREVIEW:
                databaseSchema = "caefeeder";
                solrCollection = "preview";
                break;
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LIVE + " or " + KIND_PREVIEW);
        }
        if (getComponentSpec().getExtra().containsKey(EXTRA_DATABASE_SCHEMA))
            databaseSchema = getComponentSpec().getExtra().get(EXTRA_DATABASE_SCHEMA);
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getJdbcClientSecretRef();
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
        env.addAll(getSolrEnvVars("cae", solrCollection));

        if (getComponentSpec().getKind().equals(KIND_LIVE))
            env.add(EnvVarSimple("REPOSITORY_URL", getTargetState().getServiceUrlFor("content-server", "mls")));

        return env;
    }

    @Override
    public String getJdbcClientDefaultSchema() {
        return databaseSchema;
    }

    @Override
    public String getMongoDBClientDefaultCollectionPrefix() {
        return "blueprint";
    }

    @Override
    public String getUapiClientDefaultUsername() {
        return "feeder";
    }
}
