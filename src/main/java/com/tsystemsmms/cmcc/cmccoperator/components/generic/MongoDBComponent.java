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
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ImageSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.ClientSecret;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

import static com.tsystemsmms.cmcc.cmccoperator.components.HasMongoDBClient.MONGODB_CLIENT_SECRET_REF_KIND;
import static com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef.DEFAULT_PASSWORD_KEY;
import static com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef.DEFAULT_USERNAME_KEY;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSecret;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.format;

/**
 * Build a MongoDB deployment.
 */
@Slf4j
public class MongoDBComponent extends AbstractComponent implements HasService {
    public static final String MONGODB_ROOT_USERNAME = "root";

    public MongoDBComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "");
    }

    @Override
    public void requestRequiredResources() {
        getClientSecretRef();
    }

    ClientSecretRef getClientSecretRef() {
        return getTargetState().getClientSecretRef(MONGODB_CLIENT_SECRET_REF_KIND, MONGODB_ROOT_USERNAME,
                (clientSecret, password) -> getTargetState().loadOrBuildSecret(clientSecret, Map.of(
                        DEFAULT_PASSWORD_KEY, password,
                        DEFAULT_USERNAME_KEY, MONGODB_ROOT_USERNAME
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
        return resources;
    }

    @Override
    public Map<String, String> getPodLabels() {
        return super.getSelectorLabels(); // no version on the db
    }

    @Override
    public long getTerminationGracePeriodSeconds() {
        return 30L;
    }

    @Override
    public EnvVarSet getEnvVars() {
        ClientSecretRef csr = getClientSecretRef();
        EnvVarSet env = new EnvVarSet();
        env.add(EnvVarSecret("MONGO_INITDB_ROOT_USERNAME", csr.getSecretName(), csr.getUsernameKey()));
        env.add(EnvVarSecret("MONGO_INITDB_ROOT_PASSWORD", csr.getSecretName(), csr.getPasswordKey()));
        return env;
    }

    @Override
    public Probe getStartupProbe() {
        var result = new ProbeBuilder()
                .withFailureThreshold(6)
                .withInitialDelaySeconds(5)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(5);

        if (isMongo6()) {
            result.withExec(new ExecActionBuilder()
                    .withCommand("bash", "-ec", "mongosh $TLS_OPTIONS --eval 'db.hello().isWritablePrimary || db.hello().secondary' | grep -q 'true'")
                    .build());
        } else {
            result.withExec(new ExecActionBuilder()
                    .withCommand("bash", "-ec", "mongo --disableImplicitSessions $TLS_OPTIONS --eval 'db.hello().isWritablePrimary || db.hello().secondary' | grep -q 'true'")
                    .build());
        }

        return result.build();
    }

    @Override
    public Probe getLivenessProbe() {
        var result = new ProbeBuilder()
                .withFailureThreshold(6)
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(10)
                .withSuccessThreshold(1)
                .withTimeoutSeconds(5);

        if (isMongo6()) {
            result.withExec(new ExecActionBuilder()
                    .withCommand("/bin/bash", "-ec", "mongosh --quiet \"$(hostname --ip-address || echo '127.0.0.1')/test\" --eval 'quit(db.runCommand({ ping: 1 }).ok ? 0 : 2)' || exit 1")
                    .build());
        } else {
            result.withExec(new ExecActionBuilder()
                    .withCommand("mongo", "--disableImplicitSessions", "--eval", "db.adminCommand('ping')")
                    .build());
        }

        return result.build();
    }

    @Override
    public Probe getReadinessProbe() {
        return getStartupProbe();
    }

    @Override
    public ImageSpec getDefaultImage() {
        return new ImageSpec("docker.io/library/mongo:5.0");
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

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        volumeMounts.add(new VolumeMountBuilder()
                .withName(getTargetState().getResourceNameFor(this))
                .withMountPath("/data/db")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName(getTargetState().getResourceNameFor(this, "init"))
                .withMountPath("/docker-entrypoint-initdb.d/create-default-users.js")
                .withSubPath("create-default-users.js")
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

    boolean isMongo6() {
        var version = getComponentSpec().getExtra().get("version");
        return !StringUtils.isEmpty(version) && version.equals("6.0");
    }

    public static Map<String, String> createUsersFromClientSecrets(TargetState targetState) {
        Map<String, ClientSecret> secrets = targetState.getClientSecrets(MONGODB_CLIENT_SECRET_REF_KIND);

        if (secrets == null) {
            log.warn("[{}] No MongoDB users to be created", targetState.getContextForLogging());
            return Collections.emptyMap();
        }

        StringBuilder createUsersJs = new StringBuilder();
        ClientSecret rootClientSecret = secrets.get(MONGODB_ROOT_USERNAME);
        if (rootClientSecret == null)
            throw new CustomResourceConfigError("No secret available for MongoDB root user");

        // log in as root
        Map<String, String> rootData = rootClientSecret.getStringData();
        createUsersJs.append("db = db.getSiblingDB('admin');\n");
        createUsersJs.append(format("db.auth('{}', '{}');\n",
                rootData.get(DEFAULT_USERNAME_KEY), rootData.get(DEFAULT_PASSWORD_KEY)));

        for (ClientSecret cs : secrets.values()) {
            Map<String, String> data = cs.getStringData();
            if (MONGODB_ROOT_USERNAME.equals(data.get(DEFAULT_USERNAME_KEY)))
                continue;
            // we would like to give client only rights to a specific database, but CM requires the right to create multiple databases (or somehow know which DBs will be created; the list is undocumented, however.
//            createUsersJs.append(format("db = db.getSiblingDB('{}');\ndb.createUser({user: '{}', pwd: '{}', roles: ['readWrite', 'dbAdmin']});\n",
//                    data.get(DEFAULT_SCHEMA_KEY),
//                    data.get(DEFAULT_USERNAME_KEY),
//                    data.get(DEFAULT_PASSWORD_KEY)
//            ));
            createUsersJs.append(format("db.createUser({user: '{}', pwd: '{}', roles: ['root']});\n",
                    data.get(DEFAULT_USERNAME_KEY),
                    data.get(DEFAULT_PASSWORD_KEY)
            ));
        }
        return Map.of("create-default-users.js", createUsersJs.toString());
    }
}
