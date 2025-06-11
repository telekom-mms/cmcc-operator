/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.crds;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.tsystemsmms.cmcc.cmccoperator.utils.Utils;
import io.fabric8.kubernetes.api.model.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class ComponentSpec {
  @JsonPropertyDescription("Type of the component")
  private String type;

  @JsonPropertyDescription("Sub-type ('cms' or 'mls', or 'master' and 'replica', or 'preview' and 'live')")
  private String kind = "";

  @JsonPropertyDescription("Name to be used for k8s objects")
  private String name = "";

  @JsonPropertyDescription("Forces to use MLS over RLS when set to true")
  private Boolean forceMls = false;

  @JsonPropertyDescription("Additional annotations")
  private Map<String, String> annotations = new HashMap<>();

  @JsonPropertyDescription("Args for the main pod container")
  private List<String> args = new LinkedList<>();

  @JsonPropertyDescription("Additional environment variables")
  private List<EnvVar> env = new LinkedList<>();

  @JsonPropertyDescription("Extra parameters (depends on component)")
  private Map<String, String> extra = new HashMap<>();

  @JsonPropertyDescription("Image for main pod' main container")
  private ImageSpec image = new ImageSpec();

  @JsonPropertyDescription("Make available with this milestone")
  private Milestone milestone = null;

  @JsonPropertyDescription("Security context for a pod")
  PodSecurityContext podSecurityContext = new PodSecurityContext();

  @JsonPropertyDescription("Resource management (limits, requests)")
  private ResourceMgmt resources;

  @JsonPropertyDescription("Schema names for JDBC, MongoDB, and/or UAPI")
  private Map<String, String> schemas = new HashMap<>();

  @JsonPropertyDescription("Security context for containers in a pod")
  SecurityContext securityContext = new SecurityContext();

  @JsonPropertyDescription("Size of persistent data/cache volumes")
  ComponentSpec.VolumeSize volumeSize = new ComponentSpec.VolumeSize();

  @JsonPropertyDescription("Additional volume mounts for a pod")
  private List<VolumeMount> volumeMounts = new LinkedList<>();

  @JsonPropertyDescription("Additional volumes for a pod")
  private List<Volume> volumes = new LinkedList<>();

  @JsonPropertyDescription("Overriding Affinity rules for a pod")
  private Affinity affinity;

  @JsonPropertyDescription("Health probe timeouts for a pod")
  private Timeouts timeouts = new Timeouts();

  @Data
  public static class Timeouts {
    @JsonPropertyDescription("Timeout in seconds for startup")
    Integer startup = 600;
    @JsonPropertyDescription("Timeout in seconds for liveness")
    Integer live = 200;
    @JsonPropertyDescription("Timeout in seconds for readiness")
    Integer ready = 100;
  }

  public ComponentSpec() {
  }

  /**
   * Construct a ComponentSpec from another.
   *
   * @param that source for the new object
   */
  @SuppressWarnings("CopyConstructorMissesField")
  public ComponentSpec(ComponentSpec that) {
    this.type = that.type;
    this.kind = that.kind;
    this.name = that.name;
    this.update(that);
  }

  public void update(ComponentSpec that) {
    this.setAnnotations(that.getAnnotations());
    this.setForceMls(that.getForceMls());
    this.setArgs(that.getArgs());
    this.getEnv().addAll(that.getEnv());
    this.getExtra().putAll(that.getExtra());
    this.getImage().update(that.getImage());
    if (that.getMilestone() != null)
      this.setMilestone(that.getMilestone());
    if (this.podSecurityContext == null)
      this.podSecurityContext = that.getPodSecurityContext();
    else
      this.podSecurityContext = Utils.mergeObjects(PodSecurityContext.class, this.podSecurityContext, that.getPodSecurityContext());
    if (this.resources == null)
      this.resources = that.getResources();
    else
      this.resources.merge(that.getResources());
    if (this.schemas == null)
      this.schemas = that.getSchemas();
    else
      Utils.mergeMapReplace(this.schemas, that.getSchemas());
    if (this.securityContext == null)
      this.securityContext = that.getSecurityContext();
    else
      this.securityContext = Utils.mergeObjects(SecurityContext.class, this.securityContext, that.getSecurityContext());
    if (that.getVolumeSize().getData() != null)
      this.volumeSize.setData(that.getVolumeSize().getData());
    if (that.getVolumeSize().getMongoDbData() != null)
      this.volumeSize.setMongoDbData(that.getVolumeSize().getMongoDbData());
    if (that.getVolumeSize().getMysqlData() != null)
      this.volumeSize.setMysqlData(that.getVolumeSize().getMysqlData());
    if (that.getVolumeSize().getSolrData() != null)
      this.volumeSize.setSolrData(that.getVolumeSize().getSolrData());
    if (that.getVolumeSize().getTransformedBlobCache() != null)
      this.volumeSize.setTransformedBlobCache(that.getVolumeSize().getTransformedBlobCache());
    if (that.getVolumeSize().getUapiBlobCache() != null)
      this.volumeSize.setUapiBlobCache(that.getVolumeSize().getUapiBlobCache());

    that.getVolumeMounts().forEach(v -> {
      if (this.volumeMounts.stream().noneMatch(tv ->
              StringUtils.equals(tv.getName(),      v.getName()) &&
              StringUtils.equals(tv.getMountPath(), v.getMountPath()) &&
              StringUtils.equals(tv.getSubPath(),   v.getSubPath())))
        this.volumeMounts.add(v);
    });
    that.getVolumes().forEach(v -> {
      if (this.volumes.stream().noneMatch(tv -> tv.getName().equals(v.getName())))
        this.volumes.add(v);
    });
    this.affinity = that.getAffinity();
    this.timeouts = that.getTimeouts();
  }

  @Data
  @AllArgsConstructor
  public static class VolumeSize {
    @JsonPropertyDescription("Size of data volume, in k8s quantity")
    String data;
    @JsonPropertyDescription("DEPRECATED. Size of MongoDb data volume, in k8s quantity")
    String mongoDbData;
    @JsonPropertyDescription("DEPRECATED. Size of MySQL data volume, in k8s quantity")
    String mysqlData;
    @JsonPropertyDescription("DEPRECATED. Size of Solr data volume, in k8s quantity")
    String solrData;
    @JsonPropertyDescription("Size of transformed BLOB cache, in k8s quantity")
    String transformedBlobCache;
    @JsonPropertyDescription("Size of UAPI BLOB cache, in k8s quantity")
    String uapiBlobCache;

    public VolumeSize() {
    }

    public VolumeSize(String size) {
      data = size;
      mongoDbData = size;
      mysqlData = size;
      solrData = size;
      transformedBlobCache = size;
      uapiBlobCache = size;
    }
  }
}
