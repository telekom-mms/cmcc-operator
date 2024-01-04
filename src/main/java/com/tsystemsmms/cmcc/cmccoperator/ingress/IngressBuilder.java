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

import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.Collection;

/**
 * Build ingress resources with certain attributes.
 */
public interface IngressBuilder {
  /**
   * Generates the Kubernetes resources based on the builder properties.
   *
   * @return Kubernetes resources
   */
  Collection<? extends HasMetadata> build();

  /**
   * Lets the ingress match this exact path.
   *
   * @param path    the path
   * @param service the name of the service requests should be routed to
   * @return the builder
   */
  IngressBuilder pathExact(String path, String service);

  /**
   * Lets the ingress match this regular expression.
   *
   * @param pattern the path
   * @param service the name of the service requests should be routed to
   * @return the builder
   */
  IngressBuilder pathPattern(String pattern, String service);

  /**
   * Lets the ingress match this path prefix.
   *
   * @param path    the path
   * @param service the name of the service requests should be routed to
   * @return the builder
   */
  IngressBuilder pathPrefix(String path, String service);

  /**
   * Redirect the client to the new relative or absolute URL.
   *
   * @param uri the target URI
   * @return the builder
   */
  IngressBuilder redirect(String uri);

  /**
   * Redirect the client to the new relative or absolute URL, using status code {@code code}.
   *
   * @param uri the target URI
   * @param code the HTTP status code for the redirect, for example, 301
   * @return the builder
   */
  IngressBuilder redirect(String uri, int code);

    /**
     * Sets the response timeout for this ingress.
     *
     * @param seconds timeout
     * @return the builder
     */
  IngressBuilder responseTimeout(int seconds);

  /**
   * Rewrite the request path using the given pattern.
   *
   * @param pattern to rewrite to
   * @return the builder
   */
  IngressBuilder rewrite(String pattern);

  /**
   * Set the maximum upload size.
   *
   * @param size in megabytes
   * @return the builder
   */
  IngressBuilder uploadSize(int size);
}
