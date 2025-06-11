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

import org.junit.jupiter.api.Test;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.deepClone;
import static org.junit.jupiter.api.Assertions.*;

class MilestoneTest {
    @Test
    public void nextMilestoneTest() {
        assertEquals(Milestone.DatabasesReady, Milestone.DeploymentStarted.getNext());
        assertEquals(Milestone.Ready, Milestone.Ready.getNext());
        assertEquals(Milestone.Ready, Milestone.RunJob.getNext());
        assertEquals(Milestone.Never, Milestone.Never.getNext());
    }

    @Test
    public void equalsTest() {
        assertSame(Milestone.Ready, Milestone.Ready);
        assertSame(Milestone.Ready, Milestone.RunJob.getNext());
    }

    @Test
    public void jacksonTest() {
        assertSame(deepClone(Milestone.Ready, Milestone.class), Milestone.Ready);
    }
}