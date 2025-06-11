/*
 * Copyright (c) 2024 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 * All rights reserved.
 */
package com.tsystemsmms.cmcc.cmccoperator.reconciler;

import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.utils.HttpResponseAdapter;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedList;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;

/**
 * <p>CMCC Operator Application Test</p>
 * <p>@author kpe</p>
 */
@SpringBootTest
@EnableMockOperator
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MilestonesCMCCReconcilerTest extends AbstractCMCCReconcilerTest {

  private final String[] STS_DATABASES = {"solr-leader", "mysql", "mongodb"};
  private final String[] STS_CONTENT_SERVERS = {"content-management-server", "master-live-server"};
  private final String[] STS_MANAGEMENT = {"cae-feeder-preview", "cae-preview", "content-feeder",
          "elastic-worker", "headless-preview", "studio-client", "studio-server", "user-changes"};
  private final String[] STS_DELIVERY_SERVICES = {"cae-feeder-live", "replication-live-server", "solr-follower"};
  private final String[] STS_DELIVERY = {"cae-live-0", "cae-live-1", "headless-live-0", "headless-live-1", "overview"};
  private final String[][] STS_ALL = {STS_DATABASES, STS_CONTENT_SERVERS, {"workflow-server"} , STS_MANAGEMENT, STS_DELIVERY_SERVICES, STS_DELIVERY};

  private CoreMediaContentCloud cmcc;

  @Autowired
  private KubernetesMockServer server;

  public MilestonesCMCCReconcilerTest() {
    super("milestones");
  }

  @Override
  public CoreMediaContentCloud getCmcc() {
    return cmcc;
  }

  @Test
  void testCmccReachesReadyWithVersion() {
    server.reset();
    setupHttpClientOverrides();

    // prepare
    cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("version-test");
    cmcc.getSpec().setVersion("2025.1");
    cmcc.getStatus().setMilestone(Milestone.DeliveryServicesReady);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.DeliveryServicesReady));

    // act
    this.initStatusOnAllStatefulSets();
    this.scaleStsToSpec(STS_ALL);
    this.reconcile();

    // assert
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.Ready));
    assertThat(getCmcc().getStatus().getCurrentVersion(), is("2025.1"));
    assertThat(getCmcc().getStatus().getTargetVersion(), is(emptyOrNullString()));
  }

  @Test
  void testCmccStartsUpgradeOnNewVersion() {
    server.reset();
    setupHttpClientOverrides();
    
    // prepare
    cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("new-version");
    cmcc.getSpec().setVersion("2025.1");
    cmcc.getStatus().setCurrentVersion("2025.1");
    cmcc.getStatus().setMilestone(Milestone.Ready);
    this.reconcile();
//    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.Healing));

    this.scaleStsToSpec(STS_ALL);
    this.createPod("replication-live-server");
    this.createPod("solr-follower");
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.Ready));

    // prepare shortcut override for execution of "disableReplication" command
    var requests = new LinkedList<String>();
    addHttpRequestOverride(
            (request) -> request.uri().getPath().endsWith("api/v1/namespaces/milestones/pods/replication-live-server-0/exec"),
            new HttpResponseAdapter(101, "\"ACCEPTED\":\"Replicator has been disabled."),
            r -> requests.add(r.uri().toString()));

    addHttpRequestOverride(
            (request) -> request.uri().getPath().endsWith("api/v1/namespaces/milestones/pods/solr-follower-0/exec"),
            new HttpResponseAdapter(101, "\"status\":\"OK\""),
            r -> requests.add(r.uri().toString()));

    // act
    cmcc.getSpec().setVersion("2025.2");
    this.reconcile();

    // assert
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.DeploymentStarted));
    assertThat(getCmcc().getStatus().getCurrentVersion(), is("2025.1"));
    assertThat(getCmcc().getStatus().getTargetVersion(), is("2025.2"));
    // check for correct call of execution of "disableReplication" command
    assertThat(requests, hasItem(containsString("replication-live-server-0/exec")));
    assertThat(requests, hasItem(containsString("solr-follower-0/exec")));
  }
}
