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

import com.tsystemsmms.cmcc.cmccoperator.components.job.MgmtToolsJobComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.customresource.ConfigMapCustomResource;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetStateFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler.OPERATOR_SELECTOR_LABELS;

@ControllerConfiguration(labelSelector = CmccConfigMapReconciler.SELECTOR_LABEL)
@Slf4j
public class CmccConfigMapReconciler implements Reconciler<ConfigMap>, ErrorStatusHandler<ConfigMap>, EventSourceInitializer<ConfigMap> {
    public static final String SELECTOR_LABEL = "cmcc.tsystemsmms.com.customresource=cmcc";

    private final KubernetesClient kubernetesClient;
    private final TargetStateFactory targetStateFactory;
    private final YamlMapper yamlMapper;

    public CmccConfigMapReconciler(KubernetesClient kubernetesClient, TargetStateFactory targetStateFactory, YamlMapper yamlMapper) {
        this.kubernetesClient = kubernetesClient;
        this.targetStateFactory = targetStateFactory;
        this.yamlMapper = yamlMapper;
        log.info("Using ConfigMap with label {} for configuration", SELECTOR_LABEL);
    }

    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap cm, Context context) {
        ConfigMapCustomResource cmcc = new ConfigMapCustomResource(cm, yamlMapper);
        CoreMediaContentCloudStatus status = cmcc.getStatus();

        TargetState targetState = targetStateFactory.buildTargetState(cmcc);
        targetState.reconcile();

        status.setError("");
        status.setErrorMessage("");
        if (!cmcc.getStatus().getJob().isBlank()) {
            cmcc.getSpec().setJob("");
        }
        cmcc.setStatus(status);
        cmcc.updateResource();
        return UpdateControl.updateResource(cm);
    }

    @Override
    public ErrorStatusUpdateControl<ConfigMap> updateErrorStatus(ConfigMap cm, Context<ConfigMap> context, Exception e) {
        ConfigMapCustomResource cmcc = new ConfigMapCustomResource(cm, yamlMapper);
        CoreMediaContentCloudStatus status = cmcc.getStatus();

        status.setErrorMessage(e.getMessage());
        status.setError("error");
        cmcc.setStatus(status);
        cmcc.updateResource();
        return ErrorStatusUpdateControl.updateStatus(cm);
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<ConfigMap> context) {
        return EventSourceInitializer.nameEventSources(
                new InformerEventSource<>(InformerConfiguration.from(Job.class, context)
                        .withLabelSelector(Utils.selectorFromLabels(MgmtToolsJobComponent.getJobLabels()))
                        .withSecondaryToPrimaryMapper(Mappers.fromOwnerReference()).build(), kubernetesClient),
                new InformerEventSource<>(InformerConfiguration.from(StatefulSet.class, context)
                        .withLabelSelector(Utils.selectorFromLabels(OPERATOR_SELECTOR_LABELS))
                        .withSecondaryToPrimaryMapper(Mappers.fromOwnerReference()).build(), kubernetesClient)
        );
    }
}
