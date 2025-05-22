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

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentDefaults;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.IngressTls;
import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.stream.Stream;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;

@Slf4j
public abstract class AbstractUrlMappingBuilder implements UrlMappingBuilder {
  final IngressBuilderFactory ingressBuilderFactory;
  @Getter
  final TargetState targetState;
  final String serviceName;

  public AbstractUrlMappingBuilder(IngressBuilderFactory ingressBuilderFactory, TargetState targetState, String serviceName) {
    this.ingressBuilderFactory = ingressBuilderFactory;
    this.targetState = targetState;
    this.serviceName = serviceName;
  }

  @Override
  public IngressBuilder builder(String name, String hostname) {
    IngressTls tls = targetState.getCmcc().getSpec().getDefaultIngressTls();

    return ingressBuilderFactory.builder(targetState, name, targetState.getHostname(hostname), tls);
  }

  public ComponentDefaults getDefaults() {
    return getTargetState().getCmcc().getSpec().getDefaults();
  }

  public CoreMediaContentCloudSpec getSpec() {
    return getTargetState().getCmcc().getSpec();
  }

  public String liveName(String site, String name, String... more) {
    return concatOptional(Stream.concat(Stream.of(getDefaults().getNamePrefix(), "live", getDefaults().getNameSuffix(), site, name), Arrays.stream(more)).toList());
  }

  public String previewName(String name) {
    return concatOptional(getDefaults().getNamePrefix(), "preview", getDefaults().getNameSuffix(), name);
  }


  @Override
  public Collection<? extends HasMetadata> buildPreviewResources() {
    LinkedList<HasMetadata> ingresses = new LinkedList<>();
    String fqdn = getTargetState().getPreviewHostname();
    SiteMapping siteMapping = getSpec().getSiteMappings().stream().findAny().orElseThrow();

    IngressTls tls = targetState.getCmcc().getSpec().getDefaultIngressTls();
    int uploadSize = getInt(getTargetState().getCmcc().getSpec().getWith().getUploadSize().getPreview());
    int responseTimeout = getInt(getTargetState().getCmcc().getSpec().getWith().getResponseTimeout().getPreview());

    if (StringUtils.isEmpty(siteMapping.getPrimarySegment()) || !siteMapping.getPrimarySegmentRedirect()) {
      log.debug("Skipping home page root ingress (primary segment: {})", siteMapping.getPrimarySegment());
    } else {
      ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("home"), fqdn, tls)
              .responseTimeout(responseTimeout)
              .uploadSize(uploadSize)
              .pathExact("/", serviceName).redirect("/" + siteMapping.getPrimarySegment()).build());
    }
    ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("blueprint"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPrefix("/blueprint", serviceName).build());
    ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("all"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPattern("/(.*)", serviceName).rewrite("/blueprint/servlet/$1").build());
    ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("static"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPattern("/(public|resources|static)(.*)", serviceName).rewrite("/blueprint/$1$2").build());
    ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("seo"), fqdn, tls)
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .pathPattern("/(robots\\.txt|sitemap.*\\.xml)", serviceName).rewrite(getTargetState().getCmcc().getSpec().getWith().getIngressSeoHandler() + "/preview/$1").build());
    return ingresses;
  }

  @Override
  public String buildPreviewUrl(SiteMapping siteMapping, String segment) {
    return "https://" + getTargetState().getPreviewHostname() + "/" + segment;
  }

  @Override
  public Collection<? extends HasMetadata> buildStudioResources() {
    LinkedList<HasMetadata> ingresses = new LinkedList<>();

    IngressTls tls = targetState.getCmcc().getSpec().getDefaultIngressTls();
    int responseTimeout = getInt(getTargetState().getCmcc().getSpec().getWith().getResponseTimeout().getPreview());
    int uploadSize = getInt(getTargetState().getCmcc().getSpec().getWith().getUploadSize().getStudio());

    ingresses.addAll(ingressBuilderFactory.builder(targetState, concatOptional(getDefaults().getNamePrefix(), "studio", getDefaults().getNameSuffix()), getTargetState().getStudioHostname(), tls)
            .pathPrefix("/", getTargetState().getServiceNameFor("studio-client"))
            .pathPrefix("/api", serviceName)
            .pathPrefix("/login", serviceName)
            .pathPrefix("/logout", serviceName)
            .pathPattern("/cspInfo.html", serviceName) // needs to be ImplemetationSpecific: "." is not allowed on prefix and exact
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .build());

    ingresses.addAll(ingressBuilderFactory.builder(targetState, concatOptional(getDefaults().getNamePrefix(), "studio-rest", getDefaults().getNameSuffix()), getTargetState().getStudioHostname(), tls)
            .pathPattern("/rest/(.*)", serviceName).rewrite("/$1")
            .responseTimeout(responseTimeout)
            .uploadSize(uploadSize)
            .build());

    return ingresses;
  }
}
