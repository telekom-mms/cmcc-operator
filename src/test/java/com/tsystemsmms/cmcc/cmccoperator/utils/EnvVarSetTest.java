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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.EnvVarSimple;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class EnvVarSetTest {

    @Test
    public void jacksonTest() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        EnvVarSet dut = new EnvVarSet();
        dut.add(EnvVarSimple("bar", "456"));
        dut.add(EnvVarSimple("foo", "123"));

        assertEquals("[{\"name\":\"bar\",\"value\":\"456\"},{\"name\":\"foo\",\"value\":\"123\"}]", mapper.writeValueAsString(dut));

        List<Map<String, String>> asList = List.of(
                Map.of("name", "bar",
                        "value", "456"),
                Map.of("name", "foo",
                        "value", "123"));
        assertEquals(asList, mapper.convertValue(dut, List.class));
    }
}
