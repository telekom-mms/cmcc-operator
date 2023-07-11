/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ImageSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ResourceMgmt;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Component {
  /**
   * Create Kubernetes resources for this component
   *
   * @return list of resources
   */
  List<HasMetadata> buildResources();

  /**
   * The base name to be used when building a resource for this component. The target state will use this name
   * combined with additional information to compute the complete resource name.
   *
   * @return the name
   * @see TargetState#getResourceNameFor(Component, String...)
   */
  String getBaseResourceName();

  /**
   * The name of the component.
   *
   * @return the name
   */
  String getSpecName();

  /**
   * The specification this component was built from.
   *
   * @return the specification
   */
  ComponentSpec getComponentSpec();

  /**
   * The default image to use for this component.
   *
   * @return the specification
   */
  default ImageSpec getDefaultImage() {
    return new ImageSpec();
  }

  /**
   * Returns the resource management for this component.
   *
   * @return resource management settings
   */
  ResourceMgmt getResourceManagement();

  /**
   * Returns a name suitable for a resource built from this component.
   *
   * @return a name.
   */
  default ObjectMeta getResourceMetadata() {
    return getResourceMetadataForName(getTargetState().getResourceNameFor(this));
  }

  /**
   * Returns a name suitable for a resource built from the components name and the given name.
   *
   * @param name additional name
   * @return compound name
   */
  default ObjectMeta getResourceMetadataForName(String name) {
    ObjectMeta metadata = getTargetState().getResourceMetadataFor(name);
    metadata.getLabels().putAll(getSelectorLabels());
    return metadata;
  }

  /**
   * Returns the map of schema names (JDBC, MongoDB, UAPI) defined for this component.
   *
   * @return map of schema names
   */
  Map<String, String> getSchemas();

  /**
   * The specification this component was built from.
   *
   * @return the specification
   */
  Map<String, String> getSelectorLabels();

  /**
   * Returns the target state this component is built from. Typically injected when the component is created.
   *
   * @return target state
   */
  TargetState getTargetState();

  /**
   * Should the resources of this component be built in this control loop, or should they be deleted?
   *
   * @return true if resources should be built.
   */
  boolean isBuildResources();

  /**
   * Has this component started successfully? The target state might use this to decided when to move on to the next
   * milestone.
   * <p>
   * If the component is not active at the current milestone, return Optional.empty();
   *
   * @return true if component has started successfully
   */
  Optional<Boolean> isReady();

  /**
   * Called by the target state to trigger any callbacks to get references to required resource, like
   * ClientSecretRefs. Called after configuration of the component, but before buildResources().
   */
  void requestRequiredResources();

  /**
   * Update the component spec. Re-computes any derived values. type, kind and name are immutable and cannot be changed.
   *
   * @param componentSpec The component spec
   * @return the updated component
   */
  Component updateComponentSpec(ComponentSpec componentSpec);
}
