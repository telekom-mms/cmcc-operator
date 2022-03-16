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

import com.tsystemsmms.cmcc.cmccoperator.components.HasMySQLSchema;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

@Slf4j
public class ContentServerComponent extends CorbaComponent implements HasMySQLSchema, HasService {
    public static final String KIND_CMS = "cms";
    public static final String KIND_MLS = "mls";
    public static final String KIND_RLS = "rls";

    public static final String LICENSE_VOLUME_NAME = "license";

    @Getter
    final String databaseSchema;
    String licenseSecretName;

    public ContentServerComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "content-server");
        if (getComponentSpec().getKind() == null)
            throw new IllegalArgumentException("kind must be set to either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        switch (componentSpec.getKind()) {
            case KIND_CMS:
                licenseSecretName = getSpec().getLicenseSecrets().getCMSLicense();
                databaseSchema = "management";
                break;
            case KIND_MLS:
                licenseSecretName = getSpec().getLicenseSecrets().getMLSLicense();
                databaseSchema = "master";
                break;
            case KIND_RLS:
                licenseSecretName = getSpec().getLicenseSecrets().getRLSLicense();
                databaseSchema = "replication";
                break;
            default:
                throw new IllegalArgumentException("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        }
    }

    @Override
    public String getResourceName() {
        String kind;

        switch (getComponentSpec().getKind()) {
            case KIND_CMS:
                kind = "content-management-server";
                break;
            case KIND_MLS:
                kind = "master-live-server";
                break;
            case KIND_RLS:
                kind = "replication-live-server";
                break;
            default:
                throw new IllegalArgumentException("kind \"" + getComponentSpec().getKind() + "\" is illegal, must be either " + KIND_CMS + ", " + KIND_MLS + ", or " + KIND_RLS);
        }

        return concatOptional(getDefaults().getNamePrefix(), kind);
    }

    @Override
    public String getDatabaseSecretName() {
        return concatOptional(
                getDefaults().getNamePrefix(),
                "mysql",
                getDatabaseSchema());
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
        env.addAll(getMySqlEnvVars());

        return env;
    }

    public Map<String, String> getSpringBootProperties() {
        Map<String, String> properties = super.getSpringBootProperties();

        properties.putAll(Map.of(
                "cap.server.license", "/coremedia/licenses/license.zip",
                "com.coremedia.corba.server.host", this.getServiceName(),
                "cap.server.cache.resource-cache-size", "5000"
                ));
        if (getComponentSpec().getKind().equals(KIND_CMS)) {
            // can't use "publisher.target[0].iorUrl" because of the square brackets
            properties.put("publisher.target[0].iorUrl", getTargetState().getServiceUrlFor("content-server", "mls"));
        }
        if (getComponentSpec().getKind().equals(KIND_RLS)) {
            properties.put("replicator.publication-ior-url", getTargetState().getServiceUrlFor("content-server", "mls"));
        }

        return properties;
    }

    /**
     * Get a list of environment variables to configure the MySQL database connection of the component.
     *
     * @return list of env vars
     */
    public EnvVarSet getMySqlEnvVars() {
        MySQLDetails details = getMySQLDetails(getDatabaseSchema());
        EnvVarSet env = new EnvVarSet();

        env.add(EnvVarSimple("MYSQL_HOST", details.getHostName())); // needed for MySQL command line tools
        env.add(EnvVarSimple("SQL_STORE_DRIVER", "com.mysql.cj.jdbc.Driver"));
        env.add(EnvVarSecret("SQL_STORE_PASSWORD", details.getSecretName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        env.add(EnvVarSimple("SQL_STORE_SCHEMA", getDatabaseSchema()));
        env.add(EnvVarSimple("SQL_STORE_URL", details.getJdbcUrl()));
        env.add(EnvVarSecret("SQL_STORE_USER", details.getSecretName(), TargetState.DATABASE_SECRET_USERNAME_KEY));
        return env;
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
    public String getUapiClientDefaultUsername() {
        return "publisher";
    }

}
