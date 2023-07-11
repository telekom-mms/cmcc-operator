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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImageSpecTest {
  @Test
  public void when_name_given() {
    ImageSpec dut = new ImageSpec("curl");

    assertEquals("", dut.getRegistry(), "registry is empty");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("", dut.getTag(), "tag is empty");
  }

  @Test
  public void when_name_and_tag_given() {
    ImageSpec dut = new ImageSpec("curl:1.2.3");

    assertEquals("", dut.getRegistry(), "registry is empty");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("1.2.3", dut.getTag(), "tag is 1.2.3");
  }

  @Test
  public void when_name_with_path_given() {
    ImageSpec dut = new ImageSpec("library/curl");

    assertEquals("library", dut.getRegistry(), "registry is library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("", dut.getTag(), "tag is empty");
  }

  @Test
  public void when_name_with_path_and_tag_given() {
    ImageSpec dut = new ImageSpec("library/curl:1.2.3");

    assertEquals("library", dut.getRegistry(), "registry is library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("1.2.3", dut.getTag(), "tag is 1.2.3");
  }

  @Test
  public void when_registry_and_name_with_path_given() {
    ImageSpec dut = new ImageSpec("docker.io/library/curl");

    assertEquals("docker.io/library", dut.getRegistry(), "registry is docker.io/library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("", dut.getTag(), "tag is empty");
  }

  @Test
  public void when_registry_and_name_with_path_and_ag_given() {
    ImageSpec dut = new ImageSpec("docker.io/library/curl:1.2.3");

    assertEquals("docker.io/library", dut.getRegistry(), "registry is docker.io/library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("1.2.3", dut.getTag(), "tag is 1.2.3");
  }

  @Test
  public void when_registry_with_port_and_name_with_path_given() {
    ImageSpec dut = new ImageSpec("docker.io:443/library/curl");

    assertEquals("docker.io:443/library", dut.getRegistry(), "registry is docker.io:443/library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("", dut.getTag(), "tag is empty");
  }

  @Test
  public void when_registry_with_port_and_name_with_path_and_tag_given() {
    ImageSpec dut = new ImageSpec("docker.io:443/library/curl:1.2.3");

    assertEquals("docker.io:443/library", dut.getRegistry(), "registry is docker.io:443/library");
    assertEquals("curl", dut.getRepository(), "repository is curl");
    assertEquals("1.2.3", dut.getTag(), "tag is 1.2.3");
  }

}
