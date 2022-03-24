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

/**
 * A factory that geneerates an IngressBuilder.
 */
public interface IngressBuilderFactory {
    /**
     * Create a builder for Ingress resources. The factory method requires mandatory parameters.
     *
     * @param metadata The metadata for the generated resources
     * @param name     The base name for the generated resource
     * @param hostname The hostname the ingresses should be generated for
     * @param tls      The TLS settings for this host
     * @return the builder
     */
    IngressBuilder builder(TargetState metadata, String name, String hostname, IngressTls tls);
}
