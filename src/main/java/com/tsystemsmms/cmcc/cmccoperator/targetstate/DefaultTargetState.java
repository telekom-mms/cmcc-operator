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

import com.tsystemsmms.cmcc.cmccoperator.components.corba.CAEComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.WithOptions;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentSpecBuilder;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MongoDBComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MySQLComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;

/**
 * Create the runtime config based on the CRD data.
 */
@Slf4j
public class DefaultTargetState extends AbstractTargetState {
    public DefaultTargetState(BeanFactory beanFactory,
                              KubernetesClient kubernetesClient,
                              CmccIngressGeneratorFactory cmccIngressGeneratorFactory,
                              ResourceNamingProviderFactory resourceNamingProviderFactory,
                              ResourceReconcilerManager resourceReconcilerManager,
                              YamlMapper yamlMapper,
                              CustomResource cmcc) {
        super(beanFactory,
                kubernetesClient,
                cmccIngressGeneratorFactory,
                resourceNamingProviderFactory,
                resourceReconcilerManager,
                yamlMapper,
                cmcc);
    }

    @Override
    public void convergeDefaultComponents() {
        if (cmcc.getSpec().getWith().getDatabases()) {
            if (cmcc.getSpec().getWith().databaseCreateForKind("mongodb")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mongodb")
                                .withMilestone(Milestone.Created)
                                .build()
                ));
            }
            if (cmcc.getSpec().getWith().databaseCreateForKind("mysql")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mysql")
                                .withMilestone(Milestone.Created)
                                .build()
                ));
            }
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
                    ComponentSpecBuilder.ofType("solr").withMilestone(Milestone.Created).build(),
                    ComponentSpecBuilder.ofType("studio-client").build(),
                    ComponentSpecBuilder.ofType("studio-server").build(),
                    ComponentSpecBuilder.ofType("user-changes").build(),
                    ComponentSpecBuilder.ofType("workflow-server").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("overview").withMilestone(Milestone.ManagementReady).build(),

                    ComponentSpecBuilder.ofType("management-tools")
                            .withName("initcms")
                            .withMilestone(Milestone.ContentServerInitialized)
                            .withArgs(List.of("change-passwords"))
                            .build()
            ));
        }

        WithOptions.WithDelivery delivery = cmcc.getSpec().getWith().getDelivery();
        if (getInt(delivery.getRls()) > 0) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("content-server").withKind("rls").withMilestone(Milestone.ManagementReady).build()
            ));
        } else if (getInt(delivery.getRls()) == 0) {
            // make sure we don't have an RLS component even if the custom resource has it defined
            componentCollection.removeOfTypeAndKind("content-server", "rls");
        }
        if (getInt(delivery.getMaxCae()) > getInt(delivery.getMinCae())) {
            throw new RuntimeException("Unable to configure Live CAE Horizontal Pod Autoscaler: not implemented yet");
        }
        if (getInt(delivery.getMinCae()) > 0) {
            Map<String, String> liveCaeExtra = Map.of(
                    CAEComponent.EXTRA_REPLICAS, String.valueOf(getInt(delivery.getMinCae()))
            );
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("cae").withKind("live").withExtra(liveCaeExtra).build()
            ));
        }
    }

    @Override
    public void convergeOverrideResources() {
        if (cmcc.getSpec().getWith().getDatabases()) {
            if (cmcc.getSpec().getWith().databaseCreateForKind("mongodb")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mongodb")
                                .withMilestone(Milestone.Created)
                                .withExtra(MongoDBComponent.createUsersFromClientSecrets(this))
                                .build()
                ));
            }
            if (cmcc.getSpec().getWith().databaseCreateForKind("mysql")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mysql")
                                .withMilestone(Milestone.Created)
                                .withExtra(MySQLComponent.createUsersFromClientSecrets(this))
                                .build()
                ));
            }
        }

        if (cmcc.getStatus().getMilestone() == Milestone.Ready && !getCmcc().getSpec().getJob().isBlank()) {
            log.info("[{}] starting job \"{}\"", getContextForLogging(), getCmcc().getSpec().getJob());
            getCmcc().getStatus().setJob(getCmcc().getSpec().getJob());
            getCmcc().getStatus().setMilestone(Milestone.RunJob);
            getCmcc().getSpec().setJob("");
        }

        // add job after the custom jobs, so the modified job overrides the template in components
        if (cmcc.getStatus().getMilestone() == Milestone.RunJob && !getCmcc().getStatus().getJob().isBlank()) {
            Optional<ComponentSpec> toRun = cmcc.getSpec().getComponents().stream().filter(c -> c.getName().equals(getCmcc().getStatus().getJob())).findAny();
            if (toRun.isEmpty()) {
                getCmcc().getStatus().setJob("");
                throw new CustomResourceConfigError("No such job \"" + getCmcc().getStatus().getJob() + "\"");
            }
            ComponentSpec job = Utils.deepClone(toRun.get(), ComponentSpec.class);
            job.setMilestone(Milestone.RunJob);
            componentCollection.add(job);
        }
    }

    @Override
    public void onMilestoneReached() {
        super.onMilestoneReached();

        if (cmcc.getStatus().getMilestone() == Milestone.ContentServerReady) {
            log.info("[{}] Restarting CMS and MLS", getContextForLogging());
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "cms"));
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "mls"));
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException e) {
                //
            }
        }
    }
}
