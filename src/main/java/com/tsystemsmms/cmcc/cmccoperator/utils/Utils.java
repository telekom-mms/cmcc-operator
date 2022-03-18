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
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.ImageSpec;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSource;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Utils {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static EnvVar EnvVarSimple(String name, String value) {
        return new EnvVar(name, value, null);
    }

    public static EnvVar EnvVarSecret(String name, String secretName, String secretKey) {
        EnvVarSource s = new EnvVarSource();
        s.setSecretKeyRef(new SecretKeySelector(secretKey, secretName, false));
        return new EnvVar(name, null, s);
    }

    public static String defaultString(String... values) {
        return Stream.of(values).filter(s -> s != null && !s.isBlank()).findFirst().orElseThrow();
    }

    /**
     * Combine the elements of the list into a string joined by "-". If an element is null or empty, it is skipped.
     *
     * @param strings List of strings to be concatenated
     * @return concatenated string
     */
    public static String concatOptional(String joiner, List<String> strings) {
        StringBuilder sb = new StringBuilder();

        strings = strings.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.toList());
        switch (strings.size()) {
            case 0:
                return "";
            case 1:
                return strings.get(0);
        }
        sb.append(strings.get(0));
        for (String s : strings.subList(1, strings.size())) {
            sb.append(joiner).append(s);
        }
        return sb.toString();
    }

    /**
     * Combine the arguments into a string joined by "-". If an element is null or empty, it is skipped.
     *
     * @param strings strings to be concatenated
     * @return concatenated string
     */
    public static String concatOptional(List<String> strings) {
        return concatOptional("-", strings);
    }

    /**
     * Combine the arguments into a string joined by "-". If an argument is null or empty, it is skipped.
     *
     * @param strings strings to be concatenated
     * @return concatenated string
     */
    public static String concatOptional(String... strings) {
        return concatOptional("-", Arrays.asList(strings));
    }

    /**
     * Combine the arguments into a string joined by a joiner. If an argument is null or empty, it is skipped.
     *
     * @param strings strings to be concatenated
     * @return concatenated string
     */
    public static String concatOptionalWithJoiner(String joiner, String... strings) {
        return concatOptional(joiner, Arrays.asList(strings));
    }

    /**
     * Replace "{}" with parameters. Each placeholder is replaced with the next parameter from the parameter list.
     *
     * @param pattern a pattern containing zero or more placeholders
     * @param params  values the placeholders should replace
     * @return formatted string
     */
    public static String format(String pattern, String... params) {
        StringBuilder r = new StringBuilder();

        String[] parts = pattern.split("\\{}");
        if (parts.length - 1 > params.length) {
            throw new IndexOutOfBoundsException("Number of parameters does not match pattern");
        }
        for (int i = 0; i < parts.length - 1; i++) {
            r.append(parts[i]);
            r.append(params[i]);
        }
        if (parts.length > 0)
            r.append(parts[parts.length - 1]);
        if (pattern.endsWith("{}"))
            r.append(params[params.length - 1]);
        return r.toString();
    }

    /**
     * Generate a deep clone of the object to avoid incidential modification. Under the hood, the object gets
     * serialized with Jackson and the copy created from the JSON. It is therefor necessary for the object to be
     * serializable by Jackson.
     *
     * @param source object to be cloned
     * @param clazz  class of the object
     * @param <T>    class of the object
     * @return a deep clone of the object
     */
    @SneakyThrows
    public static <T> T deepClone(T source, Class<T> clazz) {
        return objectMapper
                .readValue(objectMapper.writeValueAsString(source), clazz);
    }

    public static String encode64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    public static String decode64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    /**
     * Converts an object into a boolean. If the object is null, or its string representation is blank, return the
     * default value. The return value is true if
     * <ul>
     *     <li>the object is a number, and the number is not zero</li>
     *     <li>the objects string representation is "on", "true", or "yes"</li>
     * </ul>
     *
     * @param o   object to be converted
     * @param def default value
     * @return the boolean value
     */
    public static boolean booleanOf(Object o, boolean def) {
        String s;
        long l = 0l;
        if (o == null)
            return def;
        s = o.toString().toLowerCase(Locale.ROOT);
        if (s.isBlank()) {
            return def;
        }
        try {
            l = Long.parseLong(s);
        } catch (NumberFormatException e) {
            // ignore
        }
        return l != 0 || s.equals("on") || s.equals("true") || s.equals("yes");
    }
}
