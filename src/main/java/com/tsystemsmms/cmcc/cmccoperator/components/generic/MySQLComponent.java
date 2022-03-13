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
import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.DatabaseSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSecret;

/**
 * Build a MySQL deployment. Resources stolen from the Bitnami MySQl chart.
 */
public class MySQLComponent extends AbstractComponent implements HasService {
    public MySQLComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "");
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = new EnvVarSet();
        env.add(EnvVarSecret("MYSQL_ROOT_PASSWORD", getResourceName(), "password"));
        return env;
    }

    @Override
    public Probe getStartupProbe() {
        return new ProbeBuilder()
                .withExec(new ExecActionBuilder()
                        .withCommand("/bin/bash", "-ec", "password_aux=\"${MYSQL_ROOT_PASSWORD:-}\"\n" +
                                "if [[ -f \"${MYSQL_ROOT_PASSWORD_FILE:-}\" ]]; then\n" +
                                "    password_aux=$(cat \"$MYSQL_ROOT_PASSWORD_FILE\")\n" +
                                "fi\n" +
                                "mysqladmin status -uroot -p\"${password_aux}\"")
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
    public String getImage() {
        return "docker.io/mariadb:10.7";
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
                .withName(getResourceName())
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(getResourceName())
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName(getResourceName() + "-config")
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(getResourceName())
                        .withDefaultMode(420)
                        .build())
                .build());
        volumes.add(new VolumeBuilder()
                .withName(getResourceName() + "-init")
                .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(getResourceName() + "-extra")
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
                .withName(getResourceName())
                .withMountPath("/var/lib/mysql")
                .build());
//        volumeMounts.add(new VolumeMountBuilder()
//                .withName(getResourceName() + "-config")
//                .withMountPath("/opt/bitnami/mysql/conf/my.cnf")
//                .withSubPath("my.cnf")
//                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName(getResourceName() + "-init")
                .withMountPath("/docker-entrypoint-initdb.d")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("run-mysql")
                .withMountPath("/run/mysqld")
                .build());

        return volumeMounts;
    }

    Secret buildSecret() {
        DatabaseSecret secret = getTargetState().getDatabaseSecret(getResourceName(), "root");
        return getTargetState().buildDatabaseSecret(getResourceName(), secret);
    }

    List<ConfigMap> buildExtraConfigMaps() {
        if (getComponentSpec().getExtra() == null || getComponentSpec().getExtra().size() == 0)
            return Collections.emptyList();
        return Collections.singletonList(new ConfigMapBuilder()
                .withMetadata(getResourceMetadataForName(getResourceName() + "-extra"))
                .withData(getComponentSpec().getExtra())
                .build());
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildPvc());
        resources.add(buildStatefulSet());
        resources.add(buildService());
        resources.add(buildSecret());
        resources.addAll(buildExtraConfigMaps());
        return resources;
    }
}
