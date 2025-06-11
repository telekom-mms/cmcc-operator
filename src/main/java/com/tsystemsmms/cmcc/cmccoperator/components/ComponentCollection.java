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

import com.tsystemsmms.cmcc.cmccoperator.crds.ComponentSpec;
import com.tsystemsmms.cmcc.cmccoperator.crds.Milestone;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.CustomResourceConfigError;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.NoSuchComponentException;
import com.tsystemsmms.cmcc.cmccoperator.targetstate.TargetState;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import org.springframework.beans.factory.BeanFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Manage a collection of components. Includes a factory method for components that uses the ComponentBeanFactories and the type from the ComponentSpec.
 */
public class ComponentCollection {
  private final HashMap<ComponentReference, Component> components = new HashMap<>();
  private final BeanFactory beanFactory;
  private final KubernetesClient kubernetesClient;
  private final TargetState targetState;

  public ComponentCollection(BeanFactory beanFactory, KubernetesClient kubernetesClient, TargetState targetState) {
    this.beanFactory = beanFactory;
    this.kubernetesClient = kubernetesClient;
    this.targetState = targetState;
  }

  /**
   * Adds a component by spec. Returns the generated component to allow further customization.
   *
   * @param componentSpec specifying the component to add
   * @return the created/updated component
   */
  public Component add(ComponentSpec componentSpec) {
    ComponentReference cr = new ComponentReference(componentSpec);
    Component c = components.get(cr);
    if (c == null) {
      c = createComponentByComponentSpec(componentSpec);
      if (componentSpec.getMilestone() == null) {
        c.getComponentSpec().setMilestone(Milestone.DeliveryServicesReady);
      }
      components.put(cr, c);
    } else {
      c.updateComponentSpec(componentSpec);
    }
    return c;
  }

  /**
   * Add components for all the specs.
   *
   * @param specs components to be added
   */
  public void addAll(Collection<ComponentSpec> specs) {
    specs.forEach(this::add);
  }

  public Stream<Component> findAllOfType(String type) {
    return components.values().stream().filter(c -> c.getComponentSpec().getType().equals(type));
  }

  public Stream<Component> findAllOfTypeAndKind(String type, String kind) {
    return components.values().stream().filter(c -> c.getComponentSpec().getType().equals(type) && c.getComponentSpec().getKind().equals(kind));
  }

  public Optional<Component> getOfTypeAndKind(String type, String kind) {
    return components.values().stream().filter(c -> c.getComponentSpec().getType().equals(type) && c.getComponentSpec().getKind().equals(kind)).findAny();
  }

  public void removeOfTypeAndKind(String type, String kind) {
    components.entrySet().removeIf(e -> e.getValue().getComponentSpec().getType().equals(type) && e.getValue().getComponentSpec().getKind().equals(kind));
  }

  /**
   * Returns all added components.
   *
   * @return the components
   */
  public Collection<Component> getComponents() {
    return components.values();
  }

  /**
   * Find all components implementing clazz.
   *
   * @param clazz the class
   * @param <T>   the class
   * @return a list of components implementing class.
   */
  @SuppressWarnings("unchecked")
  public <T> List<T> getAllImplementing(Class<T> clazz) {
    return (List<T>) components.values().stream()
            .filter(clazz::isInstance)
            .toList();
  }

