/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Helm convert from Yaml to objects and vice versa.
 *
 * This class is using both SnakeYaml and Jackson. SnakeYaml has some eccentric functionality that does not work well
 * with sets, for example, when converting from Yaml to a bean. We fix this by first having SnakeYaml parse into
 * generic Maps, Lists, etc., then using Jackson to convert from that to the target class.
 */
public class YamlMapper {
    private final Yaml yaml;
    ObjectMapper mapper = new ObjectMapper();

    public YamlMapper() {
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
        yaml = new Yaml(representer, options);
    }

    public <T> T load(String string, Class<T> clazz) {
        return mapper.convertValue(yaml.load(string), clazz);
    }

    public <T> T load(String string, TypeReference<T> type) {
        return mapper.convertValue(yaml.load(string), type);
    }

    public <T> T load(String string, TypeReference<T> type, Supplier<String> error) {
        try {
            return mapper.convertValue(yaml.load(string), type);
        } catch (RuntimeException e) {
            throw new CustomResourceConfigError(error.get(), e);
        }
    }

    public String dump(Object value) {
        return yaml.dump(mapper.convertValue(value, Map.class));
    }
}
