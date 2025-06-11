/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.components.Component;

import java.util.Arrays;
import java.util.LinkedList;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Implements a default way of generating Kubernetes resource names.
 * <p>
 * The name consists of the optional defaults.namePrefix, the optional defaults.nameSuffix, the component name, and any optional parts passed in.
 */
public class DefaultResourceNamingProvider implements ResourceNamingProvider {
    private final TargetState targetState;

    public DefaultResourceNamingProvider(TargetState targetState) {
        this.targetState = targetState;
    }

    @Override
    public String nameFor(Component component, String... additional) {
        return nameFor(component.getBaseResourceName(), additional);
    }

    @Override
    public String nameFor(String component, String... additional) {
        LinkedList<String> args = new LinkedList<>();
        args.add(targetState.getCmcc().getSpec().getDefaults().getNamePrefix());
        args.add(component);
        args.add(targetState.getCmcc().getSpec().getDefaults().getNameSuffix());
        if (additional != null && additional.length > 0) {
            args.addAll(Arrays.asList(additional));
        }
        return concatOptional(args);
    }
}
