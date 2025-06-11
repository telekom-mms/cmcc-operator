/*
 * Copyright (c) 2024 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 * All rights reserved.
 */
package com.tsystemsmms.cmcc.cmccoperator.reconciler;

import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.utils.HttpResponseAdapter;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * <p>CMCC Operator Application Test</p>
 * <p>@author rool</p>
 */
@SpringBootTest
@EnableMockOperator
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UpgradeCMCCReconcilerTest extends AbstractCMCCReconcilerTest {

  private final String[] STS_DATABASES = {"solr-leader", "mysql", "mongodb"};
  private final String[] STS_CONTENT_SERVERS = {"content-management-server", "master-live-server"};
  private final String[] STS_MANAGEMENT = {"cae-feeder-preview", "cae-preview", "content-feeder",
                                            "elastic-worker", "headless-preview", "studio-client",
                                            "studio-server", "user-changes"};
  private final String[] STS_DELIVERY_SERVICES = {"cae-feeder-live", "replication-live-server", "solr-follower"};
  private final String[] STS_DELIVERY = {"cae-live-0", "cae-live-1", "headless-live-0", "headless-live-1", "overview"};
  private final String[][] STS_ALL = {STS_DATABASES, STS_CONTENT_SERVERS, {"workflow-server"} , STS_MANAGEMENT, STS_DELIVERY_SERVICES, STS_DELIVERY};

  private CoreMediaContentCloud cmcc;

  public UpgradeCMCCReconcilerTest() {
    super("milestones");
  }

  @Override
  public CoreMediaContentCloud getCmcc() {
    return cmcc;
  }

  @Test
  void testCmccReachesMilestones() {
    server.reset();
    setupHttpClientOverrides();

    this.cmcc = createCoreMediaContentCloud();
    cmcc.getMetadata().setName("test-cmcc-reaches-milestones");
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.DeploymentStarted));

    this.initStatusOnAllStatefulSets();

    this.scaleStsToSpec(STS_DATABASES);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.DatabasesReady));

    this.scaleStsToSpec(STS_CONTENT_SERVERS);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.ContentServerInitialized));

    // intercept request for CMS/MLS Restart (workaround as Mock Server does not handle this type of request)
    this.addHttpRequestOverride(r -> "application/json-patch+json".equals(r.getContentType())
                    && r.bodyString().contains("kubectl.kubernetes.io/restartedAt"),
            new HttpResponseAdapter(202),
            r -> scaleSts(extractNameFromUriPath(r), 0) // simulate restart by scaling down to 0
    );
    this.scaleStsToSpec("workflow-server");
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.ContentServerReady));

    // wait for CMS+MLS restart (scale to 0)
    // boot them again
    this.waitForStatefulSet(0, "content-management-server");
    this.waitForStatefulSet(0, "master-live-server");

    this.scaleStsToSpec(STS_CONTENT_SERVERS);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.ContentServerReady));

    this.scaleStsToSpec(STS_MANAGEMENT);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.ManagementReady));

    this.scaleStsToSpec(STS_DELIVERY_SERVICES);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.DeliveryServicesReady));

    this.scaleStsToSpec(STS_DELIVERY);
    this.reconcile();
    assertThat(getCmcc().getStatus().getMilestone(), is(Milestone.Ready));
  }
}

