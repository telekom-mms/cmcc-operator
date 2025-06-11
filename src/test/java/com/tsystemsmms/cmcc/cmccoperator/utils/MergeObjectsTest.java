/*
 * Copyright (c) 2023. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MergeObjectsTest {
  @Test
  public void deepMergeSecurityContextTest() throws JsonProcessingException {
    SecurityContext main = new SecurityContextBuilder()
            .withAllowPrivilegeEscalation(false)
            .withCapabilities(new CapabilitiesBuilder()
                    .addToAdd("foo")
                    .addToAdd("goo")
                    .build())
            .withRunAsUser(1000L)
            .build();
    SecurityContext addl = new SecurityContextBuilder()
            .withAllowPrivilegeEscalation(true)
            .withCapabilities(new CapabilitiesBuilder()
                    .addToAdd("foo")
                    .addToAdd("bar")
                    .build())
            .withRunAsGroup(2000L)
            .build();

    main = Utils.mergeObjects(SecurityContext.class, main, addl);

    assertEquals(main.getRunAsGroup(), 2000L, "runAsGroup has not been merged");
    assertEquals(main.getRunAsUser(), 1000L, "runAsUser has not been merged");
    assertEquals(main.getAllowPrivilegeEscalation(), true, "sllowPrivilegeEscalation has not been merged");
    assertNull(main.getRunAsNonRoot(), "runAsNonRoot has not been set");
    assertEquals(main.getCapabilities().getAdd().size(), 2, "capabilities.add has been replaced");
    assertEquals(main.getCapabilities().getAdd().get(0).equals("foo"), true, "capabilities.add has been replaced");
  }
}
