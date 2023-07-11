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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageSpec {
    static final Pattern IMAGE_NAME_PATTERN = Pattern.compile("(?<registry>([a-zA-Z0-9.-]+(:[0-9]+)/)?([a-zA-Z0-9_.-]+/)*)?(?<repository>[a-zA-Z0-9_.-]+)(:(?<tag>[a-zA-Z0-9._-]+))?");

    @JsonPropertyDescription("Image registry (default 'coremedia')")
    String registry = "";
    @JsonPropertyDescription("Image repository (default differs between components)")
    String repository = "";
    @JsonPropertyDescription("Image tag (default 'latest')")
    String tag = "";
    @JsonPropertyDescription("Image pull policy (default 'IfNotPresent')")
    String pullPolicy = "";

    public ImageSpec(String spec) {
        Matcher m = IMAGE_NAME_PATTERN.matcher(spec);
        if (!m.matches()) {
            throw new CustomResourceConfigError("Unable to parse image specification \"" + spec + "\"");
        }
        if (m.group("registry") != null && m.group("registry").length() > 0)
            this.registry = m.group("registry").substring(0, m.group("registry").length()-1);
        this.repository = m.group("repository");
        this.tag = Objects.requireNonNullElse(m.group("tag"), "");
    }

    public void update(ImageSpec that) {
        if (!that.registry.isBlank())
            this.registry = that.registry;
        if (!that.repository.isBlank())
            this.repository = that.repository;
        if (!that.tag.isBlank())
            this.tag = that.tag;
        if (!that.pullPolicy.isBlank())
            this.pullPolicy = that.pullPolicy;
    }
}
