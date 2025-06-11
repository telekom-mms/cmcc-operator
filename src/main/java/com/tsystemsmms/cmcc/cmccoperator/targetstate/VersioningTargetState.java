/*
 * Copyright (c) 2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.generic.SolrComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CustomResource;
import com.tsystemsmms.cmcc.cmccoperator.ingress.UrlMappingBuilderFactory;
import com.tsystemsmms.cmcc.cmccoperator.resource.ResourceReconcilerManager;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.CONTENT_SERVER;
import static com.tsystemsmms.cmcc.cmccoperator.components.corba.ContentServerComponent.KIND_RLS;
import static com.tsystemsmms.cmcc.cmccoperator.components.generic.SolrComponent.KIND_FOLLOWER;
import static com.tsystemsmms.cmcc.cmccoperator.components.generic.SolrComponent.SOLR;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.DeploymentStarted;
import static com.tsystemsmms.cmcc.cmccoperator.crds.Milestone.Ready;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Create the runtime config based on the CRD data.
 */
@Slf4j
public class VersioningTargetState extends DefaultTargetState {

    private String versionOverride;

    public VersioningTargetState(BeanFactory beanFactory,
                                 KubernetesClient kubernetesClient,
                                 ResourceNamingProviderFactory resourceNamingProviderFactory,
                                 ResourceReconcilerManager resourceReconcilerManager,
                                 Map<String, UrlMappingBuilderFactory> urlMappingBuilderFactories,
                                 YamlMapper yamlMapper,
                                 CustomResource cmcc) {
        super(beanFactory,
                kubernetesClient,
                resourceNamingProviderFactory,
                resourceReconcilerManager,
                urlMappingBuilderFactories,
                yamlMapper,
                cmcc);
    }

    @Override
    public boolean isVersioning() {
        return true;
    }

    public boolean isUpgrading() {
        return !StringUtils.isEmpty(getCmcc().getStatus().getTargetVersion()) &&
                !getCmcc().getStatus().getCurrentVersion().equals(getCmcc().getStatus().getTargetVersion());
    }

    @Override
    public String getVersion() {
        return this.versionOverride == null ? super.getVersion() : versionOverride;
    }

    public VersionOverrideClosable withCurrentlyDeployedVersion() {
        this.versionOverride = getCmcc().getStatus().getCurrentVersion();
        return () -> VersioningTargetState.this.versionOverride = null;
    }

    @Override
    public List<HasMetadata> buildResources() {
        if (Ready.equals(cmcc.getStatus().getMilestone()) &&
                isEmpty(getCmcc().getStatus().getTargetVersion())) {
            var newVersion = getCmcc().getSpec().getVersion();
            var currentVersion = getCmcc().getStatus().getCurrentVersion();

            if (StringUtils.isNotEmpty(currentVersion)) {
                if (!getCmcc().getStatus().getCurrentVersion().equals(newVersion)) {
                    log.info("[{}] Upgrading to new target version {}, current version {}", getContextForLogging(), newVersion, currentVersion);
                    getCmcc().getStatus().setTargetVersion(newVersion);
                    cmcc.getStatus().setMilestone(Milestone.DeploymentStarted);
                }
            }
        }

        var result = super.buildResources();

        if (DeploymentStarted.equals(cmcc.getStatus().getMilestone()) && isNotEmpty(getCmcc().getStatus().getTargetVersion())) {
            log.info("[{}] Trying to disable replication on all remaining Replication Live Servers", getContextForLogging());
            componentCollection.findAllOfTypeAndKind(CONTENT_SERVER, KIND_RLS)
                    .parallel()
                    .map(ContentServerComponent.class::cast)
                    .forEach(ContentServerComponent::disableRlsReplication);

            log.info("[{}] Trying to disable replication on all remaining Solr followers", getContextForLogging());
            componentCollection.findAllOfTypeAndKind(SOLR, KIND_FOLLOWER)
                    .parallel()
                    .map(SolrComponent.class::cast)
                    .forEach(SolrComponent::disableReplication);
        }

        return result;
    }

    @Override
    public void onMilestoneReached(Milestone previousMilestone) {
        super.onMilestoneReached(previousMilestone);

        var specVersion = getCmcc().getSpec().getVersion();
        var currentVersion = getCmcc().getStatus().getCurrentVersion();

        if (cmcc.getStatus().getMilestone() == Ready && !currentVersion.equals(specVersion)) {
            log.info("[{}] Newly arrived readiness from deployment path, setting current version from {} to {}",
                    getContextForLogging(),
                    currentVersion,
                    specVersion);

            getCmcc().getStatus().setCurrentVersion(specVersion);

            if (isNotEmpty(getCmcc().getStatus().getTargetVersion())) {
                log.info("[{}] Also clearing target version from {}", getContextForLogging(), getCmcc().getSpec().getVersion());
                getCmcc().getStatus().setTargetVersion("");
            }
        }
    }

    public interface VersionOverrideClosable extends AutoCloseable {
        @Override
        void close();
    }
}
