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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.Getter;
import org.checkerframework.checker.units.qual.C;
import org.yaml.snakeyaml.Yaml;

import static org.springframework.beans.BeanUtils.copyProperties;

public abstract class AbstractCustomResource implements CustomResource {
    final HasMetadata resource;
    @Getter
    final CoreMediaContentCloudSpec spec;
    @Getter
    final CoreMediaContentCloudStatus status;

    public AbstractCustomResource(CoreMediaContentCloud cmcc) {
        resource = cmcc;
        spec = cmcc.getSpec();
        status = cmcc.getStatus();
    }

    public AbstractCustomResource(HasMetadata resource, CoreMediaContentCloudSpec spec, CoreMediaContentCloudStatus status) {
        this.resource = resource;
        this.spec = spec;
        this.status = status;
    }


    @Override
    public String getApiVersion() {
        return resource.getApiVersion();
    }

    @Override
    public String getKind() {
        return resource.getKind();
    }

    @Override
    public ObjectMeta getMetadata() {
        return resource.getMetadata();
    }

    @Override
    public void setStatus(CoreMediaContentCloudStatus status) {
        copyProperties(status, this.status);
    }
}
