/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import lombok.extern.slf4j.Slf4j;

@ResourceReconciler(HasMetadata.class)
@Slf4j
public class HasMetadataReconciler implements Reconciler {
    @Override
    public void reconcile(KubernetesClient kubernetesClient, String namespace, HasMetadata resource) {
//        log.debug("reconciling {}/{}", resource.getKind(), resource.getMetadata().getName());
        // https://github.com/fabric8io/kubernetes-client/blob/main/doc/FAQ.md#alternatives-to-createorreplace-and-replace
        kubernetesClient.resource(resource).inNamespace(namespace).unlock().createOr(NonDeletingOperation::update);
    }
}
