/*
 * Copyright (c) 2024. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;

@Slf4j
public class HeadlessComponent extends AbstractRenderingCorbaComponent {

  public HeadlessComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
    super(kubernetesClient, targetState, componentSpec, "headless-server");
  }

  @Override
  public Collection<? extends HasMetadata> buildIngressResources() {
    var urlMapperName = getCmcc().getSpec().getDefaults().getHeadlessUrlMapper();
    var factory = getTargetState().getUrlMappingBuilderFactories().get(urlMapperName);
    var generator = factory.instance(getTargetState(), getTargetState().getServiceNameFor(this));

    if (this.getComponentSpec().getKind().equals(KIND_PREVIEW)) {
      return generator.buildPreviewResources();
    }

    if (this.getComponentSpec().getKind().equals(KIND_LIVE)) {
      return generator.buildLiveResources(null);
    }

    return Collections.emptyList();
  }

}
