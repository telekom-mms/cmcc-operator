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

/**
 * A component was requested but not found.
 */
public class NoSuchComponentException extends CustomResourceConfigError {
    String name;

    public NoSuchComponentException() {
        super("Unable to find a component");
    }

    public NoSuchComponentException(String name) {
        super("No component \"" + name + "\" has been defined");
        this.name = name;
    }

    public NoSuchComponentException(String name, String message) {
        super(message);
        this.name = name;
    }

    public NoSuchComponentException(String name, Throwable cause) {
        super("No component \"" + name + "\" has been defined", cause);
        this.name = name;
    }

    public NoSuchComponentException(String name, String message, Throwable cause) {
        super(message, cause);
        this.name = name;
    }
}
