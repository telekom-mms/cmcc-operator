/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.ingress;

import com.tsystemsmms.cmcc.cmccoperator.crds.IngressTls;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedList;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;

/**
 * Implements the URL rewriting for the standard Blueprint link scheme, where the site segment is always the first part
 * of the URL. For example, consider this site mapping:
 * <p>
 * - hostname: corporate
 * primarySegment: corporate
 * additionalSegments:
 * - corporate-de-de
 * - corporate-en-ca
 * - corporate-en-gb
 * <p>
 * The resulting rewrite rules for the Ingress serving the host corporate simply prepend /blueprint/servlet to the URL.
 */
@Slf4j
public class HeadlessUrlMappingBuilder extends AbstractUrlMappingBuilder {

  public HeadlessUrlMappingBuilder(IngressBuilderFactory ingressBuilderFactory, TargetState targetState, String serviceName) {
    super(ingressBuilderFactory, targetState, serviceName);
  }
  @Override
  public Collection<? extends HasMetadata> buildPreviewResources() {
    LinkedList<HasMetadata> ingresses = new LinkedList<>();
    String fqdn = getTargetState().getHeadlessServerPreviewHostname();
    IngressTls tls = targetState.getCmcc().getSpec().getDefaultIngressTls();
    int uploadSize = getInt(getTargetState().getCmcc().getSpec().getWith().getUploadSize().getPreview());
    int responseTimeout = getInt(getTargetState().getCmcc().getSpec().getWith().getResponseTimeout().getPreview());

    ingresses.addAll(ingressBuilderFactory.builder(getTargetState(), previewName("headless"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPattern("/(graphql|graphiql|caas|previewurl|preview)(.*)", serviceName).build());

    return ingresses;
  }

  @Override
  public Collection<? extends HasMetadata> buildLiveResources(SiteMapping siteMapping) {
    LinkedList<HasMetadata> ingresses = new LinkedList<>();
    var fqdn = getTargetState().getHeadlessServerLiveHostname();
    var spec = getTargetState().getCmcc().getSpec();
    var tls = spec.getDefaultIngressTls();
    var uploadSize = getInt(spec.getWith().getUploadSize().getLive());
    var responseTimeout = getInt(spec.getWith().getResponseTimeout().getLive());

    ingresses.addAll(ingressBuilderFactory.builder(getTargetState(), liveName(null,"headless"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPattern("/(graphql|caas)(.*)", serviceName).build());

    return ingresses;
  }

  @Override
  public String buildPreviewUrl(SiteMapping siteMapping, String segment) {
    return "https://" + getTargetState().getHeadlessServerPreviewHostname() + "/" + "graphiql";
  }

  @Override
  public String buildLiveUrl(SiteMapping siteMapping, String segment) {
    return "https://" + getTargetState().getHeadlessServerLiveHostname() + "/" + "graphql";
  }
}
