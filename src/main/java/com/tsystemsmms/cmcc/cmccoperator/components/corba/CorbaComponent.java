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

import com.tsystemsmms.cmcc.cmccoperator.components.HasService;
import com.tsystemsmms.cmcc.cmccoperator.components.HasUapiClient;
import com.tsystemsmms.cmcc.cmccoperator.components.SpringBootComponent;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;

@Slf4j
public abstract class CorbaComponent extends SpringBootComponent implements HasService, HasUapiClient {

  public static final String PVC_TRANSFORMED_BLOBCACHE = "tx-blob";
  public static final String MOUNT_TRANSFORMED_BLOBCACHE = "/coremedia/cache/persistent-transformed-blobcache";

  public static final String PVC_UAPI_BLOBCACHE = "uapi-blob";
  public static final String MOUNT_UAPI_BLOBCACHE = "/coremedia/cache/uapi-blobcache";

  protected CorbaComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec, String imageRepository) {
    super(kubernetesClient, targetState, componentSpec, imageRepository);
  }

  @Override
  public List<HasMetadata> buildResources() {
    List<HasMetadata> resources = new LinkedList<>();
    resources.add(buildStatefulSet());
    resources.add(buildService());
    if (getCmcc().getSpec().getWith().getJsonLogging()) {
      resources.add(buildLoggingConfigMap());
    }
    return resources;
  }

  @Override
  public void requestRequiredResources() {
    super.requestRequiredResources();
    getUapiClientSecretRef();
  }


  @Override
  public EnvVarSet getEnvVars() {
    EnvVarSet env = super.getEnvVars();

    env.add(EnvVarSimple("SPRING_APPLICATION_NAME", getSpecName()));
    env.add(EnvVarSimple("SPRING_BOOT_EXPLODED_APP", "true"));
    env.add(EnvVarSimple("JAVA_HEAP", ""));
    env.add(EnvVarSimple("JAVA_OPTS", getCmcc().getSpec().getDefaults().getJavaOpts()));
    env.addAll(getUapiClientEnvVars("REPOSITORY"));

    return env;
  }

  @Override
  public Map<String, String> getSpringBootProperties() {
    Map<String, String> properties = super.getSpringBootProperties();

    properties.putAll(Map.of(
            // needed in many applications
            "com.coremedia.transform.blobCache.basePath", MOUNT_TRANSFORMED_BLOBCACHE,
            "com.coremedia.transform.blobCache.size", String.valueOf(getVolumeSizeLimit(ComponentSpec.VolumeSize::getTransformedBlobCache)),
            "repository.blob-cache-path", MOUNT_UAPI_BLOBCACHE,
            "repository.blob-cache-size", String.valueOf(getVolumeSizeLimit(ComponentSpec.VolumeSize::getUapiBlobCache)),
            "repository.heap-cache-size", Integer.toString(128 * 1024 * 1024),
            "repository.url", getTargetState().getServiceUrlFor("content-server", "cms"),
            "management.health.diskspace.path", MOUNT_UAPI_BLOBCACHE
    ));
    return properties;
  }


  public Map<String, String> getSiteMappingProperties() {
    HashMap<String, String> properties = new HashMap<>();
    String preview = Utils.defaultString(getDefaults().getSiteMappingProtocol(), "https://") + getTargetState().getPreviewHostname();

    for (SiteMapping siteMapping : getSpec().getSiteMappings()) {
      properties.put("blueprint.site.mapping." + siteMapping.getPrimarySegment(), preview);
      for (String segment : siteMapping.getAdditionalSegments()) {
        properties.put("blueprint.site.mapping." + segment, preview);
      }
    }

    return properties;
  }


  @Override
  public List<ContainerPort> getContainerPorts() {
    return List.of(
            new ContainerPortBuilder()
                    .withName("ior")
                    .withContainerPort(8080)
                    .build(),
            new ContainerPortBuilder()
                    .withName("management")
                    .withContainerPort(8081)
                    .build(),
            new ContainerPortBuilder()
                    .withName("corba")
                    .withContainerPort(8083)
                    .build()
    );
  }

  @Override
  public List<ServicePort> getServicePorts() {
    return List.of(
            new ServicePortBuilder().withName("ior").withPort(8080).withNewTargetPort("ior").build(),
            new ServicePortBuilder().withName("management").withPort(8081).withNewTargetPort("management").build(),
            new ServicePortBuilder().withName("corba").withPort(8083).withNewTargetPort("corba").build());
  }

  @Override
  public List<Volume> getVolumes() {
    List<Volume> volumes = new LinkedList<>(super.getVolumes());
    volumes.addAll(List.of(
            new VolumeBuilder()
                    .withName("coremedia-var-tmp")
                    .withEmptyDir(new EmptyDirVolumeSource())
                    .build()
    ));

    if (needsTransformedBlobCache() && !getCmcc().getSpec().getWith().getCachesAsPvc()) {
      // only a volume when no PVC is wanted, otherwise a claim will be created (see below)
      volumes.add(new VolumeBuilder()
                      .withName(PVC_TRANSFORMED_BLOBCACHE)
                      .withEmptyDir(new EmptyDirVolumeSource())
                      .build());
    }

    if (needsUapiCache()) {
      volumes.add(new VolumeBuilder()
              .withName(PVC_UAPI_BLOBCACHE)
              .withEmptyDir(new EmptyDirVolumeSource())
              .build());
    }

    return volumes;
  }

  protected boolean needsTransformedBlobCache() {
    return false;
  }

  protected boolean needsUapiCache() {
    return true;
  }

  @Override
  public List<PersistentVolumeClaim> getVolumeClaims() {
    List<PersistentVolumeClaim> claims = super.getVolumeClaims();

    if (needsTransformedBlobCache() && getCmcc().getSpec().getWith().getCachesAsPvc()) {
      claims.add(getPersistentVolumeClaim(PVC_TRANSFORMED_BLOBCACHE, getVolumeSize(ComponentSpec.VolumeSize::getTransformedBlobCache)));
    }
    return claims;
  }

  @Override
  public List<VolumeMount> getVolumeMounts() {
    LinkedList<VolumeMount> volumeMounts = new LinkedList<>(super.getVolumeMounts());

    if (needsTransformedBlobCache()) {
      volumeMounts.add(new VolumeMountBuilder()
              .withName(PVC_TRANSFORMED_BLOBCACHE)
              .withMountPath(MOUNT_TRANSFORMED_BLOBCACHE)
              .build());
    }

    if (needsUapiCache()) {
      volumeMounts.add(new VolumeMountBuilder()
              .withName(PVC_UAPI_BLOBCACHE)
              .withMountPath(MOUNT_UAPI_BLOBCACHE)
              .build());
    }

    return volumeMounts;
  }

  @Override
  public String getServiceUrl() {
    return "http://" + getTargetState().getResourceNameFor(this) + ":8080/ior";
  }
}
