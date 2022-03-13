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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSecret;

/**
 * Build a MySQL deployment. Resources stolen from the Bitnami MySQl chart.
 */
public class MongoDBComponent extends AbstractComponent implements HasService {
    public static final String MONGODB_SECRET_URL_KEY = "url";
    public MongoDBComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "");
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = new EnvVarSet();
        env.add(EnvVarSecret("MONGO_INITDB_ROOT_USERNAME", getResourceName(), TargetState.DATABASE_SECRET_USERNAME_KEY));
        env.add(EnvVarSecret("MONGO_INITDB_ROOT_PASSWORD", getResourceName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        return env;
    }

    @Override
    public Probe getStartupProbe() {
        return new ProbeBuilder()
                .withExec(new ExecActionBuilder()
                        .withCommand("bash", "-ec", "mongo --disableImplicitSessions $TLS_OPTIONS --eval 'db.hello().isWritablePrimary || db.hello().secondary' | grep -q 'true'")
                        .build())
                .withFailureThreshold(6)
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(5)
                .build();
    }

    @Override
    public Probe getLivenessProbe() {
        return new ProbeBuilder()
                .withExec(new ExecActionBuilder()
                        .withCommand("mongo", "--disableImplicitSessions", "--eval", "db.adminCommand('ping')")
                        .build())
                .withFailureThreshold(6)
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(5)
                .build();
    }

    @Override
    public Probe getReadinessProbe() {
        return getStartupProbe();
    }

    @Override
    public String getImage() {
        return "docker.io/library/mongo:5.0";
    }

    @Override
    public List<ContainerPort> getContainerPorts() {
        return List.of(
                new ContainerPortBuilder()
                        .withName("mongodb")
                        .withContainerPort(27017)
                        .build()
        );
    }

    @Override
    public List<ServicePort> getServicePorts() {
        return List.of(
                new ServicePortBuilder().withName("ior").withPort(27017).withNewTargetPort("mongodb").build());
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

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName(getResourceName())
                .withMountPath("/data/db")
                .build());

        return volumeMounts;
    }

    Secret buildSecret() {
        DatabaseSecret secret = getTargetState().getDatabaseSecret(getResourceName(), "root");
        return new SecretBuilder()
                .withMetadata(getResourceMetadata())
                .withStringData(Map.of(
                        TargetState.DATABASE_SECRET_USERNAME_KEY, secret.getUsername(),
                        TargetState.DATABASE_SECRET_PASSWORD_KEY, secret.getPassword(),
                        MONGODB_SECRET_URL_KEY, "mongodb://" + secret.getUsername() + ":" + secret.getPassword() + "@" + getResourceName() + ":27017"
                ))
                .build();
    }

    @Override
    public List<HasMetadata> buildResources() {
        List<HasMetadata> resources = new LinkedList<>();
        resources.add(buildPvc());
        resources.add(buildStatefulSet());
        resources.add(buildService());
        resources.add(buildSecret());
        return resources;
    }
}
