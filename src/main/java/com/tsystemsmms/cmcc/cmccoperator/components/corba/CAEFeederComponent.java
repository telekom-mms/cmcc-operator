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

import com.tsystemsmms.cmcc.cmccoperator.components.HasJdbcClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient;
import com.tsystemsmms.cmcc.cmccoperator.components.HasSolrClient;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.SolrComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodAffinity;
import io.fabric8.kubernetes.api.model.PodAffinityBuilder;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.*;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class CAEFeederComponent extends CorbaComponent implements HasJdbcClient, HasMongoDBClient, HasSolrClient {

    public static final String CAE_FEEDER = "cae-feeder";

    public static final String KIND_LIVE = "live";
    public static final String KIND_PREVIEW = "preview";
    public static final String EXTRA_DATABASE_SCHEMA = "databaseSchema";

    @Value("${cmcc.caefeeder.resetCmd:SPRING_PROPERTIES=\"\" java -cp \"libs/*\" com.coremedia.amaro.cae.feeder.reset.ResetCaeFeeder reset}")
    private String resetFeederCommand;

    public CAEFeederComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, CAE_FEEDER);

        if (getComponentSpec().getKind() == null) {
            throw new CustomResourceConfigError("kind must be set to either " + KIND_LIVE + " or " + KIND_PREVIEW);
        }
        switch (componentSpec.getKind()) {
            case KIND_LIVE:
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, "mcaefeeder",
                        MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                        SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName(KIND_LIVE, SOLR_CLIENT_SERVER_LEADER),
                        UAPI_CLIENT_SECRET_REF_KIND, "feeder"
                ));
                break;
            case KIND_PREVIEW:
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, "caefeeder",
                        MONGODB_CLIENT_SECRET_REF_KIND, "blueprint",
                        SOLR_CLIENT_SECRET_REF_KIND, HasSolrClient.getSolrClientSecretRefName(KIND_PREVIEW, SOLR_CLIENT_SERVER_LEADER),
                        UAPI_CLIENT_SECRET_REF_KIND, "feeder"
                ));
                break;
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_LIVE + " or " + KIND_PREVIEW);
        }
        if (getComponentSpec().getExtra().containsKey(EXTRA_DATABASE_SCHEMA)) {
            getSchemas().put(JDBC_CLIENT_SECRET_REF_KIND, getComponentSpec().getExtra().get(EXTRA_DATABASE_SCHEMA));
        }
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getMongoDBClientSecretRef();
        getJdbcClientSecretRef();
        getSolrClientSecretRef();
    }

    @Override
    protected PodAffinity getPodAffinity() {
        var affinityRules = new LinkedList<WeightedPodAffinityTerm>();

        if (getComponentSpec().getKind().equals(KIND_PREVIEW)) {
            affinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_CMS, 10));
        }
        if (getComponentSpec().getKind().equals(KIND_LIVE)) {
            affinityRules.add(createAffinityToComponent(CONTENT_SERVER, KIND_MLS, 10));
        }
        affinityRules.add(createAffinityToComponent(SolrComponent.SOLR, SolrComponent.KIND_LEADER, 25));

        return new PodAffinityBuilder()
                .withPreferredDuringSchedulingIgnoredDuringExecution(affinityRules.stream().filter(Objects::nonNull).toList())
                .build();
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getJdbcClientEnvVars("JDBC"));
        env.addAll(getMongoDBEnvVars());
        env.addAll(getSolrEnvVars("cae"));

        if (getComponentSpec().getKind().equals(KIND_LIVE)) {
            env.add(EnvVarSimple("REPOSITORY_URL", getTargetState().getServiceUrlFor("content-server", "mls")));
        }

        return env;
    }

    @Override
    public List<HasMetadata> buildResources() {
        var result = super.buildResources();
        resetFeederIfNeeded();
        return result;
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
                log.warn("[{}] Error resetting CAE feeder: {}", getTargetState().getContextForLogging(), name, e);
            }
            if (!getTargetState().isUpgrading()) {
                getTargetState().restartStatefulSet(name);
            }
            getTargetState().setFlag(flagName, specGeneration);
        }
    }

    /**
     * Reset the CAE Feeder.
     * Triggers this command by default: 'SPRING_PROPERTIES="" java -cp "libs/*" com.coremedia.amaro.cae.feeder.reset.ResetCaeFeeder reset'
     */
    public void resetFeeder() {
        var pods = getTargetState().getKubernetesClient().pods()
                .inNamespace(getNamespace())
                .withLabels(getSelectorLabels())
                .resources()
                .toList();

        log.debug("[{}] Resetting CAE Feeder on pods {}, using '{}'", getTargetState().getContextForLogging(),
                pods.stream().map(x -> x.get().getMetadata().getName()).toList(), resetFeederCommand);
        var results = pods.stream().map(pod -> executeCommand(pod, resetFeederCommand)).toList();

        results.stream().forEach(result -> {
            if (result.exitCode != 0 || result.output == null || !result.output.contains("The CAE Feeder will be reset when restarted")) {
                throw new CustomResourceConfigError(format("Error resetting CAE Feeder, exitcodes: [{}], output: {}",
                        results.stream().map(x -> x.exitCode).toList().toString(),
                        result.output));
            }
        });
    }
}
