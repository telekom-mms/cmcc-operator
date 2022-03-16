# Custom Resource CoreMediaContentClouds

## Custom Resource Properties Status

The `status` property of the custom resource has these fields:

| Property         | Type   | Description                                                         |
|------------------|--------|---------------------------------------------------------------------|
| `milestone`      | enum   | Which milestone has been reached in configuring all components      |
| `error`          | String | A one-line error message, or empty string                           |
| `errorMessage`   | String | A longer error message (if any)                                     |
| `ownedResources` | String | Used internally by the operator to keep track of created resources. |

The `milestone` status column shows the creation status of the installation:
1. `Created`: The initial databases are being created (if requested by `with.databases`)
2. `DatabasesReady`: The databases are running, all schema s have been created. The core management components are being started (CMS, MLS).
3. `ContentserverReady`: The Content Management Server and the Master Live Server are running. The default passwords are being replaced based on secrets.
4. `ManagementReady`: The core management components are running. All remaining component are being started, including the content import.
5. `Ready`: The content import has completed, all components are up and running.
6. `Never`: Special state that will never be reached, can be used on components to define them, but have the operator never create the resources for them.


## Custom Resource Properties Specification

The Custom Resource `CoreMediaContentClouds` (`cmcc` for short) `spec` field defines these properties to allow you to deploy a CoreMedia installation. Whenever possible, these properties have suitable defaults.

