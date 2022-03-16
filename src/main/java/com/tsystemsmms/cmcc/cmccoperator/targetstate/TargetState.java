/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.targetstate;

import com.tsystemsmms.cmcc.cmccoperator.components.ComponentCollection;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentDefaults;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Secret;

import java.util.List;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Create kubernetes resources for the target state described by the custom resource.
 */
public interface TargetState {
    String DATABASE_SECRET_USERNAME_KEY = "username";
    String DATABASE_SECRET_PASSWORD_KEY = "password";

    /**
     * Return the CoreMediaContentCloud custom resource this target state is working on.
     *
     * @return cmcc
     */
    CoreMediaContentCloud getCmcc();

    CmccIngressGeneratorFactory getCmccIngressGeneratorFactory();

    /**
     * Returns true if the Content Server is using the initial passwords.
     * <p>
     * When the Content Server first comes up and initializes an empty database, it creates default users with
     * default passwords. This flag can be queried to decide whether to use these default password ("admin"/"admin)
     * or use the usernames and passwords from the respective secrets.
     *
     * @return true if default passwords are active
     */
    boolean isInitialPasswords();

    /**
     * Owner references to be added to created resources.
     *
     * @return the owner reference
     */
    OwnerReference getOutOwnerReference();

    /**
     * Construct the Kubernetes metadata for the given name.
     *
     * @param name resource name
     * @return the metadata
     */
    ObjectMeta getResourceMetadataForName(String name);

    /**
     * Build all k8s resources for the desired target state.
     *
     * @return list of resources
     */
    List<HasMetadata> buildResources();

    /**
     * Returns the component collection used to manage the components of the target state.
     *
     * @return component collection
     */
    ComponentCollection getComponentCollection();

    /**
     * Return the name of the service resource for the named component.
     *
     * @param name component name
     * @return service name
     * @throws IllegalArgumentException if no component can be found, or the component doesn't implement HasService
     */
    default String getServiceNameFor(String name) {
        return getComponentCollection().getServiceNameFor(name);
    }


    /**
     * Return the name of the service resource for the named component.
     *
     * @param name component name
     * @param kind component kind
     * @return service name
     * @throws IllegalArgumentException if no component can be found, or the component doesn't implement HasService
     */
    default String getServiceNameFor(String name, String kind) {
        return getComponentCollection().getServiceNameFor(name, kind);
    }


    /**
     * Return the URL for the primary service for the named component.
     *
     * @param name component name
     * @return service name
     * @throws IllegalArgumentException if no component can be found, or the component doesn't implement HasService
     */
    default String getServiceUrlFor(String name) {
        return getComponentCollection().getHasServiceComponent(name).getServiceUrl();
    }


    /**
     * Return the URL for the primary service for the named component.
     *
     * @param name component name
     * @param kind component kind
     * @return service name
     * @throws IllegalArgumentException if no component can be found, or the component doesn't implement HasService
     */
    default String getServiceUrlFor(String name, String kind) {
        return getComponentCollection().getHasServiceComponent(name, kind).getServiceUrl();
    }

    /**
     * Returns a hostname for the given name. If the name does not contain periods, extend it with the prefix and
     * ingress domain; otherwise return it unchanged.
     *
     * @param name the name
     * @return the hostname
     */
    default String getHostname(String name) {
        ComponentDefaults defaults = getCmcc().getSpec().getDefaults();
        String fqdn = name;
        if (!fqdn.contains("."))
            fqdn = concatOptional(defaults.getNamePrefix(), fqdn) + "." + defaults.getIngressDomain();
        return fqdn;
    }

    /**
     * Returns the hostname the Preview is available under from the outside.
     *
     * @return hostname of the Preview.
     */
    default String getPreviewHostname() {
        return getHostname(getCmcc().getSpec().getDefaults().getPreviewHostname());
    }

    /**
     * Returns the hostname the Studio is available under from the outside.
     *
     * @return hostname of the Studio.
     */
    default String getStudioHostname() {
        return getHostname(getCmcc().getSpec().getDefaults().getStudioHostname());
    }

    /**
     * Returns a database secret value, either from an existing secret, or creating new values.
     *
     * @param name   name of the secret
     * @param schema the schema and username to use when creating the new value
     * @return database secret
     */
    DatabaseSecret getDatabaseSecret(String name, String schema);

    Secret buildDatabaseSecret(String name, DatabaseSecret secret);
}
