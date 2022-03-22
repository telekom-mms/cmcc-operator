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

import com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler;
import com.tsystemsmms.cmcc.cmccoperator.components.Component;
import com.tsystemsmms.cmcc.cmccoperator.components.ComponentCollection;
import com.tsystemsmms.cmcc.cmccoperator.crds.ClientSecretRef;
import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentDefaults;
import com.tsystemsmms.cmcc.cmccoperator.crds.CoreMediaContentCloud;
import com.tsystemsmms.cmcc.cmccoperator.ingress.CmccIngressGeneratorFactory;
import io.fabric8.kubernetes.api.model.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.tsystemsmms.cmcc.cmccoperator.utils.Utils.concatOptional;

/**
 * Create kubernetes resources for the target state described by the custom resource.
 */
public interface TargetState {
    String DATABASE_SECRET_USERNAME_KEY = "username";
    String DATABASE_SECRET_PASSWORD_KEY = "password";

    /**
     * Build all k8s resources for the desired target state.
     *
     * @return list of resources
     */
    List<HasMetadata> buildResources();

    /**
     * Fill in the secret in the ClientSecret. Either load the secret from the cluster, or generate a secret from the
     * given maps.
     *
     * @param cs      the secret reference
     * @param entries for the secret, will be base64 encoded
     */
    default void loadOrBuildSecret(ClientSecret cs, Map<String, String> entries) {
        Secret secret = loadSecret(cs.getRef().getSecretName());
        cs.setSecret(secret != null ? secret : new SecretBuilder()
                .withMetadata(getResourceMetadataFor(cs.getRef().getSecretName()))
                .withType("Opaque")
                .withStringData(entries)
                .build());
    }

    /**
     * Load a secret from the cluster.
     *
     * @param name resource
     * @return secret or null
     */
    Secret loadSecret(String name);

    /**
     * Returns the name of the custom resource this state is working on.
     *
     * @return resource name
     */
    String getContextForLogging();

    /**
     * Returns a ClientSecretRef for a client connection. This allows a component to request the secret ref for a
     * connection to a service, including the service address key, the username key, and the password key, so they
     * can be added as environment variables to a pod.
     * <p>
     * This method triggers the creation of any secret that has not been listed in the custom resource as a managed
     * object. For example, when configuring <tt>with.databases</tt>, the necessary secrets are created, and in turn,
     * the database accounts created.
     *
     * @param kind              kind of network client, for example "jdbc", "mongodb", or "uapi".
     * @param schema            schema or account name. Must be unique among all entries of a kind.
     * @param buildOrLoadSecret method that creates a suitable ClientSecret if there is no ClientSecretRef
     * @return the secret reference
     */
    ClientSecretRef getClientSecretRef(String kind, String schema, BiConsumer<ClientSecret, String> buildOrLoadSecret);

    /**
     * Returns all requested client secret refs of a kind.
     *
     * @param kind tpye of client
     * @return collection of client secret refs
     */
    Collection<ClientSecretRef> getClientSecretRefs(String kind);

    /**
     * Returns a client secrets for the given kind amd schema.
     *
     * @param kind   kind of service
     * @param schema the schema/user
     * @return default client secrets
     */
    ClientSecret getClientSecret(String kind, String schema);

    /**
     * Returns all default client secrets for the given kind. Can be used to create users in a server.
     *
     * @param kind kind of service
     * @return default client secrets
     */
    Map<String, ClientSecret> getClientSecrets(String kind);

    /**
     * Return the CoreMediaContentCloud custom resource this target state is working on.
     *
     * @return cmcc
     */
    CoreMediaContentCloud getCmcc();

    /**
     * Return the factory used for ingress generation.
     *
     * @return ingress generator factory.
     */
    CmccIngressGeneratorFactory getCmccIngressGeneratorFactory();

    /**
     * Returns the component collection used to manage the components of the target state.
     *
     * @return component collection
     */
    ComponentCollection getComponentCollection();

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
     * Owner references to be added to created resources.
     *
     * @return the owner reference
     */
    OwnerReference getOurOwnerReference();

    /**
     * Returns the hostname the Preview is available under from the outside.
     *
     * @return hostname of the Preview.
     */
    default String getPreviewHostname() {
        return getHostname(getCmcc().getSpec().getDefaults().getPreviewHostname());
    }

    /**
     * Construct the Kubernetes metadata for the given name.
     *
     * @param name resource name
     * @return the metadata
     */
    ObjectMeta getResourceMetadataFor(String name);

    /**
     * Construct the Kubernetes metadata for the given name.
     *
     * @param component  component
     * @param additional additional qualifiers
     * @return the metadata
     */
    ObjectMeta getResourceMetadataFor(Component component, String... additional);

    /**
     * Returns the name for the component.
     *
     * @param component  component
     * @param additional additional qualifiers
     * @return the resource name
     * @see ResourceNamingProvider#nameFor(Component, String...)
     */
    String getResourceNameFor(Component component, String... additional);

    /**
     * Returns the name for the component.
     *
     * @param component  component
     * @param additional additional qualifiers
     * @return the resource name
     * @see ResourceNamingProvider#nameFor(String, String...)
     */
    String getResourceNameFor(String component, String... additional);

    /**
     * Get the name for a secret.
     *
     * @param kind   kind of secret, see clientSecret
     * @param schema schema, service or username
     * @return name
     */
    default String getSecretName(String kind, String schema) {
        return concatOptional(getCmcc().getSpec().getDefaults().getNamePrefix(), kind, schema);
    }

    /**
     * Returns a map of labels that can be used to identify resources created from the custom resource.
     *
     * @return labels
     */
    default HashMap<String, String> getSelectorLabels() {
        HashMap<String, String> labels = new HashMap<>();
        labels.putAll(CoreMediaContentCloudReconciler.OPERATOR_SELECTOR_LABELS);
        labels.put("cmcc.tsystemsmms.com/cmcc", getCmcc().getMetadata().getName());
        return labels;
    }

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
     * @param component name
     * @return service name
     * @throws IllegalArgumentException if no component can be found, or the component doesn't implement HasService
     */
    default String getServiceNameFor(Component component) {
        return getResourceNameFor(component);
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
     * Returns the hostname the Studio is available under from the outside.
     *
     * @return hostname of the Studio.
     */
    default String getStudioHostname() {
        return getHostname(getCmcc().getSpec().getDefaults().getStudioHostname());
    }

    /**
     * Checks if the given Job is ready. The Job has to exist, and it has to have at least one successful execution.
     *
     * @param name resource
     * @return true if ready
     */
    boolean isJobReady(String name);

    /**
     * Checks if the given StatefulSet is ready. The StatefulSet has to exist, and its current number of replicas have
     * to match the desired count.
     *
     * @param name resource
     * @return true if ready
     */
    boolean isStatefulSetReady(String name);

    /**
     * Returns true if this resource is owned by the operator.
     *
     * @param resource to check
     * @return true if we own this
     */
    boolean isWeOwnThis(HasMetadata resource);
}