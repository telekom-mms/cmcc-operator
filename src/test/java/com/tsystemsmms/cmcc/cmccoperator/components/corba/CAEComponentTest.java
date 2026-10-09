/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components.corba;

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentDefaults;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloudStatus;
import com.tsystemsmms.cmcc.cmccoperator.customresource.CrdCustomResource;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CAEComponentTest {

  private CAEComponent componentWith(String heapCacheSize) {
    ComponentSpec componentSpec = new ComponentSpec();
    componentSpec.setType(CAEComponent.TYPE_CAE);
    componentSpec.setKind("preview");
    if (heapCacheSize != null) {
      componentSpec.setHeapCacheSize(heapCacheSize);
    }

    CoreMediaContentCloudSpec cmccSpec = new CoreMediaContentCloudSpec();
    cmccSpec.setDefaults(new ComponentDefaults());
    CoreMediaContentCloud cmcc = new CoreMediaContentCloud(cmccSpec, new CoreMediaContentCloudStatus());
    ObjectMeta metadata = new ObjectMeta();
    metadata.setNamespace("test");
    cmcc.setMetadata(metadata);

    TargetState targetState = mock(TargetState.class);
    doReturn(new CrdCustomResource(cmcc)).when(targetState).getCmcc();
    when(targetState.getServiceUrlFor(anyString(), anyString())).thenReturn("http://content-server:8080/ior");
    when(targetState.getStudioHostname()).thenReturn("studio.example.com");

    return new CAEComponent(mock(KubernetesClient.class), targetState, componentSpec);
  }

  @Test
  public void heapCacheSizeDefaultsTo128MiB() {
    assertEquals("134217728", componentWith(null).getSpringBootProperties().get("repository.heap-cache-size"));
  }

  @Test
  public void heapCacheSizeIsConfigurableWithHeapCacheSize() {
    assertEquals("268435456", componentWith("256Mi").getSpringBootProperties().get("repository.heap-cache-size"));
  }
}
