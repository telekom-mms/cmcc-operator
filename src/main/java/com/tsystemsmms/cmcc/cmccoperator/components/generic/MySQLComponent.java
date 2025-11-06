/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.generic;

import com.tsystemsmms.cmcc.cmccoperator.components.AbstractComponent;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ImageSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasJdbcClient.JDBC_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

/**
 * Build a MySQL deployment.
 */
@Slf4j
public class MySQLComponent extends AbstractComponent implements HasService {
    public static final String MYSQL = "mysql";
    public static final String MYSQL_ROOT_USERNAME = "root";
    public static final String MYSQL_USE_MARIA_JDBC = "useMariaDBJdbc";

    @Getter
    private boolean useMariaDbJdbc;

    public MySQLComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "");
        this.useMariaDbJdbc = Boolean.parseBoolean(componentSpec.getExtra().getOrDefault(MYSQL_USE_MARIA_JDBC, "false"));
    }

    @Override
    public Component updateComponentSpec(ComponentSpec newCs) {
        var result = super.updateComponentSpec(newCs);
        this.useMariaDbJdbc = Boolean.parseBoolean(newCs.getExtra().getOrDefault(MYSQL_USE_MARIA_JDBC, "false"));
        return result;
    }

    @Override
    public void requestRequiredResources() {
        getClientSecretRef();
    }

    ClientSecretRef getClientSecretRef() {
        return getTargetState().getClientSecretRef(getComponentSpec().getType(), MYSQL_ROOT_USERNAME,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        ClientSecretRef.DEFAULT_PASSWORD_KEY, password,
                        ClientSecretRef.DEFAULT_USERNAME_KEY, MYSQL_ROOT_USERNAME
                ))
        );
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(getPersistentVolumeClaim(getTargetState().getResourceNameFor(this),
                getVolumeSize(ComponentSpec.VolumeSize::getData)));
        resources.add(buildStatefulSet());
        resources.add(buildService());
        resources.addAll(buildExtraConfigMaps());

        restoreUserSchemasIfNeeded();

        return resources;
    }

    @Override
    public Map<String, String> getPodLabels() {
        return super.getSelectorLabels(); // no version on the db
    }

    protected void restoreUserSchemasIfNeeded() {
        String name = getTargetState().getResourceNameFor(this);
        String flag = concatOptional("restore", name, "users");
        if (getTargetState().isFlag(flag)) {
            if (!getState().isReady().orElse(false)) {
                return;
            }
            restoreUserSchemas();
            getTargetState().setFlag(flag, false);
        }
    }

    private void restoreUserSchemas() {
        var pod = getTargetState().getKubernetesClient().pods()
                .inNamespace(getNamespace())
                .withLabels(getSelectorLabels())
                .resources()
                .findFirst().get();

        var result = executeCommand(pod, "mysql -v -u root --password=$MYSQL_ROOT_PASSWORD < /docker-entrypoint-initdb.d/create-default-users.sql");

        if (result.exitCode != 0) {
            log.warn("[{}] Error while restoring database user schemas for {}. Output: {} Error: {}",
                    getTargetState().getContextForLogging(),
                    getTargetState().getResourceNameFor(this),
                    result.output,
                    result.errorOutput);
        }
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public EnvVarSet getEnvVars() {
        ClientSecretRef csr = getClientSecretRef();
        EnvVarSet env = new EnvVarSet();
        env.add(EnvVarSecret("MYSQL_ROOT_PASSWORD", csr.getSecretName(), csr.getPasswordKey()));
        return env;
    }

    @Override
    public Probe getStartupProbe() {
        return new ProbeBuilder()
                .withExec(new ExecActionBuilder()
                        .withCommand("/bin/bash", "-ec","""
                                password_aux="${MYSQL_ROOT_PASSWORD:-}"
                                if [[ -f "${MYSQL_ROOT_PASSWORD_FILE:-}" ]]; then
                                    password_aux=$(cat "$MYSQL_ROOT_PASSWORD_FILE")
                                fi
                                mysqladmin status -uroot -p"${password_aux}"
                                """)
                        .build())
                .withFailureThreshold(300)
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(10)
                .build();
    }

    @Override
    public Probe getLivenessProbe() {
        return getStartupProbe();
    }

    @Override
    public Probe getReadinessProbe() {
        return getStartupProbe();
    }

    @Override
    public ImageSpec getDefaultImage() {
        return new ImageSpec("docker.io/mysql:9");
    }


    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("mysql")
                        .withContainerPort(3306)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("ior").withPort(3306).withNewTargetPort("mysql").build());
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        volumes.add(new VolumeBuilder()
                .withName(getTargetState().getResourceNameFor(this))
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getTargetState().getResourceNameFor(this))
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName(getTargetState().getResourceNameFor(this, "init"))
                .withSecret(new SecretVolumeSourceBuilder()
                        .withSecretName(getTargetState().getResourceNameFor(this, "extra"))
                        .withDefaultMode(420)
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("run-mysql")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName(getTargetState().getResourceNameFor(this))
                .withMountPath("/var/lib/mysql")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName(getTargetState().getResourceNameFor(this, "init"))
                .withMountPath("/docker-entrypoint-initdb.d")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("run-mysql")
                .withMountPath("/run/mysqld")
                .build());

        return volumeMounts;
    }

    List<HasMetadata> buildExtraConfigMaps() {
        if (getComponentSpec().getExtra() == null || getComponentSpec().getExtra().size() == 0)
            return Collections.emptyList();
        return Collections.singletonList(new SecretBuilder()
                .withMetadata(getResourceMetadataForName(getTargetState().getResourceNameFor(this) + "-extra"))
                .withType("Opaque")
                .withStringData(getComponentSpec().getExtra())
                .build());
    }

    public static Map<String, String> createUsersFromClientSecrets(TargetState targetState) {
        Map<String, ClientSecret> secrets = targetState.getClientSecrets(JDBC_CLIENT_SECRET_REF_KIND);

        if (secrets == null) {
            throw new CustomResourceConfigError("No MySQL users to be created");
        }

        StringBuilder sql = new StringBuilder();
        for (ClientSecret cs : secrets.values()) {
            Map<String, String> data = cs.getStringData();
            if (MYSQL_ROOT_USERNAME.equals(data.get(ClientSecretRef.DEFAULT_USERNAME_KEY)))
                continue;
            sql.append(format("CREATE SCHEMA IF NOT EXISTS {} CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;\n", data.get(ClientSecretRef.DEFAULT_SCHEMA_KEY)));
            sql.append(format("CREATE USER IF NOT EXISTS '{}'@'%' IDENTIFIED BY '{}';\n", data.get(ClientSecretRef.DEFAULT_USERNAME_KEY), data.get(ClientSecretRef.DEFAULT_PASSWORD_KEY)));
            sql.append(format("ALTER USER '{}'@'%' IDENTIFIED BY '{}';\n", data.get(ClientSecretRef.DEFAULT_USERNAME_KEY), data.get(ClientSecretRef.DEFAULT_PASSWORD_KEY)));
            sql.append(format("GRANT ALL PRIVILEGES ON {}.* TO '{}'@'%';\n", data.get(ClientSecretRef.DEFAULT_SCHEMA_KEY), data.get(ClientSecretRef.DEFAULT_USERNAME_KEY)));
        }

        return Map.of("create-default-users.sql", sql.toString());
    }
}
