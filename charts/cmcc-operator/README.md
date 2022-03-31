# CoreMedia Content Cloud Operator

The [CoreMedia Content Cloud Operator](https://github.com/T-Systems-MMS/cmcc-operator) provides an easy to use way to
manage deployments of [CoreMedia Content Cloud](https://www.coremedia.com/). This Helm chart allows you to install the
operator; an accompanying chart helps managing the custom resource or config map to manage an individual deployment.

## TL;DR

```console
$ helm repo add cmcc-operator https://foo.github.io/charts
$ helm upgrade --install --create-namespace --namespace cmcc-operator cmcc-operator charts/cmcc-operator
```

## Introduction

The [CoreMedia Content Cloud Operator](https://github.com/T-Systems-MMS/cmcc-operator) provides an easy to use way to
manage deployments of [CoreMedia Content Cloud](https://www.coremedia.com/). This Helm chart allows you to install the
operator; an accompanying chart helps managing the custom resource or config map to manage an individual deployment.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+

## Installing the Chart

To install the chart with the release name `my-release`:

```console
$ helm upgrade --install --create-namespace --namespace cmcc-operator cmcc-operator charts/cmcc-operator
```

The command deploys Joomla! on the Kubernetes cluster in the default configuration. The [Parameters](#parameters)
section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `my-release` deployment:

```console
$ helm delete my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Parameters

| Name                         | Description                                                                                                                           | Value                                                              |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `affinity`                   | Node affinity attributes to apply to the operator pods.                                                                               | `{}`                                                               |
| `cmcc.ingressBuilder`        | The type of builder to use for the Ingress objects.                                                                                   | `blueprint`                                                        |
| `cmcc.manageSecrets`         | Should the operator create secrets dynamically for databases and UAPI client?                                                         | `true`                                                             |
| `cmcc.useConfigMap`          | Should the operator find deployment configuration for a CMCC install in a config map?                                                 | `false`                                                            |
| `cmcc.useCrd`                | Should the operator find deployment configuration for a CMCC install in a custom resource of type `CoreMediaContentCloud`?            | `true`                                                             |
| `clusterRole.create`         | Whether to create a ClusterRole with appropriate rights for the operator to work cluster-wide                                         | `true`                                                             |
| `image.pullPolicy`           | The pull policy to apply to the image                                                                                                 | `IfNotPresent`                                                     |
| `image.repository`           | The repository to pull the operator image from.                                                                                       | `ghcr.io/t-systems-mms/cmcc-operator/cmcc-operator`                |
| `image.tag`                  | The image tag to use                                                                                                                  | As set in the chart, typically corresponds with the chart version. |
| `imagePullSecrets`           | Pull secrets to add to the deployment. Uses the namespace default service account and its pull secrets by default.                    | `""`                                                               |
| `fullnameOverride`           | Overrides the full name of components, which is used for the deployment, clusterrole and clusterrolemapping, and the service account. | the chart name plus the release name                               |
| `nameOverride`               | Overrides the short name. Used for the selector labels.                                                                               | the chart name                                                     |
| `nodeSelector`               | Node selector attributes to apply to the operator pods.                                                                               | `{}`                                                               |
| `podAnnotations`             | Annotations to apply to the operator pods.                                                                                            | `{}`                                                               |
| `podSecurityContext`         | Security context to apply to the operator pods.                                                                                       | `{}`                                                               |
| `resources`                  | Resources to apply to the operator pods.                                                                                              | `{}`                                                               |
| `securityContext`            | Security context to apply to the operator deployment.                                                                                 | `{}`                                                               |
| `serviceAccount.annotations` | Annotations to apply the Service account, if it is creatd.                                                                            | `{}`                                                               |
| `serviceAccount.create`      | Whether to create a ServiceAccount for the operator.                                                                                  | `true`                                                             |
| `serviceAccount.name`        | The name of the ServiceAccount to be created.                                                                                         | full name of the components                                        |
| `tolerations`                | Node tolerations attributes to apply to the operator pods.                                                                            | `{}`                                                               |


## Configuration and Installation Details

### Custom Resource

The operator gets the desired configuration from a custom resource of type `CoreMediaContentClouds`. If `cmcc.useCrd` is enabled (default), the chart will install the appropriate `CustomResourceDefinition`.

If you cannot install a CRD, you can disable `cmcc.useCrd` and enable `cmcc.useConfigMap`, to supply the configuration for a deployment using a config map instead of the custom resource. See [Installing the Operator Using a Config Map](https://github.com/T-Systems-MMS/cmcc-operator#installing-the-operator-using-a-config-map) for further details.

### Role-based Access Control

If you install the operator in its default configuration, it will monitor `CoreMediaContentClouds` custom resources cluster-wide. In order for the operator to be allowed to work that way, a `ClusterRole` and a `ClusterRoleBinding` are installed together with a `ServiceAccount`, granting the operator the appropriate rights. This includes config maps, deployments and statefulsets, pvc and ingresses.

If `cmcc.manageSecrets` is enabled (default), the operator will automatically generated secrets for UAPI clients and (if the custom resources has `with.databases` enabled) database accounts. This requires that the operator can read and write any secret cluster-wide. If you disable `cmcc.manageSecrets`, the right to manage secrets is removed from the `ClusterRole`, and you will need to:
* supply any and all secrets yourself
* configure the Content Server components (Content Management Server, Master Live Server, and any Replication Live Server) to change the passwords for the default system accounts from their defaults to the values provided in your secrets.

Of course, you can disable both `clusterrole.create` and `serviceaccount.create`, and provide your custom RBAC configuration for the operator yourself.

## License

Copyright &copy; 2022 T-Systems Multimedia Solutions GmbH

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.