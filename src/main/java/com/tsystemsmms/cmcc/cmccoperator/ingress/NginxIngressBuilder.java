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
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.networking.v1.*;

import java.util.*;

public class NginxIngressBuilder extends AbstractIngressBuilder {
  private final String hostname;
  private final String name;
  private final TargetState targetState;
  private final HashMap<String, String> annotations = new HashMap<>();
  private final HashSet<Path> paths = new HashSet<>();
  private final IngressTls tls;

  public NginxIngressBuilder(TargetState targetState, String name, String hostname, IngressTls tls) {
    this.annotations.putAll(targetState.getCmcc().getSpec().getWith().getIngressAnnotations());
    this.hostname = hostname;
    this.name = name;
    this.targetState = targetState;
    this.tls = tls;
  }

  @Override
  public Collection<? extends HasMetadata> build() {
    ObjectMeta metadata = targetState.getResourceMetadataFor(name);
    metadata.getAnnotations().putAll(annotations);

    List<HTTPIngressPath> httpPaths = paths.stream().map(path -> withPath(new HTTPIngressPathBuilder(), path)
            .withBackend(new IngressBackendBuilder()
                    .withService(new IngressServiceBackendBuilder()
                            .withName(path.getService())
                            .withPort(new ServiceBackendPort("http", null))
                            .build())
                    .build())
            .build()).toList();

    IngressSpecBuilder ingressSpecBuilder = new IngressSpecBuilder()
            .withIngressClassName("nginx")
            .withRules(new IngressRuleBuilder()
                    .withHost(hostname)
                    .withHttp(new HTTPIngressRuleValueBuilder()
                            .withPaths(httpPaths)
                            .build())
                    .build());

    if (tls.isEnabled()) {
      IngressTLS ingressTls = new IngressTLSBuilder()
              .withHosts(hostname)
              .withSecretName(tls.getSecretName())
              .build();
      ingressSpecBuilder.withTls(ingressTls);
    }

    return Collections.singletonList(new io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder()
            .withMetadata(metadata)
            .withSpec(ingressSpecBuilder.build())
            .build());
  }

  @Override
  public IngressBuilder pathExact(String path, String service) {
    paths.add(new Path(path, PathType.EXACT, service));
    return this;
  }

  @Override
  public IngressBuilder pathPattern(String path, String service) {
    annotations.put("nginx.ingress.kubernetes.io/use-regex", "true");
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
  public IngressBuilder redirect(String uri, int code) {
    annotations.put("nginx.ingress.kubernetes.io/permanent-redirect", uri);
    if (code != 301)
      annotations.put("nginx.ingress.kubernetes.io/permanent-redirect-code", String.valueOf(code));
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
        b.withPathType("ImplementationSpecific");
        break;
      default:
        throw new IllegalArgumentException("Unknown path type " + path.getType());
    }

    return b;
  }

  @Override
  public IngressBuilder responseTimeout(int seconds) {
    annotations.put("nginx.ingress.kubernetes.io/proxy-read-timeout", String.valueOf(seconds));
    return this;
  }

  @Override
  public IngressBuilder uploadSize(int size) {
    if (size > 0)
      annotations.put("nginx.ingress.kubernetes.io/proxy-body-size", size + "M");
    return this;
  }
}
