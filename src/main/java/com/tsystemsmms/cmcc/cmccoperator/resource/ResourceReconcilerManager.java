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
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

@Slf4j
public class ResourceReconcilerManager {
    final KubernetesClient kubernetesClient;

    final HashMap<Class<? extends HasMetadata>, Reconciler> reconcilers;

    public ResourceReconcilerManager(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        reconcilers = new HashMap<>();
        initReconcilers();
    }

    /**
     * Depending on the state of the cluster and the resource kind, create, patch or update the resource.
     *
     * @param resources A resources of Kubernetes resource
     */
    public void createPatchUpdate(String namespace, KubernetesList resources) {
        for (HasMetadata resource : resources.getItems()) {
            Reconciler reconciler = findReconcilerForResource(resource);
            if (reconciler == null)
                reconciler = reconcilers.get(HasMetadata.class);
            reconciler.reconcile(kubernetesClient, namespace, resource);
        }
    }

    @SuppressWarnings("unchecked")
    private Reconciler findReconcilerForResource(HasMetadata resource) {
        Class<? extends HasMetadata> clazz = resource.getClass();
        Reconciler reconciler = reconcilers.get(clazz);

        while (reconciler == null) {
            if (HasMetadata.class.isAssignableFrom(clazz.getSuperclass())) {
                clazz = (Class<? extends HasMetadata>) clazz.getSuperclass();
            } else {
                return null;
            }
            reconciler = reconcilers.get(clazz);
        }
        return reconciler;
    }

    private void initReconcilers() {
        ClassPathScanningCandidateComponentProvider provider
                = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(ResourceReconciler.class));
        provider.addIncludeFilter(new AssignableTypeFilter(Reconciler.class));
        for (BeanDefinition beanDef : provider.findCandidateComponents(this.getClass().getPackageName())) {
            try {
                Reconciler reconciler = (Reconciler) Class.forName(beanDef.getBeanClassName()).getDeclaredConstructor().newInstance();
                ResourceReconciler annotation = reconciler.getClass().getAnnotation(ResourceReconciler.class);
                if (annotation != null) {
                    Class<? extends HasMetadata> hasMetadata = annotation.value();
                reconcilers.put(hasMetadata, reconciler);
            } else {
                    log.warn("bean {} is missing the ResourceReconciler annotation, ignoring", beanDef.getResourceDescription());
                }
            } catch (NoSuchMethodException | InvocationTargetException | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                log.error("Unable to register {}", beanDef.getBeanClassName(), e);
            }
        }
    }
}
