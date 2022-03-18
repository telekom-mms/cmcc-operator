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
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.MANAGEMENT_SCHEMA;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public class WorkflowServerComponent extends CorbaComponent implements HasMongoDBClient, HasJdbcClient, HasService {

    @Getter
    final String databaseSchema;

    public WorkflowServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "workflow-server");
        databaseSchema = "management";
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getJdbcClientSecretRef();
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();
        env.addAll(getJdbcClientEnvVars("SQL_STORE"));
        env.addAll(getMongoDBEnvVars());
        env.add(EnvVarSimple("COM_COREMEDIA_CORBA_SERVER_HOST", getTargetState().getResourceNameFor(this)));
        env.add(EnvVarSimple("WORKFLOW_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));
        env.addAll(getUapiClientEnvVars("WORKFLOW"));

        return env;
    }

    @Override
    public String getJdbcClientDefaultSchema() {
        return MANAGEMENT_SCHEMA;
    }

    @Override
    public String getMongoDBClientDefaultCollectionPrefix() {
        return "blueprint";
    }

    @Override
    public String getUapiClientDefaultUsername() {
        return "workflow";
    }

}