| Property                              | Type            | Default                        | Description                                                                                                                                                                                |
|---------------------------------------|-----------------|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `comment`                             | String          | ""                             | Arbitrary comment, can be used to force an update to the resource                                                                                                                          |
| `components`                          | array           | []                             | List of CoreMedia components to be created. See below for available components and their parameters                                                                                        |
| `defaults`                            | object          | –                              | Default values for components                                                                                                                                                              |
| `defaults.curlImage`                  | String          | `docker.io/alpine/k8s:1.19.15` | A Docker image with curl available. Used in init containers to wait for Content Server components to become available.                                                                     |
| `defaults.image`                      | object          | –                              | Defaults for the image specification                                                                                                                                                       |
| `defaults.image.registry`             | String          | ""                             | Docker Image Registry to pull images from                                                                                                                                                  |
| `defaults.image.tag`                  | String          | `latest`                       | Docker Image Tag to pull images from                                                                                                                                                       |
| `defaults.image.pullPolicy`           | String          | `IfNotPresent`                 | default imagePullPolicy                                                                                                                                                                    |
| `defaults.ingressDomain`              | String          | ""                             | Fully qualified domain name to append to ingress host names                                                                                                                                |
| `defaults.insecureDatabasePassword`   | String          | ""                             | **DO NOT SET**. See below for more information.                                                                                                                                            |
| `defaults.namePrefix`                 | String          | ""                             | Prefix resources with this name plus '-'.                                                                                                                                                  |
| `defaults.previewHostname`            | String          | `preview`                      | Hostname of the preview CAE. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended.                                               |
| `defaults.resources`                  | resources       | –                              | Default [resources to apply to component pods](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#resources)                                                   |
| `defaults.studioHostname`             | String          | `studio`                       | Hostname of the Studio. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended.                                                    |
| `importJob`                           | object          | –                              | Deprecated, not evaluated any more. See below for the `management-tools` component.                                                                                                        |
| `licenseSecrets`                      | object          | –                              | Names of the secrets containing the license                                                                                                                                                |
| `licenseSecrets.CMSLicense`           | String          | `license-cms`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                                                                     |
| `licenseSecrets.MLSLicense`           | String          | `license-mls`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                                                                     |
| `licenseSecrets.RLSLicense`           | String          | `license-rls`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                                                                     |
| `siteMappings`                        | array           | –                              | Mappings between DNS names and site segments, see below                                                                                                                                    |
| `with`                                | object          | –                              | Optional special components and configurations                                                                                                                                             |
| `with.databases`                      | boolean         | false                          | Create both a MariaDB and MongoDB server, and schemas and secrets for all components that require them                                                                                     |
| `with.delivery`                       | object          | –                              | Create all components required for a CoreMedia delivery stage                                                                                                                              |
| `with.delivery.rls`                   | int             | 0                              | Number of Replication Live Servers to create                                                                                                                                               |
| `with.delivery.minCae`                | int             | 0                              | Minimum number of CAEs per RLS                                                                                                                                                             |
| `with.delivery.maxCae`                | int             | 0                              | Maximum number of CAEs per RLS                                                                                                                                                             |
| `with.management`                     | boolean         | true                           | Create all components required for a CoreMedia management stage                                                                                                                            |


## Enabling Convenience Options `with`

### Local database servers `with.databases`

When `with.databases` is enabled, the operator will add a MariaDB and a MongoDB server to the components, and create appropriate database schemas and users, including the secrets needed for the components to access them.

The schema/database name as well as the username is automatically determined by the component, see below. The password for each of these accounts is generated randomly on creation.

If you would like to connect to the databases with a constant password, you can set `defaults.insecureDatabasePassword`. **DANGER You should only set this property if you are certain that the database server is only accessible over the network to authorized users.**

### Delivery Components `with.delivery`

The operator can create Replication Live Servers and Live CAEs and configure a Horizontal Pod Autoscaler for the CAEs.

`with.delivery.rls` determines the number of Replication Live Servers the operator should create. The default of `0` means that all CAEs will be connected to the Master Live Server, and no RLS will be created. Note that while it is possible to increase the number of RLS after the CoreMedia installation has been created, the operator currently cannot create additional database schemas for any new RLS, and can not clone database contents from the Master Live Server to any new RLS. If you want to increase the number of RLS after initial setup, you would need to take care of that manually.

`with.delivery.minCae` and `with.delivery.maxCae` determine how many Live CAEs should be created per RLS. The defaults are both `0`, which disables the creation of Live CAEs. When `with.delivery.minCae` is set to a value equal or larger than `with.delivery.maxCae`, a fixed number of Live CAEs will be configured. When `with.delivery.minCae` is set to a value smaller than `with.delivery.maxCae`, a horizontal pod autoscaler will be configured that will scale the number of CAEs from the minimum to the maximum amount based on the CPU load of the CAEs.

**Note** At this time, the operator can only configure a single Live CAE connected to the Master Live Server; any other configuration will lead to an error.

#### Example: development setup with one Live CAE

`with.delivery.rls=0`, `with.delivery.minCae=1`, `with.delivery.maxCae=0` will create exactly one CAE which will be connected to the Master Live Server.

#### Example: two RLS with auto scaling

`with.delivery.rls=2`, `with.delivery.minCae=1`, `with.delivery.maxCae=10` will create two RLS and one CAE each. A HPA will be set up that will scale the number of CAEs to up to 10 per RLS.

### Management Components `with.management`

When `with.management` is enabled, the operator automatically adds all required components to the list of components. This is equivalent to configuring:
```yaml
components:
  - type: cae
    kind: preview
  - type: cae-feeder
    kind: live
  - type: cae-feeder
    kind: preview
  - type: content-feeder
  - type: content-server
    kind: cms
  - type: content-server
    kind: mls
  - type: elastic-worker
  - type: overview
  - type: solr
    kind: leader
  - type: studio-client
  - type: studio-server
  - type: user-changes
  - type: workflow-server
```

You can override settings for individual components, for example the image specification, by declaring that component explicitly.


## Automatic Generation of Ingresses and Site Mappings `siteMappings`

The operator automatically creates ingresses for all components that need to be exposed: the Studio (combined for studio-client and studio-server), the preview CAE and the live CAE.

For the CAEs, the website domains and the site root channel's URI segments need to be mapped to each other: the ingress controller needs to rewrite URLs so the CAE can interpret them, and the CAE needs to generate URLs so they map back to the CAE in the right way. The array of objects `siteMappings` defines the mapping, and the operator creates both the ingress objects and the mapping properties for the live CAE from them.

The operator can only generate Ingress resources compatible with the [ingress-nginx controller](https://kubernetes.github.io/ingress-nginx/).

### Site Mappings

Each entry defines one DNS name and all the site segments that will be served under this name.

| Property             | Type            | Default | Description                                                                                                                            |
|----------------------|-----------------|---------|----------------------------------------------------------------------------------------------------------------------------------------|
| `hostname`           | String          | –       | DNS name, either a fully-qualified domain name (FQDN, www.example.com), or a short name with the default ingress domain name appended. |
| `primarySegment`     | String          | –       | Primary site segment; this is the site for the `/` URI.                                                                                |
| `additionalSegments` | array of String | –       | Segments of additional sites served from this host.                                                                                    |

Example:
```yaml
  siteMappings:
    - hostname: corporate.example.com
      primarySegment: corporate
      additionalSegments:
        - corporate-de-de
        - corporate-en-ca
        - corporate-en-gb
```

This makes the Chef Corp. example site available under these URLs:
* `https://corporate.example.com` (redirects to `/corporate`)
* `https://corporate.example.com/corporate`
* `https://corporate.example.com/corporate-de-de`
* `https://corporate.example.com/corporate-en-ca`
* `https://corporate.example.com/corporate-en-gb`

## Components

`components` specifies a list of CoreMedia components and their parameters. The only required parameter is `type`, which specifies the type of component, and for some component types, `kind` as a sub-type.

| Property           | Type           | Default         | Description                                                                                                             |
|--------------------|----------------|-----------------|-------------------------------------------------------------------------------------------------------------------------|
| `type`             | String         | –               | Required type of the component                                                                                          |
| `kind`             | String         | –               | Sub-type, required for some component types                                                                             |
| `name`             | String         | type            | The name of the component and the resources created for it. Defaults to a type-specific name, typically the type itself |
| `image`            | object         | –               | Specification of the Docker Image to use for the main container of the main pod of the component                        |
| `image.registry`   | String         | `coremedia`     | Docker Image Registry to pull images from                                                                               |
| `image.repository` | String         | see description | Docker Image Repository to pull images from. Default is type-specific based on the standard blueprint image names.      |
| `image.tag`        | String         | latest          | Docker Image Tag to pull images from                                                                                    |
| `image.pullPolicy` | String         | `IfNotPresent`  | default imagePullPolicy                                                                                                 |
| `milestone`        | enum           | `DatabaseReady` | Milestone that has to be reached before this component gets created.                                                    |
| `env`              | list of EnvVar | –               | Additional environment variables to be made available to the containers                                                 |
| `args`             | list of String | –               | Args for the main container of the main pod. Defaults to unset, using the default from the image.                       |

You can set the milestone `Never` to define but disable a component.

### Component `blob-server`

CoreMedia can make use of an external storage for blobs (images, videos, etc.), and add only references to those blobs during content import, instead of importing the data itself. Enabling this option will bring up a blob HTTP server, and configure the import job to use the appropriate settings. This is particularly useful for the CoreMedia-provided demo content. The blob server needs to have all blobs already available to it; `serverimport` will not upload the blobs to the server.

The default image for this component is `blob-server`. You can override the default image by declaring the component:
```yaml
  components:
    - type: "blob-server"
      image:
        registry: gitlab.example.com/myproject/coremedia-blobs
        repository: coremedia-blobs
        tag: "2201.1"
```

See [github.com/T-Systems-MMS/cmcc-blob-server](https://github.com/T-Systems-MMS/cmcc-blob-server) for an example on how to create a suitable Docker image.

### Component `content-feeder`

The Content Feeder application.

The Solr collection is `studio`.

### Component `cae`

The CAE type has two kinds: `preview` and `live`. The default image as well as the name are `cae-preview` and `cae-live`, respectively.

The Solr collection is `preview` and `live`, respectively.

### Component `cae-feeder`

The CAE Feeder type has two kinds: `preview` and `live`. The default image as well as the name are `cae-feeder-preview` and `cae-feeder-live`, respectively.

The database schema and username is `caefeeder` and `mcaefeeder`, respectively.

The Solr collection is `preview` and `live`, respectively.

### Component `content-server`

The Content Server type has three kinds: `cms`, `mls`, and `rls`, for the three kinds of Content Servers that can be configured in CoreMedia system.

The default name depends on the kind: `content-management-server` for a `cms`, `master-live-server` for an `mls`, and `replication-live-server` for an `rls`. Note that if you need to configure multiple RLS, you will need to specify names yourself.

The database names are `management`, `master`, and `replication`.

### Component `overview`

A static web page exposed through an ingress as `overview`. The operator creates an `/info.json` file and adds a static HTML page that shows an overview of the externally accessible components. This allows users to quickly locate the correct links to the Studio, the preview and the live sites.

The following files are included by default:

| File            | Description                                                         |
|-----------------|---------------------------------------------------------------------|
| `index.html`    | The main overview page. Includes client-side JS to render `info.js` |
| `info.json`     | See below. Can not be overridden.                                   |
| `handlebars.js` | The [Handlebars](https://handlebarsjs.com) template library         |
| `overview.css`  | Default styles                                                      |

The `info.json` contains these fields:

| Field          | Description                                                  |
|----------------|--------------------------------------------------------------|
| `comment`      | The `comment` from the spec.                                 |
| `name`         | The name of the custom resource.                             |
| `prefix`       | The `defaults.namePrefix` from the spec.                     |
| `previewUrl`   | The URL of the preview as a fully-qualified name.            |
| `siteMappings` | The `siteMappings` from the spec, with the `fqdn` filled in. |
| `studioUrl`    | The URL of the Studio as a fully-qualified name.             |

#### Customizing the Overview Page

You can customize the overview page by configuring the `extra` entry on the `overview` component to override existing files and add additional ones:
```yaml
components:
  - type: overview
    extra:
      overview.css: >
        body: { font-family: 'Comic Sans'; }
        .logo { background: url(/logo.svg);
      logo.svg: |
        <svg>...
```


### Component `elastic-worker`

The Elastic Worker application.

### Component `mongodb`

If `with.databases` is enabled, the operator creates a MongoDB instance and the necessary secrets for the components to access it.

### Component `mysql`

If `with.databases` is enabled, the operator creates a MySQL instance and the necessary secrets for the components to access it.

### Component `solr`

The Solr type has two kinds: `leader` and `follower`. The name defaults to `solr-leader` and `solr-follower`, respectively. Note that if you want to add more than one follower, you will need to assign individual names to the components yourself.

### Component `studio-client`

The Studio single-page app.

### Component `studio-server`

The Studio server app; includes the ingresses needed to access the Studio.

The database schema and username is `edcom`.

The Solr collection is `studio`.

### Component `management-tools`

A Kubernetes Job running the `management-tools` image. You can configure these properties:

| Property                              | Type            | Default    | Description                                                                                                                                                                              |
|---------------------------------------|-----------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `args`                                | list of Strings | ""         | List of the entrypoint scripts to be run to import things. Defaults to a list suitable to either local import (when `importJob.pvc` is set) or a remote download otherwise               |
| `env`                                 | list of EnvVars | –          | Additional [environment variables](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#environment-variables) to be added to the `management-tools` container |
| `importJob.blobServer`                | boolean         | false      | Configure a blob server component                                                                                                                                                        |
| `importJob.contentUsersAuth`          | object          | –          | Secret reference for authentication for downloading the content-users.zip                                                                                                                |
| `importJob.contentUsersAuth.password` | String          | `password` | secret key for the password                                                                                                                                                              |
| `importJob.contentUsersAuth.secret`   | String          | ""         | name of the secret                                                                                                                                                                       |
| `importJob.contentUsersAuth.username` | String          | `username` | secret key for the username                                                                                                                                                              |
| `importJob.contentUsersUrl`           | string          | ""         | URL of the content-users.zip to be imported, used by `use-remote-content-archive`                                                                                                        |
| `importJob.forceContentImport`        | boolean         | false      | force re-import of content and users                                                                                                                                                     |
| `importJob.forceThemeImport`          | boolean         | false      | force re-import of themes                                                                                                                                                                |
| `importJob.pvc`                       | string          | ""         | Volume `containing content-users.zip` and `frontend.zip`, used by `unpack-content-users-frontend`                                                                                        |
| `importJob.themesAuth`                | object          | –          | Secret reference for authentication for downloading the `content-users.zip`                                                                                                              |
| `importJob.themesAuth.password`       | String          | `password` | secret key for the password                                                                                                                                                              |
| `importJob.themesAuth.secret`         | String          | ""         | name of the secret                                                                                                                                                                       |
| `importJob.themesAuth.username`       | String          | `username` | secret key for the username                                                                                                                                                              |
| `importJob.themesUrl`                 | string          | ""         | URL of the frontend.zip to be imported, used by `import-themes`                                                                                                                          |

The `milestone` property determines at which milestone a configured job will be started. After the job has completed, the milestone will be advanced.

### Component `user-changes`

The User Changes application.

### Component `workflow-server`

The Workflow Server application.

The Solr collection is `studio`.