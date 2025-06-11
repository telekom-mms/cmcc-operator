/*
 * Copyright (c) 2023. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.crds;

import io.fabric8.kubernetes.api.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class ComponentSpecTest {
  @Test
  public void copyConstructorTest() {
    ComponentSpec cs = new ComponentSpec();
    cs.setAnnotations(Map.of("foo", "bar"));
    cs.setArgs(List.of("foo", "bar"));
    cs.setEnv(List.of(new EnvVar("foo", "bar", null)));
    cs.setExtra(Map.of("foo", "bar"));
    cs.setImage(new ImageSpec("foo", "bar", "baz", "bay"));
    cs.setKind("foo");
    cs.setMilestone(Milestone.DeploymentStarted);
    cs.setName("foo");
    cs.setPodSecurityContext(new PodSecurityContextBuilder()
            .withRunAsUser(1000L)
            .build());
    cs.setResources(new ResourceMgmt(Map.of("foo", "bar"), Map.of("fu", "baz")));
    cs.setSchemas(Map.of("foo", "bar"));
    cs.setSecurityContext(new SecurityContextBuilder()
                    .withRunAsUser(1000L)
            .build());
    cs.setType("foo");
    cs.setVolumeSize(new ComponentSpec.VolumeSize("1234"));

    ComponentSpec dut = new ComponentSpec(cs);

    assertEquals(cs, dut);
  }

  @Test
  public void updateTest() {
    ComponentSpec cs = new ComponentSpec();
    cs.setAnnotations(Map.of("annotation", "bar"));
    cs.setArgs(List.of("arg", "bar"));
    cs.setEnv(List.of(new EnvVar("env", "bar", null)));
    cs.setExtra(Map.of("extra", "bar"));
    cs.setImage(new ImageSpec("foo", "bar", "baz", "bay"));
    cs.setKind("foo");
    cs.setMilestone(Milestone.DeploymentStarted);
    cs.setName("foo");
    cs.setPodSecurityContext(new PodSecurityContextBuilder()
            .withRunAsUser(1000L)
            .build());
    cs.setResources(new ResourceMgmt(Map.of("foo", "bar"), Map.of("fu", "baz")));
    cs.setSchemas(Map.of("foo", "bar"));
    cs.setSecurityContext(new SecurityContextBuilder()
            .withRunAsUser(1000L)
            .build());
    cs.setType("foo");
    cs.setVolumeSize(new ComponentSpec.VolumeSize("1234"));

    ComponentSpec dut = new ComponentSpec();

    dut.update(cs);

    assertEquals(cs.getAnnotations(), dut.getAnnotations(), "annotations copied");
    assertEquals(cs.getArgs(), dut.getArgs(), "args copied");
    assertEquals(cs.getEnv(), dut.getEnv(), "env copied");
    assertEquals(cs.getExtra(), dut.getExtra(), "extra copied");
    assertEquals(cs.getImage(), dut.getImage(), "image copied");
    assertEquals("", dut.getKind(), "kind not set");
    assertEquals(cs.getMilestone(), dut.getMilestone(), "milestone copied");
    assertEquals("", dut.getName(), "name not set");
    assertEquals(cs.getPodSecurityContext(), dut.getPodSecurityContext(), "podSecurityContext copied");
    assertEquals(cs.getResources(), dut.getResources(), "resources copied");
    assertEquals(cs.getSchemas(), dut.getSchemas(), "schemas copied");
    assertEquals(cs.getSecurityContext(), dut.getSecurityContext(), "securityContext copied");
    assertNull(dut.getType(), "type not set");
    assertEquals(cs.getVolumeSize(), dut.getVolumeSize(), "volumeSize copied");
  }
}
