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
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient.SOLR_CLIENT_SECRET_REF_KIND;

@Slf4j
public class ContentFeederComponent extends CorbaComponent implements HasMongoDBClient, HasSolrClient {

    public ContentFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "content-feeder");
        setDefaultSchemas(Map.of(
                MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                SOLR_CLIENT_SECRET_REF_KIND, "studio",
                UAPI_CLIENT_SECRET_REF_KIND, "feeder"
        ));
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
        return resources;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars("content"));

        return env;
    }
}
