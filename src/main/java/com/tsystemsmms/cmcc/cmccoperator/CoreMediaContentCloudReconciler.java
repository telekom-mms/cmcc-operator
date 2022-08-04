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
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CrdCustomResource;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetStateFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ControllerConfiguration
@Slf4j
public class CoreMediaContentCloudReconciler implements Reconciler<CoreMediaContentCloud>, ErrorStatusHandler<CoreMediaContentCloud>, EventSourceInitializer<CoreMediaContentCloud> {

    public static final Map<String, String> OPERATOR_SELECTOR_LABELS = Map.of("cmcc.tsystemsmms.com/operator", "cmcc");

    private final KubernetesClient kubernetesClient;
    private final TargetStateFactory targetStateFactory;

    public CoreMediaContentCloudReconciler(KubernetesClient kubernetesClient, TargetStateFactory targetStateFactory) {
        this.kubernetesClient = kubernetesClient;
        this.targetStateFactory = targetStateFactory;
        log.info("Using custom resource {} for configuration", CoreMediaContentCloud.class.getSimpleName());
    }

    @Override
    public UpdateControl<CoreMediaContentCloud> reconcile(CoreMediaContentCloud cmcc, Context context) {
        CustomResource deepCopy = new CrdCustomResource(Utils.deepClone(cmcc, CoreMediaContentCloud.class));
        CoreMediaContentCloudStatus status = deepCopy.getStatus();

        TargetState targetState = targetStateFactory.buildTargetState(deepCopy);
        targetState.reconcile();

        status.setError("");
        status.setErrorMessage("");
        if (!deepCopy.getStatus().getJob().isBlank()) {
            cmcc.getSpec().setJob("");
            cmcc.setStatus(status);
            return UpdateControl.updateResourceAndStatus(cmcc);
        } else {
            cmcc.setStatus(status);
            return UpdateControl.updateStatus(cmcc);
        }
    }

    @Override
    public DeleteControl cleanup(CoreMediaContentCloud cmcc, Context context) {
        return DeleteControl.defaultDelete();
    }

    @Override
    public Optional<CoreMediaContentCloud> updateErrorStatus(CoreMediaContentCloud resource, RetryInfo retryInfo,
                                                             RuntimeException e) {
        CoreMediaContentCloudStatus status = resource.getStatus();
        status.setErrorMessage(e.getMessage());
        status.setError("error");
        return Optional.of(resource);
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<CoreMediaContentCloud> context) {
        return EventSourceInitializer.nameEventSources(new InformerEventSource<>(kubernetesClient.batch().v1().jobs().inAnyNamespace().withLabels(MgmtToolsJobComponent.getJobLabels()).runnableInformer(1200), Mappers.fromOwnerReference()),
                new InformerEventSource<>(kubernetesClient.apps().statefulSets().inAnyNamespace().withLabels(OPERATOR_SELECTOR_LABELS).runnableInformer(1200), Mappers.fromOwnerReference()));
    }

    public static class LabelMapper implements SecondaryToPrimaryMapper<Job> {

        @Override
        public Set<ResourceID> toPrimaryResourceIDs(Job dependentResource) {
            return null;
        }
    }
}