  /**
   * Create a Component based on a component spec. The component spec needs to specify at least the type.
   *
   * @param cs the spec
   * @return a component
   * @throws IllegalArgumentException if the component can not be created
   */
  public Component createComponentByComponentSpec(ComponentSpec cs) {
    if (cs.getType() == null) {
      throw new CustomResourceConfigError("cmcc " + targetState.getCmcc().getMetadata().getName() + ": Unable to use component configuration " + cs.getType() + ": type must be set");
    }
    return (Component) beanFactory.getBean("component:" + cs.getType(), kubernetesClient, targetState, cs);
  }

  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param name component name
   * @param kind component kind
   * @return component
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasJdbcClient getHasJdbcClientComponent(String name, String kind) throws IllegalArgumentException {
    return getHasJdbcClientComponent(c -> c.getSpecName().equals(name) && c.getComponentSpec().getKind().equals(kind));
  }


  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param p predicate to find the component with
   * @return service name
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasJdbcClient getHasJdbcClientComponent(Predicate<Component> p) throws IllegalArgumentException {
    Optional<Component> component = getComponents().stream().filter(p).findAny();
    if (component.isEmpty())
      throw new IllegalArgumentException("not found");
    if (component.get() instanceof HasJdbcClient hasJdbcClient) {
      return hasJdbcClient;
    } else {
      throw new IllegalArgumentException("exists but does not implement HasService");
    }
  }


  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param p predicate to find the component with
   * @return service name
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasService getHasServiceComponent(Predicate<Component> p) throws IllegalArgumentException {
    Optional<Component> component = getComponents().stream().filter(p).findAny();
    if (component.isEmpty())
      throw new NoSuchComponentException(null, "not found");
    if (component.get() instanceof HasService hasService) {
      return hasService;
    } else {
      throw new NoSuchComponentException(null, "exists but does not implement HasService");
    }
  }


  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param name component name
   * @return component
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasService getHasServiceComponent(String name) throws IllegalArgumentException {
    return getHasServiceComponent(c -> c.getSpecName().equals(name));
  }


  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param name component name
   * @param kind component kind
   * @return component
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasService getHasServiceComponent(String name, String kind) throws IllegalArgumentException {
    return getHasServiceComponent(c -> c.getSpecName().equals(name) && c.getComponentSpec().getKind().equals(kind));
  }


  /**
   * Find a component implementing HasService based on the given predicate. If the component does not implement
   * HasService, or no component can be found, throw an IllegalArgumentException.
   *
   * @param cs reference component
   * @return component
   * @throws IllegalArgumentException if no suitable component can be found
   */
  public HasService getHasServiceComponent(ComponentSpec cs) throws IllegalArgumentException {
    if (cs.getKind() != null && !cs.getKind().isEmpty())
      try {
        return getHasServiceComponent(c -> c.getComponentSpec().getType().equals(cs.getType()) && c.getComponentSpec().getKind().equals(cs.getKind()));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Component \"" + cs.getType() + "\"/" + cs.getKind() + ": " + e.getMessage());
      }
    else
      try {
        return getHasServiceComponent(c -> c.getComponentSpec().getType().equals(cs.getType()));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Component \"" + cs.getType() + "\": " + e.getMessage());
      }
  }


  /**
   * Return the name of the service resource for the named component.
   *
   * @param name component name
   * @return service name
   */
  public String getServiceNameFor(String name) {
    try {
      HasService component = getHasServiceComponent(c -> c.getSpecName().equals(name));
      return targetState.getResourceNameFor(component);
    } catch (NoSuchComponentException e) {
      throw new NoSuchComponentException(name);
    } catch (RuntimeException e) {
      throw new RuntimeException("Unable to locate component \"" + name + "\"", e);
    }
  }


  /**
   * Return the name of the service resource for the named component.
   *
   * @param name component name
   * @param kind component kind
   * @return service name
   */
  public String getServiceNameFor(String name, String kind) {
    try {
      HasService component = getHasServiceComponent(c -> c.getSpecName().equals(name) && c.getComponentSpec().getKind().equals(kind));
      return targetState.getResourceNameFor(component);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Component \"" + name + "\"/\"" + kind + "\": " + e.getMessage());
    }
  }

  public static boolean equalsSpec(ComponentSpec a, ComponentSpec b) {
    return new ComponentReference(a).equals(new ComponentReference(b));
  }

  @Data
  public static class ComponentReference {
    private final String type;
    private final String kind;
    private final String name;

    public ComponentReference(ComponentSpec cs) {
      this.type = cs.getType();
      this.kind = cs.getKind();
      this.name = cs.getName().isEmpty() ? this.type : cs.getName();
    }
  }
}
