/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator;

import com.tsystemsmms.cmcc.cmccoperator.components.corba.*;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.*;
import com.tsystemsmms.cmcc.cmccoperator.components.job.MgmtToolsJobComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@Configuration
public class ComponentBeanFactories {
    @Bean("component:blob-server")
    @Scope(SCOPE_PROTOTYPE)
    public BlobsComponent blobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new BlobsComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:content-feeder")
    @Scope(SCOPE_PROTOTYPE)
    public ContentFeederComponent contentFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new ContentFeederComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:cae")
    @Scope(SCOPE_PROTOTYPE)
    public CAEComponent caeComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new CAEComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:cae-feeder")
    @Scope(SCOPE_PROTOTYPE)
    public CAEFeederComponent caeFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new CAEFeederComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:content-server")
    @Scope(SCOPE_PROTOTYPE)
    public ContentServerComponent contentServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new ContentServerComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:elastic-worker")
    @Scope(SCOPE_PROTOTYPE)
    public ElasticWorkerComponent elasticWorkerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new ElasticWorkerComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:management-tools")
    @Scope(SCOPE_PROTOTYPE)
    public MgmtToolsJobComponent mgmtToolsJobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new MgmtToolsJobComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:mongodb")
    @Scope(SCOPE_PROTOTYPE)
    public MongoDBComponent mongoDBComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new MongoDBComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:mysql")
    @Scope(SCOPE_PROTOTYPE)
    public MySQLComponent mySQLComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new MySQLComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:nginx")
    @Scope(SCOPE_PROTOTYPE)
    public NginxComponent nginxComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new NginxComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:overview")
    @Scope(SCOPE_PROTOTYPE)
    public OverviewComponent overviewComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new OverviewComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:solr")
    @Scope(SCOPE_PROTOTYPE)
    public SolrComponent solrServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new SolrComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:studio-client")
    @Scope(SCOPE_PROTOTYPE)
    public StudioClientComponent studioClientComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new StudioClientComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:studio-server")
    @Scope(SCOPE_PROTOTYPE)
    public StudioServerComponent studioServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new StudioServerComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:user-changes")
    @Scope(SCOPE_PROTOTYPE)
    public UserChangesComponent userChangesServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new UserChangesComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:workflow-server")
    @Scope(SCOPE_PROTOTYPE)
    public WorkflowServerComponent workflowServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new WorkflowServerComponent(kubernetesClient, targetState, cs);
    }

    @Bean("component:generic-client")
    @Scope(SCOPE_PROTOTYPE)
    public GenericClientComponent genericClientComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec cs) {
        return new GenericClientComponent(kubernetesClient, targetState, cs);
    }
}
