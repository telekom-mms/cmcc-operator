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
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGenerator;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Slf4j
public class StudioServerComponent extends CorbaComponent implements HasMongoDBClient, HasJdbcClient, HasService, HasSolrClient {

    final String solrCollection;

    public StudioServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "studio-server");
        setDefaultSchemas(Map.of(
                JDBC_CLIENT_SECRET_REF_KIND, "studio",
                MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName("studio", SOLR_CLIENT_SERVER_LEADER),
                UAPI_CLIENT_SECRET_REF_KIND, "studio"
        ));
        solrCollection = "studio";
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getJdbcClientSecretRef();
        getSolrClientSecretRef();
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
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getMongoDBEnvVars());
        env.addAll(getJdbcClientEnvVars("EDITORIAL_COMMENTS_DATASOURCE"));
        env.addAll(getJdbcClientEnvVars("EDITORIAL_COMMENTS_DB"));
        env.addAll(getJdbcClientEnvVars("EDITORIAL_COMMENTS_LIQUIBASE"));
        // Studio Hibernate+Liquibase needs these additional settings
        env.add(new EnvVarBuilder(env.get("EDITORIAL_COMMENTS_DATASOURCE_DRIVER").orElseThrow())
                .withName("EDITORIAL_COMMENTS_DATASOURCE_DRIVER_CLASS_NAME").build());
        env.add(new EnvVarBuilder(env.get("EDITORIAL_COMMENTS_DATASOURCE_SCHEMA").orElseThrow())
                .withName("EDITORIAL_COMMENTS_JPA_PROPERTIES_HIBERNATE_DEFAULT_SCHEMA").build());
        env.add(new EnvVarBuilder(env.get("EDITORIAL_COMMENTS_DATASOURCE_USER").orElseThrow())
                .withName("EDITORIAL_COMMENTS_DATASOURCE_USERNAME").build());
        env.addAll(getSolrEnvVars("studio"));

        return env;
    }


    @Override
    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(getSiteMappingProperties());
        properties.put("studio.previewUrlPrefix", "https://" + getTargetState().getPreviewHostname());
        // add the Studio URL twice time to work around CoreMedia bug CMS-21564. Normally, it should be sufficient
        // to only have previewUrlPrefix set.
        properties.put("studio.preview-url-whitelist[0]", "https://" + getTargetState().getPreviewHostname());
        properties.put("studio.preview-url-whitelist[1]", "https://" + getTargetState().getPreviewHostname());
        properties.put("themeImporter.apiKeyStore.basePath", "/var/tmp/themeimporter");

        return properties;
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
        CmccIngressGenerator generator = getTargetState().getCmccIngressGeneratorFactory().instance(getTargetState(), getTargetState().getServiceNameFor(this));
        return generator.buildStudioResources();
    }
}
