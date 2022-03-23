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

import com.tsystemsmms.cmcc.cmccoperator.crds.SiteMapping;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Implements the URL rewriting for a customized link scheme, where the URL starts with the language, and the site
 * segment is determined by the hostname and the language identifier. For example, consider this site mapping:
 *
 * <pre>
 *     - hostname: corporate.example.de
 *       primarySegment: corporate-de-de
 *     - hostname: corporate.example.ca
 *       primarySegment: corporate-en-ca
 *       additionalSegments:
 *         - corporate-fr-ca
 * </pre>
 * <p>
 * The resulting rewrite rules build these mappings:
 * <ul>
 *     <li>https://corporate.example.de/ redirects to /de/</li>
 *     <li>https://corporate.example.de/de maps to corporate-de-de</li>
 *     <li>https://corporate.example.ca/ redirects to /en</li>
 *     <li>https://corporate.example.ca/en maps to corporate-en-ca</li>
 *     <li>https://corporate.example.ca/fr maps corporate-en-fr</li>
 * </ul>
 * <p>
 * Note that the CAE needs to be modified to generate links like these.
 * <p>
 * The site segment has to follow a specific format for this to work correctly: the last two parts (separated by dash)
 * need to be the language and the locale, respectively. For example, fr-ca for French in Canada.
 */
@Slf4j
public class OnlyLangCmccIngressGenerator extends AbstractCmccIngressGenerator {

    public OnlyLangCmccIngressGenerator(IngressBuilderFactory ingressBuilderFactory, TargetState targetState, String serviceName) {
        super(ingressBuilderFactory, targetState, serviceName);
    }

    @Override
    public Collection<? extends HasMetadata> buildLiveResources() {
        LinkedList<HasMetadata> ingresses = new LinkedList<>();

        for (SiteMapping siteMapping : targetState.getCmcc().getSpec().getSiteMappings()) {
            String site = siteMapping.getHostname();
            String fqdn = concatOptional(getDefaults().getNamePrefix(), site) + "." + getDefaults().getIngressDomain();
            Set<String> segments = new TreeSet<>(siteMapping.getAdditionalSegments());
            segments.add(siteMapping.getPrimarySegment());
            String languagePattern = segments.stream().map(this::getLanguage).collect(Collectors.joining("|"));

            if (!siteMapping.getFqdn().isBlank())
                fqdn = siteMapping.getFqdn();


            ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "home"), fqdn)
                    .pathExact("/", serviceName).redirect("/" + getLanguage(siteMapping.getPrimarySegment())).build());
            ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "blueprint"), fqdn)
                    .pathPrefix("/blueprint", serviceName).build());
            ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "all"), fqdn)
                    .pathPattern("/(.*)", serviceName).rewrite("/blueprint/servlet/$1").build());
            ingresses.addAll(ingressBuilderFactory.builder(targetState, liveName(site, "language"), fqdn)
                    .pathPattern("/(" + languagePattern + ")(.*)", serviceName).rewrite("/blueprint/servlet/" + getReplacement(siteMapping.getPrimarySegment()) + "$2").build());
        }

        return ingresses;
    }

    private String getLanguage(String segment) {
        String[] parts = segment.split("-");
        if (parts.length < 3)
            throw new CustomResourceConfigError("Segment \"" + segment + "\" in site mapping is too short, needs to have at least three parts separated by -");
        return parts[parts.length - 2];
    }

    private String getReplacement(String segment) {
        String[] parts = segment.split("-");
        if (parts.length < 3)
            throw new CustomResourceConfigError("Segment \"" + segment + "\" in site mapping is too short, needs to have at least three parts separated by -");
        parts[parts.length - 2] = "$1";
        return String.join("-", parts);
    }
}
