/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;
import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @Test
    void concatOptionalTest() {
        assertEquals("", concatOptional(Collections.emptyList()), "empty list -> empty string");
        assertEquals("one", concatOptional(List.of("one")), "list(one) -> one");
        assertEquals("one-two", concatOptional(List.of("one", "two")), "list(one, two) -> one-two");
        assertEquals("one-two-three", concatOptional(List.of("one", "two", "three")), "list(one, two, three) -> one-two-three");
    }

    @Test
    void concatOptionalNullTest() {
        assertEquals("two-three", concatOptional(null, "two", "three"), "list(null, two, three) -> two-three");
        assertEquals("one-three", concatOptional("one", null, "three"), "list(one, null, three) -> one-three");
        assertEquals("one-two", concatOptional("one", "two", null), "list(one, two, null) -> one-two");

        assertEquals("two-three", concatOptional(List.of("", "two", "three")), "list('', two, three) -> two-three");
        assertEquals("one-three", concatOptional(List.of("one", "", "three")), "list(one, '', three) -> one-three");
        assertEquals("one-two", concatOptional(List.of("one", "two", "")), "list(one, two, '') -> one-two");
    }
}