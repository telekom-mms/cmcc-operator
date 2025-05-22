/*
 * Copyright (c) 2024 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 * All rights reserved.
 */
package com.tsystemsmms.cmcc.cmccoperator;

import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
/**
 * <p>CMCC Operator Application Test</p>
 * <p>@author kpe</p>
 */
@SpringBootTest
@EnableMockOperator
public class CMCCOperatorApplicationTest {

  @Autowired
  KubernetesClient client;

  @Autowired
  ApplicationContext applicationContext;

  @Autowired
  private KubernetesClient kubernetesClient;

  @Test
  void testCrdLoaded() {
    assertThat(applicationContext.getBean(Operator.class), is(notNullValue()));
    assertThat(
            client
                    .apiextensions()
                    .v1()
                    .customResourceDefinitions()
                    .withName("coremediacontentclouds.cmcc.tsystemsmms.com")
                    .get(), is(notNullValue()));
  }

  @Test
  void testCmccCanBeCreated() {
    CoreMediaContentCloud cmcc = createCmcc();
    kubernetesClient.resource(cmcc).create();
    cmcc = kubernetesClient.resources(CoreMediaContentCloud.class).list().getItems().get(0);
    assertThat(cmcc.getMetadata().getName(), is("test-cmcc"));

    kubernetesClient.resource(cmcc).delete();
  }

  private CoreMediaContentCloud createCmcc() {
    var spec = new CoreMediaContentCloudSpec();
    spec.setComment("Test");

    CoreMediaContentCloud cmcc = new CoreMediaContentCloud();
    cmcc.setApiVersion("v2");
    cmcc.setMetadata(new ObjectMetaBuilder().withName("test-cmcc").build());
    cmcc.setSpec(spec);
    return cmcc;
  }
}
