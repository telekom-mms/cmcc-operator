/*
 * Copyright (c) 2024 T-Systems Multimedia Solutions GmbH
 * Riesaer Str. 5, D-01129 Dresden, Germany
 * All rights reserved.
 */
package com.tsystemsmms.cmcc.cmccoperator.reconciler;

import com.tsystemsmms.cmcc.cmccoperator.utils.NamespaceFilter;
import io.javaoperatorsdk.operator.springboot.starter.test.EnableMockOperator;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * <p>CMCC Operator Application Test</p>
 * <p>@author kpe</p>
 */
@SpringBootTest(properties = {"cmcc.scope.namespace.include=namespace-a,namespace-b", "cmcc.scope.namespace.exclude=bad-guy"})
@EnableMockOperator
public class NamespaceScopedCMCCReconcilerTest extends AbstractCMCCReconcilerTest {

    @Test
  void testNamespaceScoping() {
    // this does not work: operator.getConfigurationService().getConfigurationFor(reconciler).getEffectiveNamespaces();
    var controller = operator.getRegisteredController(reconciler.getClass().getSimpleName()).get();
    var namespacesRegistered = new ArrayList<String>(controller.getConfiguration().getEffectiveNamespaces());
    assertThat(namespacesRegistered, hasItem(equalTo("namespace-a")));
    assertThat(namespacesRegistered, hasItem(equalTo("namespace-b")));
    assertThat(namespacesRegistered, not(hasItem(equalTo("bad-guy"))));
    assertThat(namespacesRegistered, not(hasItem(equalTo("JOSDK_ALL_NAMESPACES"))));

    if (controller.getConfiguration().getInformerConfig().getGenericFilter() instanceof NamespaceFilter filter) {
      assertThat(NamespaceFilter.getNamespaceIncludes(), hasItem(equalTo("namespace-a")));
      assertThat(NamespaceFilter.getNamespaceIncludes(), not(hasItem(equalTo("bad-guy"))));

      assertThat(NamespaceFilter.getNamespaceExcludes(), hasItem(equalTo("bad-guy")));
      assertThat(NamespaceFilter.getNamespaceExcludes(), not(hasItem(equalTo("namespace-a"))));
    } else {
      assertThat("Wrong filter type!", controller.getConfiguration().getInformerConfig().getGenericFilter().getClass(), CoreMatchers.equalTo(NamespaceFilter.class));
    }
  }
}
