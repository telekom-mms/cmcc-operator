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
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.utils.YamlMapper;
import io.fabric8.kubernetes.api.model.ConfigMap;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.beans.BeanUtils.copyProperties;

@Slf4j
public class ConfigMapCustomResource extends AbstractCustomResource {
    final ConfigMap cm;
    final YamlMapper yamlMapper;

    public ConfigMapCustomResource(ConfigMap cm, YamlMapper yamlMapper) {
        super(cm, getSpecFromConfigMap(cm, yamlMapper), getStatusFromConfigMap(cm, yamlMapper));
        this.cm = cm;
        this.yamlMapper = yamlMapper;
    }

    private static CoreMediaContentCloudSpec getSpecFromConfigMap(ConfigMap cm, YamlMapper yamlMapper) {
        String specString = cm.getData().get("spec");
        if (specString == null)
            throw new CustomResourceConfigError("ConfigMap \"" + cm.getMetadata().getName() + "\": property \"spec\" is missing.");
        return yamlMapper.load(specString, CoreMediaContentCloudSpec.class);
    }

    private static CoreMediaContentCloudStatus getStatusFromConfigMap(ConfigMap cm, YamlMapper yamlMapper) {
        return cm.getData().containsKey("status")
                ? yamlMapper.load(cm.getData().get("status"), CoreMediaContentCloudStatus.class)
                : new CoreMediaContentCloudStatus();
    }

    @Override
    public void updateResource() {
        cm.getData().put("spec", yamlMapper.dump(spec));
        cm.getData().put("status", yamlMapper.dump(status));
    }
}
