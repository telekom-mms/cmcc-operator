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

import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.networking.v1.*;

import java.util.*;
import java.util.stream.Collectors;

public class NginxIngressBuilder extends AbstractIngressBuilder {
    private final String hostname;
    private final String name;
    private final TargetState targetState;
    private final HashMap<String,String> annotations = new HashMap<>();
    private final HashSet<Path> paths = new HashSet<>();

    public NginxIngressBuilder(TargetState targetState, String name, String hostname) {
        this.hostname = hostname;
        this.name = name;
        this.targetState = targetState;
    }

    @Override
    public Collection<? extends HasMetadata> build() {
        ObjectMeta metadata = targetState.getResourceMetadataForName(name);
        metadata.getAnnotations().putAll(annotations);

        List<HTTPIngressPath> httpPaths = paths.stream().map(path -> withPath(new HTTPIngressPathBuilder(), path)
                .withBackend(new IngressBackendBuilder()
                        .withService(new IngressServiceBackendBuilder()
                                .withName(path.getService())
                                .withPort(new ServiceBackendPort("http", null))
                                .build())
                        .build())
                .build()).collect(Collectors.toList());

        return  Collections.singletonList(new io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder()
                .withMetadata(metadata)
                .withSpec(new IngressSpecBuilder()
                        .withIngressClassName("nginx")
                        .withTls(new IngressTLSBuilder()
                                .withHosts(hostname)
                                .build())
                        .withRules(new IngressRuleBuilder()
                                .withHost(hostname)
                                .withHttp(new HTTPIngressRuleValueBuilder()
                                        .withPaths(httpPaths)
                                        .build())
                                .build())
                        .build())
                .build());
    }

    @Override
    public IngressBuilder pathExact(String path, String service) {
        paths.add(new Path(path, PathType.EXACT, service));
        return this;
    }

    @Override
    public IngressBuilder pathPattern(String path, String service) {
        paths.add(new Path(path, PathType.PATTERN, service));
        return this;
    }

    @Override
    public IngressBuilder pathPrefix(String path, String service) {
        paths.add(new Path(path, PathType.PREFIX, service));
        return this;
    }

    @Override
    public IngressBuilder redirect(String uri) {
        annotations.put("nginx.ingress.kubernetes.io/app-root", uri);
        return this;
    }

    @Override
    public IngressBuilder rewrite(String pattern) {
        annotations.put("nginx.ingress.kubernetes.io/rewrite-target", pattern);
        return this;
    }

    static HTTPIngressPathBuilder withPath(HTTPIngressPathBuilder b, Path path) {
        switch (path.getType()) {
            case EXACT:
                b.withPath(path.getPattern());
                b.withPathType("Exact");
                break;
            case PREFIX:
                b.withPath(path.getPattern());
                b.withPathType("Prefix");
                break;
            case PATTERN:
                b.withPath(path.getPattern() + "$");
                b.withPathType("Prefix");
                break;
            default:
                throw new IllegalArgumentException("Unknown path type " + path.getType());
        }

        return b;
    }
}
