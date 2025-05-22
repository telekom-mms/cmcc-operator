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
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;
import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.getInt;
import static org.apache.commons.lang3.StringUtils.isEmpty;

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
public class BlueprintUrlMappingBuilder extends AbstractUrlMappingBuilder {

  public BlueprintUrlMappingBuilder(IngressBuilderFactory ingressBuilderFactory, TargetState targetState, String serviceName) {
    super(ingressBuilderFactory, targetState, serviceName);
  }

  @Override
  public Collection<? extends HasMetadata> buildLiveResources(SiteMapping siteMapping) {
    LinkedList<HasMetadata> ingresses = new LinkedList<>();
    int responseTimeout = getInt(getTargetState().getCmcc().getSpec().getWith().getResponseTimeout().getLive());
    int uploadSize = getInt(getTargetState().getCmcc().getSpec().getWith().getUploadSize().getLive());

    String site = siteMapping.getHostname();
    IngressTls tls = targetState.getCmcc().getSpec().getDefaultIngressTls();

    List<String> fqdns = new LinkedList<>(List.of(siteMapping.getFqdn()));
    fqdns.addAll(siteMapping.getFqdnAliases());


    for (int i = 0; i < fqdns.size(); i++) {
      var fqdn = fqdns.get(i);
      if (fqdn.isBlank())
        fqdn = concatOptional(
                isEmpty(getDefaults().getNamePrefixForIngressDomain()) ? getDefaults().getNamePrefix() : getDefaults().getNamePrefixForIngressDomain(),
                site,
                isEmpty(getDefaults().getNameSuffixForIngressDomain()) ? getDefaults().getNameSuffix() : getDefaults().getNameSuffixForIngressDomain()
        ) + "." + getDefaults().getIngressDomain();

      if (StringUtils.isEmpty(siteMapping.getPrimarySegment()) || !siteMapping.getPrimarySegmentRedirect()) {
        log.debug("Skipping home page root ingress (primary segment: {})", siteMapping.getPrimarySegment());
      } else {
        ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "home", (i > 0 ? ""+i : "")), fqdn, tls)
                .responseTimeout(responseTimeout)
                .uploadSize(uploadSize)
                .pathExact("/", serviceName)
                .redirect("/" + siteMapping.getPrimarySegment()).build());
      }
      ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "blueprint", (i > 0 ? ""+i : "")), fqdn, tls)
              .responseTimeout(responseTimeout)
              .uploadSize(uploadSize)
              .pathPrefix("/blueprint", serviceName).build());
      ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "all", (i > 0 ? ""+i : "")), fqdn, tls)
              .responseTimeout(responseTimeout)
              .uploadSize(uploadSize)
              .pathPattern("/(.*)", serviceName)
              .rewrite("/blueprint/servlet/$1").build());

      if (StringUtils.isEmpty(siteMapping.getPrimarySegment())) {
        log.debug("Skipping SEO ingress, primary segment empty");
      } else {
        ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "seo", (i > 0 ? ""+i : "")), fqdn, tls)
                .responseTimeout(responseTimeout)
                .uploadSize(uploadSize)
                .pathPattern("/(robots\\.txt|sitemap.*\\.xml)", serviceName)
                .rewrite(getTargetState().getCmcc().getSpec().getWith().getIngressSeoHandler() + "/" + siteMapping.getPrimarySegment()).build());
      }
    }

    return ingresses;
  }

  @Override
  public String buildLiveUrl(SiteMapping siteMapping, String segment) {
    String site = siteMapping.getHostname();
    String fqdn = concatOptional(
            isEmpty(getDefaults().getNamePrefixForIngressDomain()) ? getDefaults().getNamePrefix() : getDefaults().getNamePrefixForIngressDomain(),
            site,
            isEmpty(getDefaults().getNameSuffixForIngressDomain()) ? getDefaults().getNameSuffix() : getDefaults().getNameSuffixForIngressDomain()
    ) + "." + getDefaults().getIngressDomain();

    if (!siteMapping.getFqdn().isBlank())
      fqdn = siteMapping.getFqdn();

    return "https://" + fqdn + "/" + segment;
  }

}
