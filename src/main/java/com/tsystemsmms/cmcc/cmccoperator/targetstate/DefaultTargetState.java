/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.components.ComponentSpecBuilder;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MongoDBComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MySQLComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Create the runtime config based on the CRD data.
 */
@Slf4j
public class DefaultTargetState extends AbstractTargetState {
    public DefaultTargetState(BeanFactory beanFactory, KubernetesClient kubernetesClient, CmccIngressGeneratorFactory cmccIngressGeneratorFactory, ResourceNamingProviderFactory resourceNamingProviderFactory, CoreMediaContentCloud cmcc) {
        super(beanFactory, kubernetesClient, cmccIngressGeneratorFactory, resourceNamingProviderFactory, cmcc);
    }

    @Override
    public boolean converge() {
        Milestone previousMilestone = getCmcc().getStatus().getMilestone();

        if (cmcc.getSpec().getWith().getDatabases()) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("mongodb")
                            .withMilestone(Milestone.Created)
                            .build(),
                    ComponentSpecBuilder.ofType("mysql")
                            .withMilestone(Milestone.Created)
                            .build()
            ));
        }


        if (cmcc.getSpec().getWith().getManagement()) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("cae").withKind("preview").build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withKind("live").build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withKind("preview").build(),
                    ComponentSpecBuilder.ofType("content-feeder").build(),
                    ComponentSpecBuilder.ofType("content-server").withKind("cms").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("content-server").withKind("mls").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("elastic-worker").build(),
                    ComponentSpecBuilder.ofType("solr").withKind("leader").withMilestone(Milestone.Created).build(),
                    ComponentSpecBuilder.ofType("studio-client").build(),
                    ComponentSpecBuilder.ofType("studio-server").build(),
                    ComponentSpecBuilder.ofType("user-changes").build(),
                    ComponentSpecBuilder.ofType("workflow-server").build(),
                    ComponentSpecBuilder.ofType("overview").withMilestone(Milestone.ManagementReady).build(),

                    ComponentSpecBuilder.ofType("management-tools")
                            .withName("initcms")
                            .withMilestone(Milestone.ContentServerReady)
                            .withArgs(List.of("change-passwords"))
                            .build()
            ));
        }

        if (cmcc.getSpec().getWith().getDelivery().getRls() != 0
                || cmcc.getSpec().getWith().getDelivery().getMinCae() > 1
                || cmcc.getSpec().getWith().getDelivery().getMaxCae() > cmcc.getSpec().getWith().getDelivery().getMinCae()) {
            throw new RuntimeException("Unable to configure RLS and HPA, not implemented yet");
        }
        if (cmcc.getSpec().getWith().getDelivery().getMinCae() == 1) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("cae").withKind("live").build()
            ));
        }

        // trigger components to request clientResourceRefs, so we can build the config maps for the database servers
        requestRequiredResources();

        if (cmcc.getSpec().getWith().getDatabases()) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("mongodb")
                            .withMilestone(Milestone.Created)
                            .withExtra(MongoDBComponent.createUsersFromClientSecrets(this))
                            .build(),
                    ComponentSpecBuilder.ofType("mysql")
                            .withMilestone(Milestone.Created)
                            .withExtra(MySQLComponent.createUsersFromClientSecrets(this))
                            .build()
            ));
        }

        // allow explicitly declared components to override default ones
        componentCollection.addAll(cmcc.getSpec().getComponents());

        // move to the next milestone when everything is ready
        advanceToNextMilestoneOnComponentsReady();

        if (!getCmcc().getStatus().getMilestone().equals(previousMilestone)) {
            onMilestoneReached();
        }

        return previousMilestone.equals(getCmcc().getStatus().getMilestone());
    }

    @Override
    public void onMilestoneReached() {
        super.onMilestoneReached();
        if (cmcc.getStatus().getMilestone().equals(Milestone.ContentServerReady)) {
            log.info("[{}] Restarting CMS and MLS", getContextForLogging());
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "cms"));
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "mls"));
        }
    }
}
