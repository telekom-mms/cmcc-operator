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

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.defaultString;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;

@Slf4j
public class CAEComponent extends AbstractRenderingCorbaComponent {
    public static final String TYPE_CAE = "cae";

    public CAEComponent(KubernetesClient kubernetesClient, TargetState targetState, ComponentSpec componentSpec) {
        super(kubernetesClient, targetState, componentSpec);
    }

    @Override
  public Map<String, String> getSpringBootProperties() {
    Map<String, String> properties = super.getSpringBootProperties();
    properties.put("cae.preview.pbe.studio-url-whitelist[0]", "https://" + getTargetState().getStudioHostname());
    addUploadSizeProperties(properties, getInt(getComponentSpec().getKind().equals(KIND_LIVE)
            ? getSpec().getWith().getUploadSize().getLive()
            : getSpec().getWith().getUploadSize().getPreview()));

    return properties;
  }

  @Override
  public Collection<? extends HasMetadata> buildIngressResources() {
    var defaults = getCmcc().getSpec().getDefaults();
    var kind = this.getComponentSpec().getKind();
    var defaultLiveUrlMapperName = defaultString(defaults.getLiveUrlMapper(), defaults.getManagementUrlMapper());

    return switch (kind) {
      case KIND_PREVIEW -> getTargetState().getManagementUrlMappingBuilderFactory()
              .instance(getTargetState(), getTargetState().getServiceNameFor(this))
              .buildPreviewResources();
      case KIND_LIVE -> getCmcc().getSpec().getSiteMappings().stream().map(
              sm -> {
                var urlMapperName = defaultString(sm.getUrlMapper(), defaultLiveUrlMapperName);
                var factory = getTargetState().getUrlMappingBuilderFactories().get(urlMapperName);
                if (factory == null) {
                  throw new CustomResourceConfigError("Unable to find URL Mapper \"" + urlMapperName + "\" for site mapping \"" + sm + "\"");
                }
                return factory
                        .instance(getTargetState(), getTargetState().getServiceNameFor("cae", "live"))
                        .buildLiveResources(sm);
              })
              .flatMap(Collection::stream)
              .collect(Collectors.toList());
      default -> Collections.emptyList();
    };
  }
}
