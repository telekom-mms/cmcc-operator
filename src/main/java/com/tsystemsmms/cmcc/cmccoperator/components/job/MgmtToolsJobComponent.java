/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.job;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.LinkedList;
import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.*;

/**
 * Run the management-tools container to import content, users, themes, and workflows, and publish content.
 *
 * In addition to the ComponentSpec, configuration is taken from the importJob property of the custom resource.
 */
public class MgmtToolsJobComponent extends JobComponent {
    public static final String CONTENT_USERS_FRONTEND_VOLUME = "content-users-frontend";
    public static final String CONTENT_USERS_FRONTEND_PATH = "/" + CONTENT_USERS_FRONTEND_VOLUME;

    public MgmtToolsJobComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec, "management-tools");
        activeDeadlineSeconds = 30 * 60L;
        if (componentSpec.getArgs().size() == 0) {
            componentSpec.setArgs(new LinkedList<>(getImportJob().getTasks()));
        }
    }

    @Override
    public EnvVarSet getEnvVars() {
        EnvVarSet env = super.getEnvVars();

        env.add(EnvVarSimple("JAVA_HEAP", ""));
        env.add(EnvVarSimple("JAVA_OPTS", "-XX:MinRAMPercentage=80 -XX:MaxRAMPercentage=95"));
        env.add(EnvVarSimple("CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));
        env.add(EnvVarSimple("DEV_MASTER_CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "mls")));
        env.add(EnvVarSimple("DEV_MANAGEMENT_CAP_CLIENT_SERVER_IOR_URL", getTargetState().getServiceUrlFor("content-server", "cms")));
        env.add(EnvVarSimple("DEBUG_ENTRYPOINT", "true"));
        env.add(EnvVarSimple("IMPORT_DIR", "/coremedia/import"));
        if (getImportJob().isForceContentImport()) {
            env.add(EnvVarSimple("FORCE_REIMPORT_CONTENT", "true"));
        }
        if (getImportJob().isForceThemeImport()) {
            env.add(EnvVarSimple("FORCE_REIMPORT_THEMES", "true"));
        }
        if (!getImportJob().getContentUsersThemesPvc().isBlank()) {
            env.add(EnvVarSimple("CONTENT_USERS_FRONTEND_PATH", CONTENT_USERS_FRONTEND_PATH));
        }
        if ( getImportJob().isBlobServer()) {
            env.add(EnvVarSimple("BLOB_STORAGE_URL", getTargetState().getServiceUrlFor("blob-server")));
        }
        if (!getImportJob().getContentUsersUrl().isBlank()) {
            env.add(EnvVarSimple("CONTENT_ARCHIVE_URL", getImportJob().getContentUsersUrl()));
            env.add(EnvVarSecret("CONTENT_ARCHIVE_USERNAME",
                    getImportJob().getContentUsersAuth().getSecret(),
                    getImportJob().getContentUsersAuth().getUsername()));
            env.add(EnvVarSecret("CONTENT_ARCHIVE_PASSWORD",
                    getImportJob().getContentUsersAuth().getSecret(),
                    getImportJob().getContentUsersAuth().getPassword()));
        }
        if (!getImportJob().getThemesUrl().isBlank()) {
            env.add(EnvVarSimple("THEMES_ARCHIVE_URL", getImportJob().getThemesUrl()));
            env.add(EnvVarSecret("THEMES_ARCHIVE_USERNAME",
                    getImportJob().getThemesAuth().getSecret(),
                    getImportJob().getThemesAuth().getUsername()));
            env.add(EnvVarSecret("THEMES_ARCHIVE_PASSWORD",
                    getImportJob().getThemesAuth().getSecret(),
                    getImportJob().getThemesAuth().getPassword()));
        } else {
            env.add(EnvVarSimple("THEMES_ARCHIVE_URL", "/coremedia/import/themes/frontend.zip"));
        }

        env.addAll(getMySqlEnvVars());

        return env;
    }

    /**
     * Get a list of environment variables to configure the MySQL database connection of the component.
     *
     * @return list of env vars
     */
    public EnvVarSet getMySqlEnvVars() {
        MySQLDetails details = getMySQLDetails("management");
        EnvVarSet env = new EnvVarSet();

        env.add(EnvVarSimple("MYSQL_HOST", details.getHostName())); // needed for MySQL command line tools

        env.add(EnvVarSimple("DEV_MANAGEMENT_JDBC_DRIVER", "com.mysql.cj.jdbc.Driver"));
        env.add(EnvVarSecret("DEV_MANAGEMENT_JDBC_PASSWORD", details.getSecretName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        env.add(EnvVarSimple("DEV_MANAGEMENT_JDBC_SCHEMA", details.getDatabaseSchema()));
        env.add(EnvVarSimple("DEV_MANAGEMENT_JDBC_URL", details.getJdbcUrl()));
        env.add(EnvVarSecret("DEV_MANAGEMENT_JDBC_USER", details.getSecretName(), TargetState.DATABASE_SECRET_USERNAME_KEY));

        details = getMySQLDetails("master");
        env.add(EnvVarSimple("DEV_MASTER_JDBC_DRIVER", "com.mysql.cj.jdbc.Driver"));
        env.add(EnvVarSecret("DEV_MASTER_JDBC_PASSWORD", details.getSecretName(), TargetState.DATABASE_SECRET_PASSWORD_KEY));
        env.add(EnvVarSimple("DEV_MASTER_JDBC_SCHEMA", details.getDatabaseSchema()));
        env.add(EnvVarSimple("DEV_MASTER_JDBC_URL", details.getJdbcUrl()));
        env.add(EnvVarSecret("DEV_MASTER_JDBC_USER", details.getSecretName(), TargetState.DATABASE_SECRET_USERNAME_KEY));
        return env;
    }



    @Override
    public List<Container> getInitContainers() {
        List<Container> containers = super.getInitContainers();

        containers.add(getContainerWaitForIor(getTargetState().getServiceNameFor("workflow-server"), getTargetState().getServiceUrlFor("workflow-server")));
        return containers;
    }

    @Override
    public List<HasMetadata> buildResources() {
        return List.of(buildJob());
    }

    @Override
    public List<Volume> getVolumes() {
        LinkedList<Volume> volumes = new LinkedList<>(super.getVolumes());

        if (!getImportJob().getContentUsersThemesPvc().isBlank()) {
            volumes.add(new VolumeBuilder()
                    .withName(CONTENT_USERS_FRONTEND_VOLUME)
                    .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                            .withClaimName(getImportJob().getContentUsersThemesPvc())
                            .build())
                    .build());
        }
        volumes.add(new VolumeBuilder()
                .withName("coremedia-export")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("coremedia-import")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        volumes.add(new VolumeBuilder()
                .withName("coremedia-tools-properties")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());

        return volumes;
    }

    @Override
    public List<VolumeMount> getVolumeMounts() {
        LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

        if (!getImportJob().getContentUsersThemesPvc().isBlank()) {
            volumeMounts.add(new VolumeMountBuilder()
                    .withName(CONTENT_USERS_FRONTEND_VOLUME)
                    .withMountPath(CONTENT_USERS_FRONTEND_PATH)
                    .build());
        }
        volumeMounts.add(new VolumeMountBuilder()
                .withName("coremedia-export")
                .withMountPath("/coremedia/export")
                .build());
        volumeMounts.add(new VolumeMountBuilder()
                .withName("coremedia-import")
                .withMountPath("/coremedia/import")
                .build());
//        volumeMounts.add(new VolumeMountBuilder()
//                .withName("coremedia-tools-properties")
//                .withMountPath("/coremedia/tools/properties/corem")
//                .build());

        return volumeMounts;
    }

    @Override
    public SecurityContext getSecurityContext() {
        SecurityContext securityContext = super.getSecurityContext();

        securityContext.setReadOnlyRootFilesystem(false); // properties location is a mix of dynamically created files and files from the image
        return securityContext;
    }


}
