/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.ImageSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.utils.EnvVarSet;
import io.fabric8.kubernetes.api.model.EnvVar;

import java.util.List;
import java.util.Map;

/**
 * Build a ComponentSpec.
 */
public class ComponentSpecBuilder {
    private final ComponentSpec componentSpec;

    /**
     * Create a builder instance. Marked private so users have to use the factory method.
     */
    private ComponentSpecBuilder() {
        componentSpec = new ComponentSpec();
    }

    /**
     * Create a builder for the given type. Since type is mandatory for each component, this is the only factory method for this builder.
     *
     * @param type of the component
     * @return the builder
     */
    public static ComponentSpecBuilder ofType(String type) {
        ComponentSpecBuilder csb = new ComponentSpecBuilder();
        csb.componentSpec.setType(type);
        return csb;
    }

    /**
     * Returns the built component spec.
     *
     * @return component spec
     */
    public ComponentSpec build() {
        return componentSpec;
    }

    /**
     * Specify the name field.
     *
     * @param name the name
     * @return the builder
     */
    public ComponentSpecBuilder withName(String name) {
        componentSpec.setName(name);
        return this;
    }

    /**
     * Specify the kind field.
     *
     * @param kind the kind
     * @return the builder
     */
    public ComponentSpecBuilder withKind(String kind) {
        componentSpec.setKind(kind);
        return this;
    }

    /**
     * Specify the image field.
     *
     * @param image the image spec
     * @return the builder
     */
    public ComponentSpecBuilder withExtra(Map<String, String> extra) {
        componentSpec.setExtra(extra);
        return this;
    }

    /**
     * Specify the image field.
     *
     * @param image the image spec
     * @return the builder
     */
    public ComponentSpecBuilder withImage(ImageSpec image) {
        componentSpec.setImage(image);
        return this;
    }

    /**
     * Specify the name field.
     *
     * @param milestone milestone
     * @return the builder
     */
    public ComponentSpecBuilder withMilestone(Milestone milestone) {
        componentSpec.setMilestone(milestone);
        return this;
    }

    /**
     * Specify the name field.
     *
     * @param env the list of environment variables
     * @return the builder
     */
    public ComponentSpecBuilder withEnv(List<EnvVar> env) {
        componentSpec.setEnv(env);
        return this;
    }

    /**
     * Specify the name field.
     *
     * @param args the list of arguments
     * @return the builder
     */
    public ComponentSpecBuilder withArgs(List<String> args) {
        componentSpec.setArgs(args);
        return this;
    }
}
