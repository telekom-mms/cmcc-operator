# Kubernetes Operator for CoreMedia Content Cloud

[![build](https://github.com/T-Systems-MMS/cmcc-operator/actions/workflows/build.yml/badge.svg)](https://github.com/T-Systems-MMS/cmcc-operator/actions/workflows/build.yml)

## Introduction

[Kubernetes Operators](https://kubernetes.io/docs/concepts/extend-kubernetes/operator/) are specialized software packages that help manage applications and resources in a k8s cluster. This operator will create, initialize and run a CoreMedia Content Cloud application. A custom resource definition is used to define all relevant parameters.

## Quick Links

* [CoreMediaContentClouds custom resource documentation](docs/custom-resource.md)
* [Installing the Operator](#preparing-your-cluster-and-installing-the-operator)
* [Using the Operator to create a CoreMedia installation](#using-the-operator)
* [Customizing the CMCC Operator](docs/customizing-the-operator.md) information for developers
* [ghcr.io/t-systems-mms/cmcc-operator/cmcc-operator](https://github.com/T-Systems-MMS/cmcc-operator/pkgs/container/cmcc-operator%2Fcmcc-operator) Docker Image

## Preparing Your Cluster and Installing the Operator

In order to use the operator, your Kubernetes cluster needs to be prepared. You can then install the operator, and create a CoreMedia Content Cloud installation through the operator.

### Preparing the Kubernetes Cluster

#### Ingress Controller
In order for the CoreMedia installation to be accessible from outside the cluster, an Ingress Controller needs to be configured. The operator currently only supports the generation of Ingress resources for the [Ingress NGINX](https://kubernetes.github.io/ingress-nginx/) controller. Follow the instructions there to install the ingress controller, or use Helm:

```shell
helm upgrade --install ingress-nginx ingress-nginx \
  --repo https://kubernetes.github.io/ingress-nginx \
  --namespace ingress-nginx --create-namespace --set controller.ingressClassResource.default=true
```

Your cluster will need to have a load balancer available, so the ingress controller can be reached. Please refer to your cluster documentation for more information.

### Required DNS Names

In order for users to be able to access the CoreMedia web sites, you will need to provide DNS names (ingress hostnames) and have them point at the ingress controllers IP address.

If you don't have a domain handy for a development setup, for example on your local machine, you can use a DNS service like [nip.io](https://nip.io) or [sslip.io](https://sslip.io). This allows you to configure `defaults.ingressDomain: 127.0.0.1.nip.io`.

All host names are built from three components: the `defaults.namePrefix`, the component name/site mapping name, and the `defaults.ingressDomain`. See below for [Site Mappings](#site-mappings). Examples:

| namePrefix | component/site | ingressDomain    | Resulting URL                         |
|------------|----------------|------------------|---------------------------------------|
| –          | overview       | 127.0.0.1.nip.io | https://overview.127.0.0.1.nip.io     |
| –          | studio         | 127.0.0.1.nip.io | https://studio.127.0.0.1.nip.io       |
| –          | corporate      | 127.0.0.1.nip.io | https://corporate.127.0.0.1.nip.io    |
| dev        | overview       | k8s.example.com  | https://dev-overview.k8s.example.com  |
| dev        | studio         | k8s.example.com  | https://dev-studio.k8s.example.com    |
| dev        | corporate      | k8s.example.com  | https://dev-corporate.k8s.example.com |


### Using Docker Desktop

If you're using Docker Desktop on macOS or Windows, you can have **exactly one service** bind to localhost port 80 and 443. Installing the ingress controller should bind to those ports, and ingresses will be available through localhost on your host machine. Make sure that no other Docker container is using these ports.

### Using k3d

If you're using [k3d](https://k3d.io/) as a cluster, your Docker install will need to [expose the ingress controller](https://k3d.io/v5.0.0/usage/exposing_services/).

### Installing the Operator

You need to add the [Custom Resource Definition](k8s/cmcc-crd.yaml) `CoreMediaContentClouds` (or `cmcc` for short) to the cluster, and create a number of object for the operator: a ClusterRole, a ClusterRoleMapping, a ServiceAccount, and a Deployment for the operator. An example can be found in [`k8s/cmcc-operator.yaml`](k8s/cmcc-operator.yaml).

The CRD and the operator can be added to the cluster like this:

```shell
kubectl apply -f k8s/cmcc-crd.yaml
kubectl apply -f k8s/cmcc-operator.yaml
```

## Using the Operator

### Pull Secret

Depending on you cluster setup and whether you're using a custom registry, you will likely need a pull secret for the pods to be able to pull their images. The operator relies on the default service account having the necessary pull secrets configured for the namespace the CoreMedia installation will be created in.

### License Files

In order for the Content Server components to work they need license files. Create a secret each for the three types of Content Server with the contents of the `license.zip` like so:

```shell
kubectl create secret generic license-cms --from-file=license.zip=license/cms-license.zip 
```

The license secrets need to be created in the same namespace you plan to install CoreMedia in. See `licenseSecrets`, below.

### Creating a CoreMedia Installation

You can createa a complete CoreMedia installation by creating the custom resource `CoreMediaContentClouds` with the desired properties. An example can be found in [`k8s/example.yaml`](k8s/example.yaml), and can be created in the cluster like this:

```shell
kubectl apply -f k8s/example.yaml
```

[docs/custom-resource.md](docs/custom-resource.md) describes all available properties of the `CoreMediaContentClouds` custom resource.

After the resource has been created, you can monitor the status:
```shell
$ kubectl get CoreMediaContentClouds
NAME     MILESTONE
obiwan   Created
```
See below for the different milestones and their meaning.

### Deleting the CoreMedia Installation

To have the operator remove all components, including the databases and their PVCs, simply delete the custom resource:
```shell
kubectl delete CoreMediaContentClouds example
```

Depending on your cluster, the deletion might take a minute.


## Building The Operator

You build the Docker image for the Operator and load it into your local Docker with this command:
```shell
./gradlew build jibDockerBuild
```

You can use `./gradlew jib` to build and push the image to a Docker registry. See the documentation for [Job Gradle Plugin](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin).