# Customizing the CMCC Operator

The Operator is implemented as a [Spring Boot](https://spring.io/projects/spring-boot) application using the [Java Operator SDK](https://github.com/java-operator-sdk/java-operator-sdk), which in turn uses the [Fabric8 Kubernetes client](https://github.com/fabric8io/kubernetes-client).

The application has been designed with the expectation that it will need to be customized for a concrete CoreMedia project. There are a number of extension points that allow you to add your own logic and component types.

## Overview

The application consists of a number of classes and packages.

[CMCCOperatorApplication](src/main/java/com/tsystemsmms/cmcc/cmccoperator/CMCCOperatorApplication.java) is the Spring Boot application class. In addition to the `main()` method, it contains a number of bean definitions.

[CoreMediaContentCloudReconciler](src/main/java/com/tsystemsmms/cmcc/cmccoperator/CoreMediaContentCloudReconciler.java) implements the logic to listen to events like the creation, modification and deletion of the custom resource, build an updated set of Kubernetes resources, and remove outdated ones. The list of updated resources is generated on each loop through a TargetState bean (see below). 

### Custom Resource Definitions

The Operator SDK includes code to automatically generate a CRD based on annotations in the code. The main class for the custom resource is [CoreMediaContentCloud](src/main/java/com/tsystemsmms/cmcc/cmccoperator/crds/CoreMediaContentCloud.java). The package contains all properties used in the CRD as POJOs, utilizing the [lombok `@Data`](https://projectlombok.org/features/Data) annotation.

### Target State

The task of computing the Kubernetes resources that should be created or updated is delegated from the reconciler to the `TargetState` bean. The `TargetState` inspects the custom resource and the state of other resources, builds a list of components that should exist, and finally creates a collection of Kubernetes resources. Kubernetes resources are typically created through the list of components and ingresses, see below.

The [DefaultTargetState](src/main/java/com/tsystemsmms/cmcc/cmccoperator/targetstate/DefaultTargetState.java) implements the functionality documented here and in [Custom Resource CoreMediaContentClouds](custom-resource.md). You can modify the existing class, derive your own class from it, or create your own implementation of `TargetState`. [AbstractTargetState](src/main/java/com/tsystemsmms/cmcc/cmccoperator/targetstate/AbstractTargetState.java) contains a number of methods that are likely going to be helpful.

### Components

Each component of a CoreMedia system (for example, the Content Management Server or the Content Application Engine) are modelled as a [Component](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/Component.java). The Component interface allows components to be queried for certain attributes ([HasMySQLSchema](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/HasMySQLSchema.java) and [HasService](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/HasService.java)), and will generate zero or more Kubernetes resources from its `buildResources()` method.

The list of components is managed by the [ComponentCollection](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/ComponentCollection.java) bean. It utilizes the [ComponentBeanFactories](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ComponentBeanFactories.java) bean to created beans based on their `ComponentSpec.type` property.

You can add your own component by implementing `Component` and adding a bean definition for your type to `ComponentBeanFactories`.

The package `com.tsystemsmms.cmcc.cmccoperator.components` contains `Component`s for all standard CoreMedia components, plus MongoDB and MariaDB components. The abstract classes [AbstractComponent](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/AbstractComponent.java), [SpringBootComponent](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/SpringBootComponent.java), [CorbaComponent](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/corba/CorbaComponent.java), and [JobComponent](src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/job/JobComponent.java) can be used to jumpstart new components.

### Ingress

The operator will create suitable Ingress resources for the overview, preview CAE, Studio, and live CAEs. The creation of these Ingress resources is implemented through [CmccIngressGenerator](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/CmccIngressGenerator.java), which gets instantiated by a Spring Bean implementing [CmccIngressGeneratorFactory](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/CmccIngressGeneratorFactory.java). Individual Ingress resources are built from [IngressBuilder](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/IngressBuilder.java), instantiated by a Spring bean implementing [IngressBuilderFactory](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/IngressBuilderFactory.java).

There is one implementation [BlueprintCmccIngressGenerator](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/BlueprintCmccIngressGenerator.java), which implements the default URL mapping logic of CMCC11. If you want to modify the way URLs are built in the CAE, you will likely want to create your own implementation of `CmssIngressGenerator`.

There is one implementation [NginxIngressBuilder](src/main/java/com/tsystemsmms/cmcc/cmccoperator/ingress/NginxIngressBuilder.java), which builds Ingress resources suitable for use with the [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/). If you are using a different Ingress controller (for example [Traefik](https://doc.traefik.io/traefik/providers/kubernetes-ingress/)), you will need to implement an `IngressBuilder` for that.

It is necessary to rewrite URLs for the CAEs (for example, you need to rewrite `/resource/...` to `/blueprint/servlet/resource...`). The standard Kuberentes Ingress definition does not offer such rewriting functionality, and the different Ingress controllers implement these in incompatible, proprietary ways. NGINX Ingress Controller uses annotations, while Traefik has defined their own CRD. This makes it necessary to have these additional builder classes.

### Resource Reconciler

Finally, the package `com.tsystemsmms.cmcc.cmccoperator.resource` contains classes that help with updating existing resources. The Fabric8 client currently has some limitations when updating existing objects, which might try to modify or overwrite properties that are immutable after creation. These classes help work around that limitation.