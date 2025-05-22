/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.customresource;

import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import io.fabric8.kubernetes.api.model.ObjectMeta;

/**
 * Holds the custom resource. We need this abstraction to be able to either use the CRD or a ConfigMap.
 */
public interface CustomResource {

    String getVersion();

    String getApiVersion();

    String getKind();

    ObjectMeta getMetadata();

    CoreMediaContentCloudSpec getSpec();

    CoreMediaContentCloudStatus getStatus();

    void setStatus(CoreMediaContentCloudStatus status);

    void updateResource();
}
