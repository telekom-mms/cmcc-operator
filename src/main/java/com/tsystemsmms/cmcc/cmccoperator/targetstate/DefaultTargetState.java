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
import com.tsystemsmms.cmcc.cmccoperator.components.corba.CAEComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MongoDBComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.MySQLComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.crds.WithOptions;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.ingress.UrlMappingBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.AbstractRenderingCorbaComponent.KIND_LIVE;
import static com.tsystemsmms.cmcc.cmccoperator.components.corba.CAEFeederComponent.*;
import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.CONTENT_SERVER;
import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.KIND_RLS;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;

/**
 * Create the runtime config based on the CRD data.
 */
@Slf4j
public class DefaultTargetState extends AbstractTargetState {
    public DefaultTargetState(BeanFactory beanFactory,
                              KubernetesClient kubernetesClient,
                              ResourceNamingProviderFactory resourceNamingProviderFactory,
                              ResourceReconcilerManager resourceReconcilerManager,
                              Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories,
                              YamlMapper yamlMapper,
                              CustomResource cmcc) {
        super(beanFactory,
                kubernetesClient,
                resourceNamingProviderFactory,
                resourceReconcilerManager,
                urlMappingBuilderFactories,
                yamlMapper,
                cmcc);
    }

    @Override
    public void convergeDefaultComponents() {
        if (cmcc.getSpec().getWith().getDatabases()) {
            if (cmcc.getSpec().getWith().databaseCreateForKind("mongodb")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mongodb")
                                .withMilestone(Milestone.DeploymentStarted)
                                .build()
                ));
            }
            if (cmcc.getSpec().getWith().databaseCreateForKind("mysql")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mysql")
                                .withMilestone(Milestone.DeploymentStarted)
                                .build()
                ));
            }
        }

        if (cmcc.getSpec().getWith().getManagement()) {
            componentCollection.addAll(List.of(
                    ComponentSpecBuilder.ofType("content-server").withKind("cms").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("content-server").withKind("mls").withMilestone(Milestone.DatabasesReady).build(),
                    ComponentSpecBuilder.ofType("workflow-server").withMilestone(Milestone.ContentServerInitialized).build(),
                    ComponentSpecBuilder.ofType("cae").withMilestone(Milestone.ContentServerReady).withKind("preview").build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withMilestone(Milestone.ContentServerReady).withKind("preview").build(),
                    ComponentSpecBuilder.ofType("headless").withMilestone(Milestone.ContentServerReady).withKind("preview").build(),
                    ComponentSpecBuilder.ofType("content-feeder").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("elastic-worker").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("studio-client").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("studio-server").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("user-changes").withMilestone(Milestone.ContentServerReady).build(),
                    ComponentSpecBuilder.ofType("cae-feeder").withMilestone(Milestone.ManagementReady).withKind("live").build(),
                    ComponentSpecBuilder.ofType("overview").withMilestone(Milestone.DeliveryServicesReady).build(),


                    ComponentSpecBuilder.ofType("management-tools")
                            .withName("initcms")
                            .withMilestone(Milestone.ContentServerInitialized)
                            .withArgs(List.of("change-passwords"))
                            .build()
            ));

            // legacy solr handling if none specified or if there is one without KIND
            if (cmcc.getSpec().getComponents().stream().noneMatch(component -> component.getType().equals("solr"))
                    || cmcc.getSpec().getComponents().stream().anyMatch(component -> component.getType().equals("solr") && StringUtils.isEmpty(component.getKind()))) {
                componentCollection.add(ComponentSpecBuilder.ofType("solr").withMilestone(Milestone.DeploymentStarted).build());
            } else {
                // otherwise new mode: "Leader" and "Follower" are explicitly designated
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("solr").withKind(SOLR_CLIENT_SERVER_LEADER).withMilestone(Milestone.DeploymentStarted).build(),
                        ComponentSpecBuilder.ofType("solr").withKind(SOLR_CLIENT_SERVER_FOLLOWER).withMilestone(Milestone.ManagementReady).build()
                ));
            }
        }

        WithOptions.WithDelivery delivery = cmcc.getSpec().getWith().getDelivery();
        var maxCae = getInt(delivery.getMaxCae());
        var minCae = getInt(delivery.getMinCae());
        if (maxCae > minCae) {
            log.trace("[{}] CMCC component can be scaled on CAE between {} and {} replicas", getContextForLogging(), minCae, maxCae);
        }
        if (getInt(delivery.getMinCae()) > 0) {
            Map<String, String> liveCaeExtra = Map.of(
                    CAEComponent.EXTRA_REPLICAS, String.valueOf(getInt(delivery.getMinCae()))
            );
            componentCollection.add(ComponentSpecBuilder.ofType("cae")
                    .withMilestone(Milestone.DeliveryServicesReady)
                    .withKind(KIND_LIVE)
                    .withExtra(liveCaeExtra).build()
            );
        }
        var maxHeadless = getInt(delivery.getMaxHeadless());
        var minHeadless = getInt(delivery.getMinHeadless());
        if (maxHeadless > minHeadless) {
            log.trace("[{}] CMCC component can be scaled on Headless between {} and {} replicas", getContextForLogging(), minHeadless, maxHeadless);
        }
        if (getInt(delivery.getMinHeadless()) > 0) {
            componentCollection.add(ComponentSpecBuilder.ofType("headless")
                    .withMilestone(Milestone.DeliveryServicesReady)
                    .withKind(KIND_LIVE)
                    .build());
        }
    }

    @Override
    public void convergeOverrideResources() {
        // check for delivery config first, because DB needs to consider RLS schemas also
        WithOptions.WithDelivery delivery = cmcc.getSpec().getWith().getDelivery();
        if (getInt(delivery.getRls()) > 0 && componentCollection.getOfTypeAndKind(CONTENT_SERVER, KIND_RLS).isEmpty()) {
            var rls = ComponentSpecBuilder.ofType(CONTENT_SERVER)
                    .withKind(KIND_RLS)
                    .withMilestone(Milestone.ManagementReady).build();
            // enforce jdbc secret creation
            componentCollection.add(rls).requestRequiredResources();
        } else if (getInt(delivery.getRls()) == 0) {
            // make sure we don't have an RLS component even if the custom resource has it defined
            componentCollection.removeOfTypeAndKind(CONTENT_SERVER, KIND_RLS);
        }

        if (cmcc.getSpec().getWith().getDatabases()) {
            if (cmcc.getSpec().getWith().databaseCreateForKind("mongodb")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mongodb")
                                .withMilestone(Milestone.DeploymentStarted)
                                .withExtra(MongoDBComponent.createUsersFromClientSecrets(this))
                                .build()
                ));
            }
            if (cmcc.getSpec().getWith().databaseCreateForKind("mysql")) {
                componentCollection.addAll(List.of(
                        ComponentSpecBuilder.ofType("mysql")
                                .withMilestone(Milestone.DeploymentStarted)
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
    public void onMilestoneReached(Milestone previousMilestone) {
        super.onMilestoneReached(previousMilestone);

        if (cmcc.getSpec().getWith().getRestartContentServer() &&
                cmcc.getStatus().getMilestone() == Milestone.ContentServerReady) {
            log.info("[{}] Restarting CMS and MLS", getContextForLogging());
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "cms"));
            restartStatefulSet(componentCollection.getServiceNameFor("content-server", "mls"));
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        getComponentCollection()
                .getAllImplementing(MilestoneListener.class)
                .stream()
                .forEach(ml -> {
                    try {
                        ml.onMilestoneReached(cmcc.getStatus().getMilestone(), previousMilestone);
                    } catch (Exception e) {
                        log.warn("[{}] onMilestoneReached error: {}", getContextForLogging(), e.getMessage());
                    }
                });
    }
}
