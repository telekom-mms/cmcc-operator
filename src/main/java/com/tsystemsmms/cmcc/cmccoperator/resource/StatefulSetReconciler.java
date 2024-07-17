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
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import lombok.extern.slf4j.Slf4j;

@ResourceReconciler(StatefulSet.class)
@Slf4j
public class StatefulSetReconciler implements Reconciler {
  @Override
  public void reconcile(KubernetesClient kubernetesClient, String namespace, HasMetadata resource) {
    RollableScalableResource<StatefulSet> existing = kubernetesClient.apps().statefulSets().inNamespace(namespace).withName(resource.getMetadata().getName());
    if (existing.get() == null) {
      log.debug("starting {}/{}", resource.getKind(), resource.getMetadata().getName());
      kubernetesClient.resource(resource).inNamespace(namespace).createOrReplace();
    } else {
      StatefulSet sts = (StatefulSet) resource;
      StatefulSetSpec spec = sts.getSpec();
      existing.edit(r ->
              new StatefulSetBuilder(r).editOrNewSpec()
                      .withMinReadySeconds(spec.getMinReadySeconds())
//                      .withPersistentVolumeClaimRetentionPolicy(spec.getPersistentVolumeClaimRetentionPolicy()) // do not use because of feature gate?
                      .withVolumeClaimTemplates(spec.getVolumeClaimTemplates())
                      .withReplicas(spec.getReplicas())
                      .withTemplate(spec.getTemplate())
                      .withUpdateStrategy(spec.getUpdateStrategy())
                      .endSpec()
                      .build());
      // TODO: handle changes to other fields, like spec.getVolumeClaimTemplates()
    }
  }
}
