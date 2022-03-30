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
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSecret;

@Slf4j
public class ContentServerComponent extends CorbaComponent implements HasJdbcClient, HasService {
    public static final String KIND_CMS = "cms";
    public static final String KIND_MLS = "mls";
    public static final String KIND_RLS = "rls";

    public static final String MANAGEMENT_SCHEMA = "management";
    public static final String MASTER_SCHEMA = "master";

    public static final String LICENSE_VOLUME_NAME = "license";

    @Getter
    final String databaseSchema;
    String licenseSecretName;

    public ContentServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "content-server");
        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        switch (componentSpec.getKind()) {
            case KIND_CMS:
                licenseSecretName = getSpec().getLicenseSecrets().getCMSLicense();
                databaseSchema = MANAGEMENT_SCHEMA;
                break;
            case KIND_MLS:
                licenseSecretName = getSpec().getLicenseSecrets().getMLSLicense();
                databaseSchema = MASTER_SCHEMA;
                break;
            case KIND_RLS:
//                licenseSecretName = getSpec().getLicenseSecrets().getRLSLicense();
//                databaseSchema = "replication";
//                break;
                throw new CustomResourceConfigError("not implemented yet");
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        }
    }

    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        getJdbcClientSecretRef();
    }

    @Override
    public String getBaseResourceName() {
        switch (getComponentSpec().getKind()) {
            case KIND_CMS:
                return "content-management-server";
            case KIND_MLS:
                return "master-live-server";
            case KIND_RLS:
                return "replication-live-server";
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        }
    }

    @Override
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = super.getSelectorLabels();
        labels.put("cmcc.tsystemsmms.com/kind", getComponentSpec().getKind());
        return labels;
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public List<Container> getInitContainers() {
        return Collections.emptyList();
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.addAll(getJdbcClientEnvVars("SQL_STORE"));
        switch (getComponentSpec().getKind()) {
            case KIND_CMS:
                env.addAll(getUapiClientEnvVars("PUBLISHER_LOCAL"));
                env.addAll(getUapiClientEnvVars("PUBLISHER_TARGET_0"));
                break;
            case KIND_MLS:
                break;
            case KIND_RLS:
                env.addAll(getUapiClientEnvVars("REPLICATOR_PUBLICATION"));
                break;
        }

        for (ClientSecret cs : getTargetState().getClientSecrets(UAPI_CLIENT_SECRET_REF_KIND).values()) {
            Secret secret = cs.getSecret().orElseThrow(() -> new CustomResourceConfigError("Unable to find secret for clientSecretRef \"" + cs.getRef().getSecretName() + "\""));
            String username = secret.getStringData().get(cs.getRef().getUsernameKey());
            if (cs.getRef().getUsernameKey() == null || username == null) {
                throw new CustomResourceConfigError("Secret \"" + secret.getMetadata().getName()
                        + "\" does not contain the field \"" + cs.getRef().getUsernameKey()
                        + "\" for the username, or it is null");
            }
            env.add(EnvVarSecret("CAP_SERVER_INITIALPASSWORD_" + username.toUpperCase(Locale.ROOT),
                    cs.getRef().getSecretName(), cs.getRef().getPasswordKey()));
        }

        return env;
    }

    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(Map.of(
                "cap.server.license", "/coremedia/licenses/license.zip",
                "com.coremedia.corba.server.host", getTargetState().getResourceNameFor(this),
                "cap.server.cache.resource-cache-size", "5000"
                ));
        if (getComponentSpec().getKind().equals(KIND_CMS)) {
            properties.put("publisher.target[0].iorUrl", getTargetState().getServiceUrlFor("content-server", "mls"));
            properties.put("publisher.target[0].ior-url", getTargetState().getServiceUrlFor("content-server", "mls"));
            properties.put("publisher.target[0].name", "mls");
        }
        if (getComponentSpec().getKind().equals(KIND_RLS)) {
            properties.put("replicator.publication-ior-url", getTargetState().getServiceUrlFor("content-server", "mls"));
        }

        properties.put("debug", "true");

        return properties;
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName(LICENSE_VOLUME_NAME)
                .withSecret(new SecretVolumeSourceBuilder()
                        .withSecretName(licenseSecretName)
                        .build())
                .build());

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumes = new LinkedList<>(super.getVolumeMounts());

        VolumeMount licenseVolumeMount = new VolumeMountBuilder()
                .withName(LICENSE_VOLUME_NAME)
                .withMountPath("/coremedia/licenses")
                .build();
        volumes.add(licenseVolumeMount);

        return volumes;
    }

    @Override
    public String getJdbcClientDefaultSchema() {
        return databaseSchema;
    }

    @Override
    public String getUapiClientDefaultUsername() {
        return "publisher";
    }
}
