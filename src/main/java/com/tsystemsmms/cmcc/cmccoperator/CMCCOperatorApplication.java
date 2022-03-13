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

import com.tsystemsmms.cmcc.cmccoperator.ingress.BlueprintCmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import com.tsystemsmms.cmcc.cmccoperator.ingress.IngressBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.ingress.NginxIngressBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.DefaultTargetStateFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetStateFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CMCCOperatorApplication {

    @Bean
    public CoreMediaContentCloudReconciler coreMediaContentCloudReconciler(
            KubernetesClient kubernetesClient,
            ResourceReconcilerManager resourceReconcilerManager,
            TargetStateFactory targetStateFactory) {
        return new CoreMediaContentCloudReconciler(
                kubernetesClient,
                resourceReconcilerManager,
                targetStateFactory);
    }

    @Bean
    public ResourceReconcilerManager resourceReconciler(KubernetesClient kubernetesClient) {
        return new ResourceReconcilerManager(kubernetesClient);
    }

    @Bean
    public CmccIngressGeneratorFactory caeIngressGeneratorFactory(IngressBuilderFactory ingressBuilderFactory) {
        return new BlueprintCmccIngressGeneratorFactory(ingressBuilderFactory);
    }

    @Bean
    public IngressBuilderFactory ingressBuilderFactory() {
        return new NginxIngressBuilderFactory();
    }

    @Bean
    public TargetStateFactory config(BeanFactory beanFactory, KubernetesClient kubernetesClient, CmccIngressGeneratorFactory cmccIngressGeneratorFactory) {
        return new DefaultTargetStateFactory(beanFactory, kubernetesClient, cmccIngressGeneratorFactory);
    }

    public static void main(String[] args) {
        SpringApplication.run(CMCCOperatorApplication.class, args);
    }

}
