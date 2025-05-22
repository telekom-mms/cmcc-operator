/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.SolrComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.*;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public class ContentFeederComponent extends CorbaComponent implements HasMongoDBClient, HasSolrClient {
    public static final String CONTENT_FEEDER = "content-feeder";

    public ContentFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, CONTENT_FEEDER);
        setDefaultSchemas(Map.of(
                MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName("studio", SOLR_CLIENT_SERVER_LEADER),
                UAPI_CLIENT_SECRET_REF_KIND, "feeder"
        ));
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getSolrClientSecretRef();
    }

    @Override
    protected PodAffinity getPodAffinity() {
        var affinityRules = new LinkedList<WeightedPodAffinityTerm>();

        affinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_CMS, 25));
        affinityRules.add(createAffinityToComponent(SolrComponent.SOLR, SolrComponent.KIND_LEADER, 25));

        return new PodAffinityBuilder()
                .withPreferredDuringSchedulingIgnoredDuringExecution(affinityRules.stream().filter(Objects::nonNull).toList())
                .build();
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildStatefulSet());
        if (Boolean.TRUE.equals(getCmcc().getSpec().getWith().getJsonLogging())) {
            resources.add(buildLoggingConfigMap());
        }
        resetFeederIfNeeded();
        return resources;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars("content"));

        return env;
    }

    protected void resetFeederIfNeeded() {
        String name = getTargetState().getResourceNameFor(this);
        String flagName = concatOptional("generation", name);
        String specGeneration = Optional.ofNullable(getComponentSpec().getExtra().get("generation")).orElse("");
        if (!getTargetState().getFlag(flagName, "").equals(specGeneration)) {
            if (!getState().isReady().orElse(false)) {
                return;
            }
            try {
                resetFeeder();
            } catch (Exception e) {
                log.warn("[{}] Error resetting feeder: {}", getTargetState().getContextForLogging(), name, e);
            }
            if (!getTargetState().isUpgrading()) {
                getTargetState().restartStatefulSet(name);
            }
            getTargetState().setFlag(flagName, specGeneration);
        }

    }

    /**
     * Reset the Content Feeder.
     */
    public void resetFeeder() {
        var pod = getTargetState().getKubernetesClient().pods()
                .inNamespace(getNamespace())
                .withLabels(getSelectorLabels())
                .resources()
                .findFirst().get();

        String stopUrl = UriComponentsBuilder.fromUriString("http://feeder:feeder@localhost/admin")
                .port(getContainerPorts().stream().filter(x -> x.getName().equals("ior")).findFirst().get().getContainerPort())
                .queryParam("action", "stop")
                .toUriString();

        log.debug("[{}] Stopping Content feeder pod {}, using {}", getTargetState().getContextForLogging(), pod.get().getMetadata().getName(), stopUrl);
        var result = this.executeWebRequest(pod, stopUrl);
        if (result.exitCode != 0 || result.output == null || !result.output.contains("stop command was sent to the feeder")) {
            throw new CustomResourceConfigError("Unable to stop content feeder on pod \"\": " + result.output);
        }

        String resetUrl = UriComponentsBuilder.fromUriString("http://feeder:feeder@localhost/admin")
                .port(getContainerPorts().stream().filter(x -> x.getName().equals("ior")).findFirst().get().getContainerPort())
                .queryParam("action", "clearCollection")
                .toUriString();

        log.debug("[{}] Resetting Content feeder pod {}, using {}", getTargetState().getContextForLogging(), pod.get().getMetadata().getName(), resetUrl);
        result = this.executeWebRequest(pod, resetUrl);
        if (result.exitCode != 0 || result.output == null || !result.output.contains("index was cleared")) {
            throw new CustomResourceConfigError("Unable to stop content feeder on pod \"\": " + result.output);
        }
    }
}
