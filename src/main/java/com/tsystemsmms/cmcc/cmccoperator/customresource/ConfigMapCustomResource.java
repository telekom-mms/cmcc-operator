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
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;
import java.util.Set;

import static org.springframework.beans.BeanUtils.copyProperties;

@Slf4j
public class ConfigMapCustomResource extends AbstractCustomResource {
    final ConfigMap cm;

    public ConfigMapCustomResource(ConfigMap cm) {
        super(cm);
        this.cm = cm;
    }

    @Override
    public void updateResource() {
        ObjectMapper mapper = new ObjectMapper();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        options.setCanonical(false);
        options.setExplicitStart(false);
        Representer representer = new Representer(options);
        representer.addClassTag(CoreMediaContentCloudSpec.class, Tag.MAP);
        representer.addClassTag(CoreMediaContentCloudStatus.class, Tag.MAP);
        representer.addClassTag(Set.class, Tag.SEQ);
        Yaml yaml = new Yaml(representer);

//        log.debug("spec:\n{}", yaml.dump(mapper.convertValue(spec, Map.class)));
        cm.getData().put("spec", yaml.dump(mapper.convertValue(spec, Map.class)));
        cm.getData().put("status", yaml.dump(mapper.convertValue(status, Map.class)));
    }
}
