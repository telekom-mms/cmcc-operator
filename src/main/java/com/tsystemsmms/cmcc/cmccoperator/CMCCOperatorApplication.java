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

import com.tsystemsmms.cmcc.cmccoperator.ingress.*;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.*;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.NamespaceFilter;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.springboot.starter.OperatorConfigurationProperties;
import io.javaoperatorsdk.operator.springboot.starter.ReconcilerProperties;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootApplication
public class CMCCOperatorApplication {

  @Bean
  @ConditionalOnProperty(value = "cmcc.useCrd", havingValue = "true", matchIfMissing = true)
  public CoreMediaContentCloudReconciler coreMediaContentCloudReconciler(
          @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") KubernetesClient kubernetesClient,
          TargetStateFactory targetStateFactory,
          NamespaceFilter<HasMetadata> namespaceFilter,
          OperatorConfigurationProperties configuration) {

    if (!NamespaceFilter.getNamespaceIncludes().isEmpty()) {
      var props = new ReconcilerProperties();
      props.setNamespaces(NamespaceFilter.getNamespaceIncludes());
      configuration.setReconcilers(Map.of(CoreMediaContentCloudReconciler.class.getSimpleName(), props));
    }

    return new CoreMediaContentCloudReconciler(
            kubernetesClient,
            targetStateFactory,
            namespaceFilter);
  }

  @Bean
  @ConditionalOnProperty(value = "cmcc.useConfigMap", havingValue = "true")
  public CmccConfigMapReconciler cmccConfigMapReconciler(
          KubernetesClient kubernetesClient,
          TargetStateFactory targetStateFactory,
          YamlMapper yamlMapper,
          NamespaceFilter<HasMetadata> namespaceFilter) {
    return new CmccConfigMapReconciler(
            kubernetesClient,
            targetStateFactory,
            yamlMapper,
            namespaceFilter);
  }

  @Bean
  public ResourceNamingProviderFactory resourceNamingProviderFactory() {
    return new DefaultResourceNamingProviderFactory();
  }

  @Bean
  public ResourceReconcilerManager resourceReconciler(@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") KubernetesClient kubernetesClient) {
    return new ResourceReconcilerManager(kubernetesClient);
  }

  @Bean
  public UrlMappingBuilderFactory blueprintIngressGeneratorFactory(IngressBuilderFactory ingressBuilderFactory) {
    return new BlueprintUrlMappingBuilderFactory(ingressBuilderFactory);
  }

  @Bean
  public UrlMappingBuilderFactory headlessIngressGeneratorFactory(IngressBuilderFactory ingressBuilderFactory) {
    return new HeadlessUrlMappingBuilderFactory(ingressBuilderFactory);
  }

  @Bean
  public UrlMappingBuilderFactory onlylangIngressGeneratorFactory(IngressBuilderFactory ingressBuilderFactory) {
    return new OnlyLangUrlMappingBuilderFactory(ingressBuilderFactory);
  }

  @Bean
  public IngressBuilderFactory ingressBuilderFactory() {
    return new NginxIngressBuilderFactory();
  }

  @Bean
  public TargetStateFactory targetStateFactory(BeanFactory beanFactory,
                                               @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") KubernetesClient kubernetesClient,
                                               ResourceNamingProviderFactory resourceNamingProviderFactory,
                                               ResourceReconcilerManager resourceReconcilerManager,
                                               List<UrlMappingBuilderFactory> urlMappingBuilderFactories,
                                               YamlMapper yamlMapper) {

    return new DefaultTargetStateFactory(beanFactory,
            kubernetesClient,
            resourceNamingProviderFactory,
            resourceReconcilerManager,
            urlMappingBuilderFactories.stream().collect(Collectors.toMap(UrlMappingBuilderFactory::getName, Function.identity())),
            yamlMapper);
  }

  @Bean
  public YamlMapper yamlMapper() {
    return new YamlMapper();
  }

  public static void main(String[] args) {
    SpringApplication.run(CMCCOperatorApplication.class, args);
  }

}
