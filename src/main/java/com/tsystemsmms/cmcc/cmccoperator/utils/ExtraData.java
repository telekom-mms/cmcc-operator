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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExtraData {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static Map<String,String> toMapStringString(Map<String,Object> mso) {
        final HashMap<String, String> mss = new HashMap<>();
        mso.forEach((key, value) -> mss.put(key, value.toString()));
        return mss;
    }

    public static <T> Optional<T> getObject(Map<String,Object> mso, String key, Class<T> clazz) {
        Object o = mso.get(key);
        if (o == null)
            return Optional.empty();
        if (o instanceof Map) {
            try {
                return Optional.ofNullable(objectMapper.readValue(objectMapper.writeValueAsString(o), clazz));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Unable to parse extra data of type " + o.getClass().getSimpleName(), e);
            }
        } else {
            throw new IllegalArgumentException("Unable to parse extra data of type " + o.getClass().getSimpleName());
        }
    }
}
