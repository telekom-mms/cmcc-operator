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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.tsystemsmms.cmcc.cmccoperator.components.job.MgmtToolsJobComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetStateFactory;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@ControllerConfiguration
@Slf4j
public class CoreMediaContentCloudReconciler implements Reconciler<CoreMediaContentCloud>, ErrorStatusHandler<CoreMediaContentCloud>, EventSourceInitializer<CoreMediaContentCloud> {

    public static final Map<String, String> OPERATOR_SELECTOR_LABELS = Map.of("cmcc.tsystemsmms.com/operator", "cmcc");

    private final KubernetesClient kubernetesClient;
    private final ResourceReconcilerManager resourceReconcilerManager;
    private final TargetStateFactory targetStateFactory;

    public CoreMediaContentCloudReconciler(KubernetesClient kubernetesClient, ResourceReconcilerManager resourceReconcilerManager, TargetStateFactory targetStateFactory) {
        this.kubernetesClient = kubernetesClient;
        this.resourceReconcilerManager = resourceReconcilerManager;
        this.targetStateFactory = targetStateFactory;
    }

    @Override
    public UpdateControl<CoreMediaContentCloud> reconcile(CoreMediaContentCloud cmcc, Context context) {
        cmcc = Utils.deepClone(cmcc, CoreMediaContentCloud.class);
        CoreMediaContentCloudStatus status = cmcc.getStatus();

        // create new desired state and its resources
        TargetState targetState = targetStateFactory.buildTargetState(cmcc);
        List<HasMetadata> newResources = targetState.buildResources();

        // compute resources no longer desired
        Set<ResourceRef> ownedResourceRefs = newResources.stream().map(ResourceRef::new).collect(Collectors.toSet());
        Set<ResourceRef> abandonedResourceRefs = ResourceRef.fromJson(status.getOwnedResourceRefs());
        abandonedResourceRefs.removeAll(ownedResourceRefs);

        log.info("Updating dependent resource of cmcc {}: {} new/updated, {} abandoned resources, milestone {}",
                cmcc.getMetadata().getName(), newResources.size(), abandonedResourceRefs.size(), status.getMilestone());

        // apply new and updated resources
        KubernetesList list = new KubernetesListBuilder().withItems(newResources).build();
        resourceReconcilerManager.createPatchUpdate(cmcc.getMetadata().getNamespace(), list);
        // remove resources no longer in desired state
        deleteResources(cmcc.getMetadata().getNamespace(), abandonedResourceRefs);

        // save state
        status.setOwnedResourceRefs(ResourceRef.toJson(ownedResourceRefs));
        status.setError("");
        status.setErrorMessage("");
        cmcc.setStatus(status);

        return UpdateControl.updateStatus(cmcc);
    }

    @Override
    public DeleteControl cleanup(CoreMediaContentCloud cmcc, Context context) {
        CoreMediaContentCloudStatus status = new CoreMediaContentCloudStatus();
        Set<ResourceRef> abandonedResourceRefs = ResourceRef.fromJson(cmcc.getStatus().getOwnedResourceRefs());

        log.info("Deleting dependent resource of cmcc \"{}\": {} abandoned resources", cmcc.getMetadata().getName(), abandonedResourceRefs.size());

        deleteResources(cmcc.getMetadata().getNamespace(), abandonedResourceRefs);
        status.setOwnedResourceRefs("[]");
        cmcc.setStatus(status);

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

    private void deleteResources(String namespace, Collection<ResourceRef> resources) {
        if (resources == null)
            return;
        for (ResourceRef resource : resources) {
            MixedOperation<GenericKubernetesResource, GenericKubernetesResourceList, Resource<GenericKubernetesResource>> op = kubernetesClient.genericKubernetesResources(resource.getApiVersion(), resource.getKind());
            Resource<GenericKubernetesResource> r = op.inNamespace(namespace).withName(resource.getName());
//            log.debug("Deleting {}/{}", resource.getKind(), resource.getName());
            r.withPropagationPolicy(DeletionPropagation.BACKGROUND).delete();
        }
    }


    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<CoreMediaContentCloud> context) {
        return List.of(new InformerEventSource<>(kubernetesClient.batch().v1().jobs().inAnyNamespace().withLabels(MgmtToolsJobComponent.getJobLabels()).runnableInformer(1200), Mappers.fromOwnerReference()),
                new InformerEventSource<>(kubernetesClient.apps().statefulSets().inAnyNamespace().withLabels(OPERATOR_SELECTOR_LABELS).runnableInformer(1200), Mappers.fromOwnerReference()));
    }

    @Data
    public static class ResourceRef {
        // make sure the serialization is reproducible, so resources don't get recreated unnecessarily
        static ObjectMapper objectMapper;

        static {
            objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        }

        String apiVersion;
        String kind;
        String name;

        public ResourceRef() {}

        public ResourceRef(HasMetadata resource) {
            this.apiVersion = resource.getApiVersion();
            this.kind = resource.getKind();
            this.name = resource.getMetadata().getName();
        }

        @SneakyThrows
        static Set<ResourceRef> fromJson(String json) {
            return objectMapper.readValue(json, new TypeReference<Set<ResourceRef>>() {});
        }

        @SneakyThrows
        static String toJson(Set<ResourceRef> refs) {
            return objectMapper.writeValueAsString(refs);
        }
    }
}
