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
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import lombok.extern.slf4j.Slf4j;

@ResourceReconciler(Job.class)
@Slf4j
public class JobReconciler implements Reconciler {
    @Override
    public void reconcile(KubernetesClient kubernetesClient, String namespace, HasMetadata resource) {
        Resource<Job> existing = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(resource.getMetadata().getName());
        if (existing.get() != null) {
//            log.debug("skipping {}/{}", resource.getKind(), resource.getMetadata().getName());
        } else {
//            log.debug("reconciling {}/{}", resource.getKind(), resource.getMetadata().getName());
            kubernetesClient.resource(resource).inNamespace(namespace).createOrReplace();
        }
    }
}
