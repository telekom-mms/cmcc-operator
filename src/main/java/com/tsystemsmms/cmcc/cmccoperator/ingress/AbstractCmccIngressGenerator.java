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
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedList;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

@Slf4j
public abstract class AbstractCmccIngressGenerator implements CmccIngressGenerator {
    final IngressBuilderFactory ingressBuilderFactory;
    @Getter
    final TargetState targetState;
    final String serviceName;

    public AbstractCmccIngressGenerator(IngressBuilderFactory ingressBuilderFactory, TargetState targetState, String serviceName) {
        this.ingressBuilderFactory = ingressBuilderFactory;
        this.targetState = targetState;
        this.serviceName = serviceName;
    }

    @Override
    public IngressBuilder builder(String name, String hostname) {
        return ingressBuilderFactory.builder(targetState, name, targetState.getHostname(hostname));
    }

    public ComponentDefaults getDefaults() {
        return getTargetState().getCmcc().getSpec().getDefaults();
    }

    public CoreMediaContentCloudSpec getSpec() {
        return getTargetState().getCmcc().getSpec();
    }

    public String liveName(String site, String name) {
        return concatOptional(getDefaults().getNamePrefix(), "live", site, name);
    }

    public String previewName(String name) {
        return concatOptional(getDefaults().getNamePrefix(), "preview", name);
    }


    @Override
    public Collection<? extends HasMetadata> buildPreviewResources() {
        LinkedList<HasMetadata> ingresses = new LinkedList<>();

        String fqdn = getTargetState().getPreviewHostname();

        String segment = getSpec().getSiteMappings().stream().findAny().orElseThrow().getPrimarySegment();

        ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("home"), fqdn)
                .pathExact("/", serviceName).redirect("/" + segment).build());
        ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("blueprint"), fqdn)
                .pathPrefix("/blueprint", serviceName).build());
        ingresses.addAll(ingressBuilderFactory.builder(targetState, previewName("all"), fqdn)
                .pathPattern("/(.*)", serviceName).rewrite("/blueprint/servlet/$1").build());
        return ingresses;
    }


    @Override
    public Collection<? extends HasMetadata> buildStudioResources() {
        return ingressBuilderFactory.builder(targetState, concatOptional(getDefaults().getNamePrefix(), "studio"), getTargetState().getStudioHostname())
                .pathPrefix("/", getTargetState().getServiceNameFor("studio-client"))
                .pathPrefix("/api", serviceName)
                .pathPrefix("/login", serviceName)
                .pathPrefix("/logout", serviceName)
                .pathPrefix("/cspInfo.html", serviceName)
                .uploadSize("500m")
                .build();
    }
}
