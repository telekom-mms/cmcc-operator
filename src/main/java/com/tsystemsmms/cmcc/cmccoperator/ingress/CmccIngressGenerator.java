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
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.Collection;

/**
 * Generate ingress resources suitable for mapping requests to a CAE.
 */
public interface CmccIngressGenerator {
    /**
     * Build the ingress resources for the live CAEs. Can build additional resources if necessary.
     *
     * @return collection of Kubernetes resources.
     */
    Collection<? extends HasMetadata> buildLiveResources();

    /**
     * Returns the absolute URL for the given segment for the live CAE. This is the same function that the CAE link
     * builder performs.
     *
     * @param segment site segment which URL should be computed
     * @return absolute URL for that site
     */
    String buildLiveUrl(SiteMapping siteMapping, String segment);

    /**
     * Build the ingress resources for the preview CAE. Can build additional resources if necessary.
     *
     * @return collection of Kubernetes resources.
     */
    Collection<? extends HasMetadata> buildPreviewResources();

    /**
     * Returns the absolute URL for the given segment for the preview CAE. This is the same function that the CAE link
     * builder performs.
     *
     * @param segment site segment which URL should be computed
     * @return absolute URL for that site
     */
    String buildPreviewUrl(SiteMapping siteMapping, String segment);

    /**
     * Build the ingress resources for the Studio. Can build additional resources if necessary.
     *
     * @return collection of Kubernetes resources.
     */
    Collection<? extends HasMetadata> buildStudioResources();

    /**
     * Generate a generic ingress for additional components like overview.
     *
     * @param name     name for the resource
     * @param hostname hostname for the ingress
     * @return an ingress builder
     */
    IngressBuilder builder(String name, String hostname);
}
