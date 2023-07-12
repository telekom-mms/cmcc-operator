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

import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.ingress.UrlMappingBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;
import java.util.Map;

/**
 * Create a DefaultTargetState based on a CustomResource custom resource.
 */
public class DefaultTargetStateFactory implements TargetStateFactory {
    private final BeanFactory beanFactory;
    private final KubernetesClient kubernetesClient;
    private final ResourceNamingProviderFactory resourceNamingProviderFactory;
    private final ResourceReconcilerManager resourceReconcilerManager;
    private final Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories;
    private final YamlMapper yamlMapper;

    public DefaultTargetStateFactory(BeanFactory beanFactory,
                                     KubernetesClient kubernetesClient,
                                     ResourceNamingProviderFactory resourceNamingProviderFactory,
                                     ResourceReconcilerManager resourceReconcilerManager,
                                     Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories,
                                     YamlMapper yamlMapper) {
        this.beanFactory = beanFactory;
        this.kubernetesClient = kubernetesClient;
        this.resourceNamingProviderFactory = resourceNamingProviderFactory;
        this.resourceReconcilerManager = resourceReconcilerManager;
        this.urlMappingBuilderFactories = urlMappingBuilderFactories;
        this.yamlMapper = yamlMapper;
    }

    @Override
    public TargetState buildTargetState(CustomResource cmcc) {
        return new DefaultTargetState(beanFactory,
                kubernetesClient,
                resourceNamingProviderFactory,
                resourceReconcilerManager,
                urlMappingBuilderFactories,
                yamlMapper,
                cmcc);
    }
}
