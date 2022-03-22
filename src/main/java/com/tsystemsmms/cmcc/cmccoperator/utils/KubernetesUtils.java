/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.*;

/**
 * Helpers for working with Kubernetes.
 */
public class KubernetesUtils {
    // see https://github.com/fabric8io/kubernetes-client/issues/887#issuecomment-940988916
    public static final List<Class<? extends HasMetadata>> ALL_KUBERNETES_RESOURCE_TYPES = Arrays.asList(
            ConfigMap.class,
            Ingress.class,
            Job.class,
            PersistentVolumeClaim.class,
            Secret.class,
            Service.class,
            StatefulSet.class);

    public static List<HasMetadata> getAllResourcesMatchingLabels(KubernetesClient kubernetesClient, String namespace, Map<String, String> labels) {
        List<HasMetadata> results = new LinkedList<>();
        for (Class<? extends HasMetadata> c : ALL_KUBERNETES_RESOURCE_TYPES) {
            results.addAll(kubernetesClient.resources(c).inNamespace(namespace).withLabels(labels).list().getItems());
        }
        return results;
    }

    public static boolean isMetadataEqual(HasMetadata a, HasMetadata b) {
        ObjectMeta am = a.getMetadata();
        ObjectMeta bm = a.getMetadata();
        return am.getNamespace().equals(bm.getNamespace())
                && am.getName().equals(bm.getName())
                && a.getKind().equals(b.getKind())
                && a.getApiVersion().equals(b.getApiVersion());
    }

    public static boolean isMetadataUnequal(HasMetadata a, HasMetadata b) {
        return isMetadataEqual(a, b);
    }

    public static boolean isMetadataContains(Collection<HasMetadata> c, HasMetadata b) {
        return c.stream().anyMatch(a -> isMetadataEqual(a, b));
    }
}
