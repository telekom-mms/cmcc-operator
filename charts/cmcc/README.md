# CoreMedia Content Cloud

This helm chart creates a custom resource for the [CoreMedia Content Cloud Operator](https://github.com/Telekom-MMS/cmcc-operator), configures and manages a [CoreMedia Content Cloud](https://www.coremedia.com/) deployment based on the configuration in that resource.

## TL;DR

```console
$ helm repo add cmcc-operator https://t-systems-mms.github.io/cmcc-operator/
$ helm upgrade --install my-release cmcc-operator/cmcc --values my-values.yaml
```

## Introduction

The [CoreMedia Content Cloud Operator](https://github.com/Telekom-MMS/cmcc-operator) provides an easy to use way to
manage deployments of [CoreMedia Content Cloud](https://www.coremedia.com/). This Helm chart  helps managing the custom resource or config map to manage an individual deployment.

## Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+

## Installing the Chart

To install the chart with the release name `my-release`:

```console
$ helm upgrade --install my-release cmcc-operator/cmcc --values my-values.yaml
```

The command deploys CoreMedia Content Cloud on the Kubernetes cluster. You will need to set a number of parameters, in particular `default.image.registry` to the Docker registry hosting your CoreMedia Content Cloud images. The [Parameters](#parameters) section lists the parameters that can be configured during installation.

> **Tip**: List all releases using `helm list`

## Uninstalling the Chart

To uninstall/delete the `my-release` deployment:

```console
$ helm delete my-release
```

The command removes all the Kubernetes components associated with the chart and deletes the release.

## Parameters

| Name               | Description                                                                                                                                                                              | Value                                |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| `cmcc`             | The contents of the custom resource. See [Custom Resource CoreMediaContentClouds](https://github.com/Telekom-MMS/cmcc-operator/blob/main/docs/custom-resource.md) for more information | –                                    |
| `fullnameOverride` | Overrides the full name of components, which is used for the deployment, clusterrole and clusterrolemapping, and the service account.                                                    | the chart name plus the release name |
| `nameOverride`     | Overrides the short name. Used for the selector labels.                                                                                                                                  | the chart name                       |
| `useConfigMap`     | Instead of creating a custom resource with the deployment description, use a config map.                                                                                                 | the chart name                       |


## Configuration and Installation Details


### Substitution of variables inside values

The contents of the `cmcc` value is fed through the Helm [`tpl()`](https://helm.sh/docs/howto/charts_tips_and_tricks/#using-the-tpl-function) function. This allows you to refer to other values from values inside the `cmcc` map. For example, if you need to configure an HTTPS_PROXY for multiple components, you could write:

```yaml
httpProxy: https://proxy.example.com:3128
cmcc:
  ...
  components:
    - name: import-theme
      type: management-tools
      env:
        - name: HTTPS_PROXY
          value: "{{ .Values.httpProxy }}"
      ...
      - name: import-content
        type: management-tools
        env:
          - name: HTTPS_PROXY
            value: "{{ .Values.httpProxy }}"
```

### Using a Config Map Instead

The operator uses a custom resource definition to obtain the deployment description for the CoreMedia Content Cloud installation. If you've installed the operator with support for using a `ConfigMap` (instead of or in addition to the custom resource), you can set `--set useConfigMap=true` to have this Helm chart create a config map.

## License

Copyright &copy; 2022 T-Systems Multimedia Solutions GmbH

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
