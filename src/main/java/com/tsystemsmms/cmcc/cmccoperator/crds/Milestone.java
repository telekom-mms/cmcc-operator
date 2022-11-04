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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum Milestone {
    Created,
    DatabasesReady,
    ContentServerInitialized,
    ContentServerReady,
    ManagementReady,
    Ready,
    RunJob,
    Healing,
    Never;

    @JsonIgnore
    private Milestone next = null;

    static {
        Milestone[] values = Milestone.values();
        /*
         * Every milestone advances to the following until we reach Ready. All states advance to the following state,
         * except all states starting with Ready advance to themself.
         */
        for (int i = 0; i < values.length - 1; i++) {
            values[i].next = values[i].compareTo(Ready) >= 0 ? Ready : values[i + 1];
        }
        Never.next = Never;
    }

    public Milestone getNext() {
        return this.next;
    }

    /**
     * Compare two milestones. Null is allowed; null is considered to be smaller than any other value.
     *
     * @param a to compare
     * @param b to compare
     * @return -1 if a is smaller than b; 0 if they are equal, and 1 if a is larger than b
     */
    public static int compareTo(Milestone a, Milestone b) {
        if (a == null)
            return b == null ? 0 : -1;
        return b == null ? -1 : a.compareTo(b);
    }
}
