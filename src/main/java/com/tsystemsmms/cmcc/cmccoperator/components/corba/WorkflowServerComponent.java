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

import com.tsystemsmms.cmcc.cmccoperator.components.HasMySQLSchema;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class WorkflowServerComponent extends CorbaComponent implements HasMySQLSchema, HasService {

    @Getter
    final String databaseSchema;

    public WorkflowServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "workflow-server");
        databaseSchema = "management";
    }

    @Override
    public String getDatabaseSecretName() {
        return concatOptional(
                getDefaults().getNamePrefix(),
                getSpecName(),
                "mysql",
                getDatabaseSchema());
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();
        env.addAll(getMySqlEnvVars());
        env.addAll(getMongoDBEnvVars());
        env.add(EnvVarSimple("COM_COREMEDIA_CORBA_SERVER_HOST", getResourceName()));
        env.add(EnvVarSimple("WORKFLOW_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));
        return env;
    }

    /**
     * Get a list of environment variables to configure the MySQL database connection of the component.
     *
     * @return list of env vars
     */
    public EnvVarSet getMySqlEnvVars() {
        MySQLDetails details = getMySQLDetails(getDatabaseSchema());
        EnvVarSet env = new EnvVarSet();

        env.add(EnvVarSimple("MYSQL_HOST", details.getHostName())); // needed for MySQL command line tools
        env.add(EnvVarSimple("SQL_STORE_DRIVER", "com.mysql.cj.jdbc.Driver"));
        env.add(EnvVarSecret("SQL_STORE_PASSWORD", details.getSecretName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        env.add(EnvVarSimple("SQL_STORE_SCHEMA", getDatabaseSchema()));
        env.add(EnvVarSimple("SQL_STORE_URL", details.getJdbcUrl()));
        env.add(EnvVarSecret("SQL_STORE_USER", details.getSecretName(), TargetState.DATABASE_SECRET_USERNAME_KEY));
        return env;
    }

}
