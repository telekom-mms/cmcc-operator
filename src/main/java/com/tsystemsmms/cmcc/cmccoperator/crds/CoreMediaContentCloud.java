/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.crds;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("cmcc.tsystemsmms.com")
@Kind("CoreMediaContentCloud")
@Version("v2")
@ShortNames({"cmcc", "coremedia"})
public class CoreMediaContentCloud extends CustomResource<CoreMediaContentCloudSpec, CoreMediaContentCloudStatus> implements Namespaced {

    public CoreMediaContentCloud() {
        super();
    }

    public CoreMediaContentCloud(CoreMediaContentCloudSpec spec, CoreMediaContentCloudStatus status) {
        super();
        this.spec = spec;
        this.status = status;
    }

    @Override
    protected CoreMediaContentCloudStatus initStatus() {
        return new CoreMediaContentCloudStatus();
    }
}

