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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class ComponentCollectionTest {
    @Test
    public void componentReferenceIdentity() {
        ComponentSpec cr = new ComponentSpec();
        ComponentCollection.ComponentReference crr;

        cr.setType("foo");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);

        cr.setType("foo");
        cr.setKind("bar");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);

        cr.setType("foo");
        cr.setKind("bar");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);

        cr.setType("foo");
        cr.setKind("");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);

        cr.setType("foo");
        cr.setKind(null);
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);
    }

    @Test
    public void componentReferenceEquality() {
        ComponentSpec cr = new ComponentSpec();
        ComponentCollection.ComponentReference crr;

        cr.setType("foo");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, new ComponentCollection.ComponentReference(cr));

        cr.setType("foo");
        cr.setKind("bar");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, new ComponentCollection.ComponentReference(cr));

        cr.setType("foo");
        cr.setKind("bar");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);

        cr.setType("foo");
        cr.setKind("");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);

        cr.setType("foo");
        cr.setKind(null);
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertEquals(crr, crr);
    }

    @Test
    public void componentReferenceInequality() {
        ComponentSpec cr = new ComponentSpec();
        ComponentCollection.ComponentReference crr;
        ComponentSpec crn = new ComponentSpec();
        ComponentCollection.ComponentReference crnr;

        cr.setType("foo");
        crr = new ComponentCollection.ComponentReference(cr);
        crnr = new ComponentCollection.ComponentReference(crn);
        assertNotEquals(crr, crnr);

        cr.setType("foo");
        cr.setKind("bar");
        crr = new ComponentCollection.ComponentReference(cr);
        assertNotEquals(crr, crnr);

        cr.setType("foo");
        cr.setKind("bar");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertNotEquals(crr, crnr);

        cr.setType("foo");
        cr.setKind("");
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertNotEquals(crr, crnr);

        cr.setType("foo");
        cr.setKind(null);
        cr.setName("baz");
        crr = new ComponentCollection.ComponentReference(cr);
        assertNotEquals(crr, crnr);
    }
}
