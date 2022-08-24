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
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public class ContentServerComponent extends CorbaComponent implements HasJdbcClient, HasService {
    public static final String KIND_CMS = "cms";
    public static final String KIND_MLS = "mls";
    public static final String KIND_RLS = "rls";

    public static final String MANAGEMENT_SCHEMA = "management";
    public static final String MASTER_SCHEMA = "master";

    public static final String LICENSE_VOLUME_NAME = "license";

    String licenseSecretName;

    int rls = 0;

    public ContentServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "content-server");
        if (getComponentSpec().getKind() == null)
            throw new CustomResourceConfigError("kind must be set to either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        switch (componentSpec.getKind()) {
            case KIND_CMS:
                licenseSecretName = getSpec().getLicenseSecrets().getCMSLicense();
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, MANAGEMENT_SCHEMA,
                        UAPI_CLIENT_SECRET_REF_KIND, "publisher"
                ));
                break;
            case KIND_MLS:
                licenseSecretName = getSpec().getLicenseSecrets().getMLSLicense();
                setDefaultSchemas(Map.of(
                        JDBC_CLIENT_SECRET_REF_KIND, MASTER_SCHEMA,
                        UAPI_CLIENT_SECRET_REF_KIND, "publisher"
                ));
                break;
            case KIND_RLS:
                rls = getInt(getCmcc().getSpec().getWith().getDelivery().getRls());
                if (rls < 1) {
                    throw new CustomResourceConfigError("with.delivery.rls must be 1 or higher, not " + rls);
                }
                licenseSecretName = getSpec().getLicenseSecrets().getRLSLicense();
                setDefaultSchemas(Map.of(
                        UAPI_CLIENT_SECRET_REF_KIND, "publisher"
                ));
                break;
            default:
                throw new CustomResourceConfigError("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        }
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();

        if (rls == 0) {
            // CMS and MLS
            resources.add(buildStatefulSet());
            resources.add(buildService());
        } else {
            // RLS
            resources.add(buildServiceRls());
            // volumes etc
            for (int i = 1; i <= rls; i++) {
                resources.add(buildStatefulSetRls(i));
                resources.add(getPersistentVolumeClaim(getTargetState().getResourceNameFor(this, getRlsName(i))));
            }
        }
        return resources;
    }

    public StatefulSet buildStatefulSetRls(int i) {
        EnvVarSet env = getEnvVarsForRls(i);
        env.addAll(getComponentSpec().getEnv());

        return new StatefulSetBuilder()
                .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this, getRlsName(i))))
                .withSpec(new StatefulSetSpecBuilder()
                        .withServiceName(getTargetState().getServiceNameFor(this))
                        .withSelector(new LabelSelectorBuilder()
                                .withMatchLabels(getSelectorLabels())
                                .build())
                        .withTemplate(new PodTemplateSpecBuilder()
                                .withMetadata(new ObjectMetaBuilder()
                                        .withLabels(getSelectorLabels())
                                        .build())
                                .withSpec(new PodSpecBuilder()
                                        .withContainers(buildContainersWithEnv(env))
                                        .withInitContainers(getInitContainers())
                                        .withSecurityContext(getPodSecurityContext())
                                        .withTerminationGracePeriodSeconds(getTerminationGracePeriodSeconds())
                                        .withVolumes(getVolumes(getRlsName(i)))
                                        .build())
                                .build())
                        .build())
                .build();
    }

    private String getRlsName(int i) {
        return "" + i;
    }

    public Service buildServiceRls() {
        return new ServiceBuilder()
                .withMetadata(getTargetState().getResourceMetadataFor(this))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabelsForRls())
                        .withPorts(getServicePorts())
                        .build())
                .build();
    }

    @Override
    public HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = super.getSelectorLabels();
        labels.put("cmcc.tsystemsmms.com/kind", getComponentSpec().getKind());
        return labels;
    }

    public HashMap<String, String> getSelectorLabelsForRls() {
        HashMap<String, String> labels = getTargetState().getSelectorLabels();
        labels.put("cmcc.tsystemsmms.com/type", getComponentSpec().getType());
        labels.put("cmcc.tsystemsmms.com/kind", "rls");
        return labels;
    }

    @Override
    public List<PersistentVolumeClaim> getVolumeClaims() {
        List<PersistentVolumeClaim> claims = super.getVolumeClaims();

        claims.add(getPersistentVolumeClaim(PVC_UAPI_BLOBCACHE));

        return claims;
    }

    /**
     * PVCs for RLSes.
     *
     * @param name per-RLS name
     * @return list of Volume resources
     */
    public List<Volume> getVolumes(String name) {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName(LICENSE_VOLUME_NAME)
                .withSecret(new SecretVolumeSourceBuilder()
                        .withSecretName(licenseSecretName)
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName(PVC_UAPI_BLOBCACHE)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this, name))
                        .build())
                .build());

        return volumes;
    }


    @Override
    public void requestRequiredResources() {
        super.requestRequiredResources();
        if (rls == 0) {
            getJdbcClientSecretRef();
        } else {
            for (int i = 1; i <= rls; i++) {
                getJdbcClientSecretRef(jdbcSecretName(i));
            }
        }
    }

    private String jdbcSecretName(int i) {
        return "rls" + i;
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

        switch (getComponentSpec().getKind()) {
            case KIND_CMS:
                env.addAll(getJdbcClientEnvVars("SQL_STORE"));
                env.addAll(getUapiClientEnvVars("PUBLISHER_LOCAL"));
                env.addAll(getUapiClientEnvVars("PUBLISHER_TARGET_0"));
                break;
            case KIND_MLS:
                env.addAll(getJdbcClientEnvVars("SQL_STORE"));
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

    EnvVarSet getEnvVarsForRls(int i) {
        EnvVarSet env = getEnvVars();
        env.addAll(getJdbcClientEnvVars("SQL_STORE", getJdbcClientSecretRef(jdbcSecretName(i))));
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
        volumes.add(new VolumeBuilder()
                .withName(PVC_UAPI_BLOBCACHE)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this, PVC_UAPI_BLOBCACHE))
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
        volumes.add(new VolumeMountBuilder()
                .withName("coremedia-var-tmp")
                .withMountPath("/coremedia/var/tmp")
                .build());

        return volumes;
    }

    @Override
    public Optional<Boolean> isReady() {
        if (rls == 0)
            return super.isReady();
        if (Milestone.compareTo(getCmcc().getStatus().getMilestone(), getComponentSpec().getMilestone()) < 0)
            return Optional.empty();
        boolean ready = true;
        for (int i = 1; i <= rls; i++) {
            if (!getTargetState().isStatefulSetReady(getTargetState().getResourceNameFor(this, getRlsName(i))))
                ready = false;
        }
        return Optional.of(ready);
    }
}
