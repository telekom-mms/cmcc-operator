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
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGenerator;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class StudioServerComponent extends CorbaComponent implements HasMySQLSchema, HasService {

    @Getter
    final String databaseSchema;
    String solrCollection;

    public StudioServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "studio-server");
        databaseSchema = "edcom";
        solrCollection = "studio";
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        resources.add(buildService());
        resources.addAll(buildIngress());
        return resources;
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
        env.addAll(getMongoDBEnvVars());
        env.addAll(getMySqlEnvVars());
        env.addAll(getSolrEnvVars("studio", solrCollection));
        return env;
    }


    @Override
    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(getSiteMappingProperties());
        properties.put("studio.preview-url-prefix", "https://" + concatOptional(getDefaults().getNamePrefix(), "preview.") + getDefaults().getIngressDomain());
        properties.put("themeImporter.apiKeyStore.basePath", "/var/tmp/themeimporter");

        return properties;
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
        env.add(EnvVarSimple("EDITORIAL_COMMENTS_DATASOURCE_DRIVER", "com.mysql.cj.jdbc.Driver"));
        env.add(EnvVarSecret("EDITORIAL_COMMENTS_DATASOURCE_PASSWORD", details.getSecretName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        env.add(EnvVarSimple("EDITORIAL_COMMENTS_DATASOURCE_URL", details.getJdbcUrl()));
        env.add(EnvVarSecret("EDITORIAL_COMMENTS_DATASOURCE_USERNAME", details.getSecretName(), TargetState.DATABASE_SECRET_USERNAME_KEY));
        env.add(EnvVarSimple("EDITORIAL_COMMENTS_DB_SCHEMA", getDatabaseSchema()));
        env.add(EnvVarSimple("EDITORIAL_COMMENTS_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA", getDatabaseSchema()));
        return env;
    }

    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("http")
                        .withContainerPort(8080)
                        .build(),
                new ContainerPortBuilder()
                        .withName("management")
                        .withContainerPort(8081)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("http").withPort(8080).withNewTargetPort("http").build(),
                new ServicePortBuilder().withName("management").withPort(8081).withNewTargetPort("management").build());
    }

    /**
     * Ingress for this component.
     *
     * @return the ingress definition
     */
    public Collection<? extends HasMetadata> buildIngress() {
        CmccIngressGenerator generator = getTargetState().getCmccIngressGeneratorFactory().instance(getTargetState(), getServiceName());
        return generator.buildStudioResources();
    }

}
