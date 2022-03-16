/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.job;

import com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler;
import com.tsystemsmms.cmcc.cmccoperator.components.SpringBootComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class JobComponent extends SpringBootComponent {
    long activeDeadlineSeconds = 120L;

    public JobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
        super(kubernetesClient, targetState, componentSpec, imageRepository);
    }

    @Override
    public boolean isBuildResources() {
        return getCmcc().getStatus().getMilestone().compareTo(getComponentSpec().getMilestone()) == 0;
    }

    @Override
    public List<HasMetadata> buildResources() {
        return List.of(buildJob());
    }

    @Override
    public Probe getLivenessProbe() {
        return null;
    }

    @Override
    public Probe getReadinessProbe() {
        return null;
    }

    @Override
    public Probe getStartupProbe() {
        return null;
    }

    Job buildJob() {
        return new JobBuilder()
                .withMetadata(getResourceMetadata())
                .withSpec(new JobSpecBuilder()
                        .withActiveDeadlineSeconds(activeDeadlineSeconds)
                        .withBackoffLimit(3)
                        .withCompletions(1)
                        .withParallelism(1)
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(getSelectorLabels())
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withRestartPolicy("Never")
                                        .withContainers(buildContainers())
                                        .withInitContainers(getInitContainers())
                                        .withVolumes(getVolumes())
                                        .build())
                                .build())
                        .build())
                .build();
    }

    @Override
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = super.getSelectorLabels();
        labels.putAll(getJobLabels());
        return labels;
    }

    public static Map<String, String> getJobLabels() {
        return Map.of("cmcc.tsystemsmms.com/job", JobComponent.class.getSimpleName().replaceAll("Component$", ""));
    }

}
