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

import com.tsystemsmms.cmcc.cmccoperator.components.job.JobComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CrdCustomResource;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetStateFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.NamespaceFilter;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@ControllerConfiguration(name = "CoreMediaContentCloudReconciler",
  generationAwareEventProcessing = false, // GenerationAwareness in update filter below
  informer = @Informer(
          genericFilter = NamespaceFilter.class, // genericFilter needed for namespace excludes, includes are already handled in CMCCOperatorApplication
          onUpdateFilter = CoreMediaContentCloudReconciler.OnUpdateGenerationAndStatusAwareFilter.class) // only events with spec changes or status changes
)
@Slf4j
public class CoreMediaContentCloudReconciler implements Reconciler<CoreMediaContentCloud> {
  public static final Map<String, String> OPERATOR_SELECTOR_LABELS = Map.of("cmcc.tsystemsmms.com/operator", "cmcc");

  private final KubernetesClient kubernetesClient;
  private final TargetStateFactory targetStateFactory;
  private final NamespaceFilter<HasMetadata> namespaceFilter;

  public CoreMediaContentCloudReconciler(KubernetesClient kubernetesClient, TargetStateFactory targetStateFactory, NamespaceFilter<HasMetadata> namespaceFilter) {
    this.kubernetesClient = kubernetesClient;
    this.targetStateFactory = targetStateFactory;
    this.namespaceFilter = namespaceFilter;
    var namespaceLogMsg = NamespaceFilter.getLogMessage();
    log.info("Using custom resource {} for configuration{}{}", CoreMediaContentCloud.class.getSimpleName(),
            namespaceLogMsg.isEmpty() ? "" : ", ", namespaceLogMsg);
  }

  @Override
  public UpdateControl<CoreMediaContentCloud> reconcile(CoreMediaContentCloud cmcc, Context context) {

    if (context.isNextReconciliationImminent()) {
      // there is already another event, skip here and go for the next one!
      return UpdateControl.noUpdate();
    }

    var latestCmccFromKube = kubernetesClient.resource(cmcc).get();
    if (latestCmccFromKube != null && !Utils.deepEquals(latestCmccFromKube.getStatus(), cmcc.getStatus())) {
      // compare with current state from cluster: When already changed again, we are far too late with this event: skip it
      // this can happen when the OP needs a long time to reconcile. In the meantime events pile up that get more and more outdated.
      // Finally, when they are handled then they represent an old state that would harm as i.e. the milestone would be flapping backwards
      // There seems to be no other way to recognize this than comparing with current kube state
      log.trace("Skipping outdated event as status has already changed again:\nLatest: {}\nFrom Event: {}",
              latestCmccFromKube.getStatus(),
              cmcc.getStatus());
      return UpdateControl.noUpdate();
    }

    CustomResource deepCopy = new CrdCustomResource(Utils.deepClone(cmcc, CoreMediaContentCloud.class));
    CoreMediaContentCloudStatus status = deepCopy.getStatus();

    TargetState targetState = targetStateFactory.buildTargetState(deepCopy);
    targetState.reconcile();

    status.setError("");
    status.setErrorMessage("");

    var statusChanged = false;
    var specChanged = false;

    if (!status.getJob().isBlank()) {
      if (!cmcc.getSpec().getJob().isBlank()) {
        cmcc.getSpec().setJob("");
        specChanged = true;
      }
      if (status.getMilestone().equals(Milestone.Ready)) {
        status.setJob(""); // job finished
      }
    }
    if (!Objects.equals(deepCopy.getSpec().getScaling().getIntVal(), cmcc.getSpec().getScaling().getIntVal())) {
      cmcc.getSpec().setScaling(deepCopy.getSpec().getScaling());
      specChanged = true;
    }
    if (!Utils.deepEquals(status, cmcc.getStatus())) {
      statusChanged = true;
    }

    if (specChanged || statusChanged) {
      cmcc.setStatus(status);

      // avoid conflicts on update
      cmcc.getMetadata().setManagedFields(Collections.emptyList());
      cmcc.getMetadata().setResourceVersion(null);
      cmcc.getMetadata().setGeneration(null);

      var result = UpdateControl.patchResourceAndStatus(cmcc); // assume both have changed
      if (!statusChanged) result = UpdateControl.patchResource(cmcc); // status did not? spec only
      if (!specChanged) result = UpdateControl.patchStatus(cmcc); // spec did not? status only

      return result;
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public ErrorStatusUpdateControl<CoreMediaContentCloud> updateErrorStatus(CoreMediaContentCloud resource, Context<CoreMediaContentCloud> context, Exception e) {
    CoreMediaContentCloudStatus status = resource.getStatus();
    status.setErrorMessage(e.getMessage());
    status.setError("error");
    resource.setStatus(status);
    return ErrorStatusUpdateControl.patchStatus(resource);
  }

  @Override
  public List<EventSource<?, CoreMediaContentCloud>> prepareEventSources(EventSourceContext<CoreMediaContentCloud> context) {
    return List.of(
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(Job.class, CoreMediaContentCloud.class)
              .withGenericFilter(namespaceFilter)
              .withLabelSelector(Utils.selectorFromLabels(JobComponent.getJobLabels()))
              .withSecondaryToPrimaryMapper(Mappers.fromOwnerReferences(CoreMediaContentCloud.class))
              .build(),
            context),
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(StatefulSet.class, CoreMediaContentCloud.class)
              .withGenericFilter(namespaceFilter)
              .withLabelSelector(Utils.selectorFromLabels(OPERATOR_SELECTOR_LABELS))
              .withSecondaryToPrimaryMapper(Mappers.fromOwnerReferences(CoreMediaContentCloud.class))
              .build(),
            context)
    );
  }

  static class OnUpdateGenerationAndStatusAwareFilter implements OnUpdateFilter<HasMetadata> {
  // inspired by operator-framework-core-5.0.4!/io/javaoperatorsdk/operator/processing/event/source/controller/InternalEventFilters.java
    @Override
    public boolean accept(HasMetadata newResource, HasMetadata oldResource) {
      // for example pods don't have generation
      if (oldResource.getMetadata().getGeneration() == null) {
        return true;
      }

      if (oldResource.getMetadata().getGeneration() < newResource.getMetadata().getGeneration()) {
        return true;
      }

      if (oldResource instanceof CoreMediaContentCloud cmcc && newResource instanceof CoreMediaContentCloud newCmcc) {
        return !Utils.deepEquals(cmcc.getStatus(), newCmcc.getStatus());
      }

      return false;
    }
  }
}
