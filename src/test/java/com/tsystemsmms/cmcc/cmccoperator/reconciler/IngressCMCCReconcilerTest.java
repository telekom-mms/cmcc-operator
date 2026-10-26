/*
 * Copyright (c) 2025 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 * All rights reserved.
 */
package com.tsystemsmms.cmcc.cmccoperator.reconciler;

import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

/**
 * <p>Tests for the with.createNginxIngresses option</p>
 */
@SpringBootTest
@EnableMockOperator
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IngressCMCCReconcilerTest extends AbstractCMCCReconcilerTest {

  private CoreMediaContentCloud cmcc;

  public IngressCMCCReconcilerTest() {
    super("ingresses");
  }

  @Override
  public CoreMediaContentCloud getCmcc() {
    return cmcc;
  }

  @Test
  void testIngressesAreCreated() {
    server.reset();

    cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("ingresses-enabled");
    cmcc.getStatus().setMilestone(Milestone.DeliveryServicesReady);
    this.reconcile();

    assertThat(getIngresses(), is(not(empty())));
  }

  @Test
  void testIngressesAreNotCreatedWhenDisabled() {
    server.reset();

    cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("ingresses-disabled");
    cmcc.getSpec().getWith().setCreateNginxIngresses(false);
    cmcc.getStatus().setMilestone(Milestone.DeliveryServicesReady);
    this.reconcile();

    assertThat(getIngresses(), is(empty()));
  }

  @Test
  void testIngressesAreRemovedWhenDisabled() {
    server.reset();

    cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("ingresses-removed");
    cmcc.getStatus().setMilestone(Milestone.DeliveryServicesReady);
    this.reconcile();
    assertThat(getIngresses(), is(not(empty())));

    cmcc = getCmcc();
    cmcc.getSpec().getWith().setCreateNginxIngresses(false);
    this.reconcile();

    assertThat(getIngresses(), is(empty()));
  }

  private List<Ingress> getIngresses() {
    return kubernetesClient.resources(Ingress.class).inNamespace(namespace).list().getItems();
  }
}
