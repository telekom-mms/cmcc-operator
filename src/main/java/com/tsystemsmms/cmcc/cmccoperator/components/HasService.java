/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.components;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;

import java.util.List;

/**
 * A Component that has a service.
 */
public interface HasService extends Component {
    /**
     * Service for this component.
     *
     * @return the service definition
     */
    default Service buildService() {
        return new ServiceBuilder()
                .withMetadata(getTargetState().getResourceMetadataFor(this))
                .withSpec(new ServiceSpecBuilder()
                        .withSelector(getSelectorLabels())
                        .withPorts(getServicePorts())
                        .build())
                .build();
    }

    /**
     * Returns the list of ports this service offers.
     *
     * @return list of ports
     */
    List<ServicePort> getServicePorts();

    /**
     * Returns the URL for the (primary) service.
     *
     * @return URL of the primary service.
     */
    default String getServiceUrl() {
        return "http://" + getTargetState().getResourceNameFor(this) + ":8080";
    }


}
