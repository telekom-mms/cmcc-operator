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
import io.fabric8.kubernetes.api.model.EnvVar;
import lombok.Data;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Data
public class ComponentSpec {
    @JsonPropertyDescription("Type of the component")
    private String type;

    @JsonPropertyDescription("Sub-type ('cms' or 'mls', or 'master' and 'replica', or 'preview' and 'live')")
    private String kind = "";

    @JsonPropertyDescription("Name to be used for k8s objects")
    private String name = "";

    @JsonPropertyDescription("Args for the main pod container")
    private List<String> args = new LinkedList<>();

    @JsonPropertyDescription("Additional environment variables")
    private List<EnvVar> env = new LinkedList<>();

    @JsonPropertyDescription("Extra parameters (depends on component)")
    private Map<String, String> extra = new HashMap<>();

    @JsonPropertyDescription("Image for main pod' main container")
    private ImageSpec image = new ImageSpec();

    @JsonPropertyDescription("Make available with this milestone")
    private Milestone milestone = Milestone.ManagementReady;

    public ComponentSpec() {

    }

    public ComponentSpec(ComponentSpec that) {
        this.type = that.type;
        this.kind = that.kind;
        this.name = that.name;
        this.update(that);
    }

    public void update(ComponentSpec that) {
        this.setArgs(that.getArgs());
        this.getEnv().addAll(that.getEnv());
        this.getExtra().putAll(that.getExtra());
        this.getImage().update(that.getImage());
        this.setMilestone(that.getMilestone());

    }
}
