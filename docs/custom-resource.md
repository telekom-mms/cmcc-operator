# Custom Resource CoreMediaContentClouds

The Custom Resource `CoreMediaContentClouds` (`cmcc` for short) defines all aspects of a CoreMedia Content Cloud
installation to be deployed. This document explains all properties and their use.

## Custom Resource Properties Status

The `status` property of the custom resource has these fields:

| Property         | Type   | Description                                                            |
|------------------|--------|------------------------------------------------------------------------|
| `milestone`      | enum   | Which milestone has been reached in configuring all components         |
| `error`          | String | A one-line error message, or empty string                              |
| `errorMessage`   | String | A longer error message (if any)                                        |
| `job`            | String | The name of the job component currently executing, or an empty string. |
| `ownedResources` | String | Used internally by the operator to keep track of created resources.    |

The `milestone` status column shows the creation status of the installation:

1. `Created`: The initial databases are being created (if requested by `with.databases`)
2. `DatabasesReady`: The databases are running, all schemas have been created. The core management components are being
   started (CMS, MLS).
3. `ContentserverInitialized`: The Content Management Server and the Master Live Server have started for the first time.
   An initial job can be run to import users or do other basic housekeeping for a fresh instance. Once this milestone
   has completed all jobs, the CMS and the MLS will be restarted.
4. `ContentserverReady`: The Content Management Server and the Master Live Server are running.
5. `ManagementReady`: The core management components are running. All remaining component are being started, including
   the content import.
6. `Ready`: The content import has completed, all components are up and running.
7. `Never`: Special state that will never be reached, can be used on components to define them, but have the operator
   never create the resources for them. See below [Running Additional Jobs](#running-additional-jobs).

## Custom Resource Properties Specification

The `spec` field defines these properties to allow you to deploy a CoreMedia installation. Whenever possible, these
properties have suitable defaults.

| Property                            | Type                 | Default                        | Description                                                                                                                                  |
|-------------------------------------|----------------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `comment`                           | String               | ""                             | Arbitrary comment, can be used to force an update to the resource                                                                            |
| `components`                        | array                | []                             | List of CoreMedia components to be created. See below for available components and their parameters                                          |
| `clientSecretRefs`                  | map of map of object | –                              | Pre-existing secrets to use, see below                                                                                                       |
| `defaults`                          | object               | –                              | Default values for components                                                                                                                |
| `defaults.curlImage`                | String               | `docker.io/alpine/curl:latest` | A Docker image with curl available. Used in init containers to wait for Content Server components to become available.                       |
| `defaults.image`                    | object               | –                              | Defaults for the image specification                                                                                                         |
| `defaults.image.registry`           | String               | ""                             | Docker Image Registry to pull images from                                                                                                    |
| `defaults.image.tag`                | String               | `latest`                       | Docker Image Tag to pull images from                                                                                                         |
| `defaults.image.pullPolicy`         | String               | `IfNotPresent`                 | default imagePullPolicy                                                                                                                      |
| `defaults.ingressDomain`            | String               | ""                             | Fully qualified domain name to append to ingress host names                                                                                  |
| `defaults.insecureDatabasePassword` | String               | ""                             | **DO NOT SET**. See below for more information.                                                                                              |
| `defaults.namePrefix`               | String               | ""                             | Prefix resources with this name plus '-'.                                                                                                    |
| `defaults.previewHostname`          | String               | `preview`                      | Hostname of the preview CAE. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended. |
| `defaults.resources`                | resources            | –                              | Default [resources to apply to component pods](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#resources)     |
| `defaults.studioHostname`           | String               | `studio`                       | Hostname of the Studio. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended.      |
| `defaultIngressTls`                 | object               | –                              | Defaults for the site mapping TLS settings, see below                                                                                        |
| `job`                               | String               | ""                             | name of a component to run as a job, see below                                                                                               |
| `licenseSecrets`                    | object               | –                              | Names of the secrets containing the license                                                                                                  |
| `licenseSecrets.CMSLicense`         | String               | `license-cms`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `licenseSecrets.MLSLicense`         | String               | `license-mls`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `licenseSecrets.RLSLicense`         | String               | `license-rls`                  | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `siteMappings`                      | array                | –                              | Mappings between DNS names and site segments, see below                                                                                      |
| `with`                              | object               | –                              | Optional special components and configurations                                                                                               |
| `with.databases`                    | boolean              | false                          | Create both a MariaDB and MongoDB server, and schemas and secrets for all components that require them                                       |
| `with.delivery`                     | object               | –                              | Create all components required for a CoreMedia delivery stage                                                                                |
| `with.delivery.rls`                 | int                  | 0                              | Number of Replication Live Servers to create                                                                                                 |
| `with.delivery.minCae`              | int                  | 0                              | Minimum number of CAEs per RLS                                                                                                               |
| `with.delivery.maxCae`              | int                  | 0                              | Maximum number of CAEs per RLS                                                                                                               |
| `with.management`                   | boolean              | true                           | Create all components required for a CoreMedia management stage                                                                              |

## Enabling Convenience Options `with`

### Local database servers `with.databases`

When `with.databases` is enabled, the operator will add a MariaDB and a MongoDB server to the components, and create
appropriate database schemas and users, including the secrets needed for the components to access them.

The schema/database name as well as the username is automatically determined by the component, see below. The password
for each of these accounts is generated randomly on creation.

If you would like to connect to the databases with a constant password, you can set `defaults.insecureDatabasePassword`
. **DANGER You should only set this property if you are certain that the database server is only accessible over the
network to authorized users.**

### Delivery Components `with.delivery`

The operator can create Replication Live Servers and Live CAEs and configure a Horizontal Pod Autoscaler for the CAEs.

`with.delivery.rls` determines the number of Replication Live Servers the operator should create. The default of `0`
means that all CAEs will be connected to the Master Live Server, and no RLS will be created. Note that while it is
possible to increase the number of RLS after the CoreMedia installation has been created, the operator currently cannot
create additional database schemas for any new RLS, and can not clone database contents from the Master Live Server to
any new RLS. If you want to increase the number of RLS after initial setup, you would need to take care of that
manually.

`with.delivery.minCae` and `with.delivery.maxCae` determine how many Live CAEs should be created per RLS. The defaults
are both `0`, which disables the creation of Live CAEs. When `with.delivery.minCae` is set to a value equal or larger
than `with.delivery.maxCae`, a fixed number of Live CAEs will be configured. When `with.delivery.minCae` is set to a
value smaller than `with.delivery.maxCae`, a horizontal pod autoscaler will be configured that will scale the number of
CAEs from the minimum to the maximum amount based on the CPU load of the CAEs.

**Note** At this time, the operator can only configure a single Live CAE connected to the Master Live Server; any other
configuration will lead to an error.

#### Example: development setup with one Live CAE

`with.delivery.rls=0`, `with.delivery.minCae=1`, `with.delivery.maxCae=0` will create exactly one CAE which will be
connected to the Master Live Server.

#### Example: two RLS with auto scaling

`with.delivery.rls=2`, `with.delivery.minCae=1`, `with.delivery.maxCae=10` will create two RLS and one CAE each. A HPA
will be set up that will scale the number of CAEs to up to 10 per RLS.

### Management Components `with.management`

When `with.management` is enabled, the operator automatically adds all required components to the list of components.
This is equivalent to configuring:

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

You can override settings for individual components, for example the image specification, by declaring that component
explicitly.

## Using Pre-Existing Secrets

Unless `with.databases` is enabled, you will need to provide secrets for all components for all connections: JDBC
databases, MongoDB databases, and UAPI servers. You reference existing secrets in the custom resource by
providing `clientSecretRef` entries. Each of the three entries contains a map of secret references, one entry per client
or schema. Each entry can have these fields:

| Key           | Default    | Key in the secret                         |
|---------------|:-----------|-------------------------------------------|
| `secretName`  | –          | name of the secret containing the details |
| `driverKey`   | `driver`   | database driver class name (JDBC)         |
| `hostnameKey` | `hostname` | hostname of the server                    |
| `passwordKey` | `password` | password to log in to the server          |
| `schemaKey`   | `schema`   | schema or client or service               |
| `urlKey`      | `url`      | connection URL                            |
| `usernameKey` | `username` | username to log in to the server          |

Components receive secrets as environment variables. See the CoreMedia documentation (Deployment Manual) for the
properties that the components take for database configuration.

### Overview of Secrets for Components

This tables shows the component types and the client secrets they use.

| Component type/kind    | `jdbc`        | `mongodb`   | `uapi`      | Description |
|------------------------|---------------|-------------|-------------|-------------|
| `content-server`/`cms` | `management`  |             | `publisher` | To the MLS  |
| `content-server`/`mls` | `master`      |             |             |             |
| `content-server`/`rls` | `replication` |             | `publisher` | To the MLS  |
| `cae`/`live`           |               | `blueprint` | `webserver` |             |
| `cae`/`preview`        |               | `blueprint` | `webserver` |             |
| `cae-feeder`/`live`    | `caefeeder`   | `blueprint` | `webserver` |             |
| `cae-feeder`/`preview` | `mcaefeeder`  | `blueprint` | `webserver` |             |
| `content-feeder`       |               | `blueprint` | `feeder`    |             |
| `elastic-worker`       |               | `blueprint` | `webserver` |             |
| `studio-server`        | `studio`      | `blueprint` | `studio`    |             |
| `user-changes`         |               | `blueprint` | `studio`    |             |
| `workflow-server`      | `management`  | `blueprint` | `workflow`  |             |

You can override the schema/user names for each component by adding an entry to the components `schemas` map, for example:

```yaml
components:
   - type: cae-feeder
     kind: live
     schemas:
       jdbc: live-cae-feeder
```


### `clientSecretRef.jdbc`

Depending on the component, different environment variables are set. All keys are used and must be specified for the
database connection to work correctly.

With the default set of components (`with.management` and `with.delivery`), you will need to provide these secret refs:

* caefeeder
* management
* master
* mcaefeeder
* studio

For example, to configure a secret for the Content Management Server which uses the `management` name:

```yaml
...
clientSecretRef:
  jdbc:
    management:
      secretName: mysql-management
...
```

This using all the default keys for the secret (see above).

For example, you can create a suitable secret on the command line, entering the appropriate details for your database
server as needed:

```shell
kubectl create secret generic mysql-management \
  --from-literal=username=cm_management \
  --from-literal=password='s3cr3t' \
  --from-literal=driver=com.mysql.cj.jdbc.Driver \
  --from-literal=hostname=mysql.example.com
  --from-literal=schema=cm_management
  --from-literal=url=jdbc:mysql://mysql.example.com:3306/cm_management
```

### `clientSecretRef.mongodb`

The components only use the `urlKey` to configure the MongoDB client. You must provide the authentication in the URL, if
the MongoDB server requires it.

### `clientSecretRef.uapi`

Unless specified explicitly here, the operator will create random passwords for the UAPI connection and will initialize
both the Content Management Server and the Master Live Server with these secrets. This includes the `admin` user. If you
would like to set a well-known admin password, create a secret:

```shell
kubectl create secret generic coremedia-admin \
  --from-literal=username=admin \
  --from-literal=password='s3cr3t'
```

Then reference it in the custom resource:

```yaml
...
clientSecretRef:
  uapi:
    admin:
      secretName: coremedia-admin
      usernameKey: username
      passwordKey: password
...
```

## Running Additional Jobs

You can add jobs to the components to be run. By default, there is one job type `management-tools` that runs
the `global/management-tools` image to import content, themes, and users, and publish imported content.
See [Running the Tools](https://documentation.coremedia.com/cmcc-11/artifacts/2201/webhelp/deployment-en/content/_running_the_tools.html)
in the Deployment Manual for details.

Jobs can take additional configuration options. See [Component `management-tools`](#component-management-tools) for the
configuration of that job type.

### Running a Job During Deployment

To run a job during deployment, simply add it to the list of components, like so:

```yaml
  - name: import
    type: management-tools
    milestone: ManagementReady
    args:
      - use-remote-content-archive
      - import-themes
      - import-content
      - publish-content
    extra:
      config: |
        contentUsersUrl: "http://gitlab.example.com/..."
        themesUrl: "http://gitlab.example.com/..."
```

The `milestone` defines when to run this job.

### Running a Job After Deployment

If you'd like to run a job after the initial deployment has succeeded, and the milestone `Ready` has been reached, you
can define the job template for the job using the milestone `Never` when adding it to the list of components.

```yaml
  - name: reimport
    type: management-tools
    milestone: Never
    args:
      - use-remote-content-archive
      - import-themes
      - import-content
      - publish-content
    extra:
      config: |
        contentUsersUrl: "http://gitlab.example.com/..."
        themesUrl: "http://gitlab.example.com/..."
```

To trigger running the job, set the `job` property to the name of the job you'd like to run. You can use `kubectl patch`
to set this property.

```shell
kubectl patch cmcc example --type merge --patch "{\"spec\":{\"job\":\"reimport\"}}"
```

While the job is running, the milestone will be `RunJob`. Once the job completes, the milestone will return to `Ready`.

## Automatic Generation of Ingresses and Site Mappings `siteMappings`

The operator automatically creates ingresses for all components that need to be exposed: the Studio (combined for
studio-client and studio-server), the preview CAE and the live CAE.

For the CAEs, the website domains and the site root channel's URI segments need to be mapped to each other: the ingress
controller needs to rewrite URLs so the CAE can interpret them, and the CAE needs to generate URLs so they map back to
the CAE in the right way. The array of objects `siteMappings` defines the mapping, and the operator creates both the
ingress objects and the mapping properties for the live CAE from them.

The operator can only generate Ingress resources compatible with
the [kubernetes/ingress-nginx](https://github.com/kubernetes/ingress-nginx).

### Site Mappings

Each entry defines one DNS name and all the site segments that will be served under this name.

| Property             | Type            | Default | Description                                                                                                                                                         |
|----------------------|-----------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hostname`           | String          | –       | Short name of the mapping; also used as the key for this entry.                                                                                                     |
| `fqdn`               | String          | –       | DNS name, either a fully-qualified domain name (FQDN, www.example.com). Defaults to the `hostname` with the default ingress domain appended.                        |
| `primarySegment`     | String          | –       | Primary site segment; this is the site for the `/` URI.                                                                                                             |
| `additionalSegments` | array of String | –       | Segments of additional sites served from this host.                                                                                                                 |
| `tls`                | object          | –       | TLS settings for this host                                                                                                                                          |
| `tls.enabled`        | boolean         | true    | Should TLS be enabled for this ingress                                                                                                                              |
| `tls.secretName`     | object          | –       | Name of the secret that store the certificate and key for this hostname. Some Ingress controllers allow this to be empty, they will then use a default certificate. |

If you have a single certificate for your setup (for example, a certificate with SNI for all hosts, or a wildcard
certificate), you can simply configure `defaultIngressTls.secretName` with the name of that secret, and leave out
the `tls` settings on the individual site mapping entries.

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

### Configuring the Ingress Builder

The operator supports two different schemes for mapping live URLs to CAE URIs: the default `blueprint` scheme (the
default Link Building Scheme in the CAE, see above), and `onlylang`. You configure the ingress builder by setting the
application property `cmcc.ingressbuilder`, for example by adding the `CMCC_INGRESSBUILDER` environment variable to the
deployment resource of the operator.

The `onlylang` ingress builder maps hostnames of the form *countrysite*/*lang* to URIs of the form *sitesegment*-*lang*
-*locale*. Consider these `siteMappings`:

```yaml
siteMappings:
  - hostname: corporate.example.de
    primarySegment: corporate-de-de
  - hostname: corporate.example.ca
    primarySegment: corporate-en-ca
    additionalSegments:
      - corporate-fr-ca
```

This will create ingresses for:

* `https://corporate.example.de/` redirects to `/de/`
* `https://corporate.example.de/de` maps to `corporate-de-de`
* `https://corporate.example.ca/` redirects to `/en`
* `https://corporate.example.ca/en` maps to `corporate-en-ca`
* `https://corporate.example.ca/fr` maps to `corporate-en-fr`

**Note** You will need to your own code in the CAE to have it generate links in this form.

## Components

`components` specifies a list of CoreMedia components and their parameters. The only required parameter is `type`, which
specifies the type of component, and for some component types, `kind` as a sub-type.

| Property           | Type           | Default         | Description                                                                                                                                  |
|--------------------|----------------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `type`             | String         | –               | Required type of the component                                                                                                               |
| `kind`             | String         | –               | Sub-type, required for some component types                                                                                                  |
| `name`             | String         | type            | The name of the component and the resources created for it. Defaults to a type-specific name, typically the type itself                      |
| `image`            | object         | –               | Specification of the Docker Image to use for the main container of the main pod of the component                                             |
| `image.registry`   | String         | `coremedia`     | Docker Image Registry to pull images from                                                                                                    |
| `image.repository` | String         | see description | Docker Image Repository to pull images from. Default is type-specific based on the standard blueprint image names.                           |
| `image.tag`        | String         | latest          | Docker Image Tag to pull images from                                                                                                         |
| `image.pullPolicy` | String         | `IfNotPresent`  | default imagePullPolicy                                                                                                                      |
| `milestone`        | enum           | `DatabaseReady` | Milestone that has to be reached before this component gets created.                                                                         |
| `env`              | list of EnvVar | –               | Additional environment variables to be made available to the containers                                                                      |
| `args`             | list of String | –               | Args for the main container of the main pod. Defaults to unset, using the default from the image.                                            |
| `schemas`          | map            | –               | The name of the JDBC, MongoDB, and/or UAPI schemas that should be used for this component. Overrides the built-in default for the component. |

You can set the milestone `Never` to define but disable a component.

### Component `blob-server`

CoreMedia can make use of an external storage for blobs (images, videos, etc.), and add only references to those blobs
during content import, instead of importing the data itself. Enabling this option will bring up a blob HTTP server, and
configure the import job to use the appropriate settings. This is particularly useful for the CoreMedia-provided demo
content. The blob server needs to have all blobs already available to it; `serverimport` will not upload the blobs to
the server.

The default image for this component is `blob-server`. You can override the default image by declaring the component:

```yaml
  components:
    - type: "blob-server"
      image:
        registry: gitlab.example.com/myproject/coremedia-blobs
        repository: coremedia-blobs
        tag: "2201.1"
```

See [github.com/T-Systems-MMS/cmcc-blob-server](https://github.com/T-Systems-MMS/cmcc-blob-server) for an example on how
to create a suitable Docker image.

### Component `content-feeder`

The Content Feeder application.

The Solr collection is `studio`.

### Component `cae`

The CAE type has two kinds: `preview` and `live`. The default image as well as the name are `cae-preview` and `cae-live`
, respectively.

The Solr collection is `preview` and `live`, respectively.

### Component `cae-feeder`

The CAE Feeder type has two kinds: `preview` and `live`. The default image as well as the name are `cae-feeder-preview`
and `cae-feeder-live`, respectively.

The database schema and username defaults to `caefeeder` and `mcaefeeder`, respectively.

The Solr collection is `preview` and `live`, respectively.

### Component `content-server`

The Content Server type has three kinds: `cms`, `mls`, and `rls`, for the three kinds of Content Servers that can be
configured in CoreMedia system.

The default name depends on the kind: `content-management-server` for a `cms`, `master-live-server` for an `mls`,
and `replication-live-server` for an `rls`. Note that if you need to configure multiple RLS, you will need to specify
names yourself.

The database names are `management`, `master`, and `replication`.

### Component `overview`

A static web page exposed through an ingress as `overview`. The operator creates an `/info.json` file and adds a static
HTML page that shows an overview of the externally accessible components. This allows users to quickly locate the
correct links to the Studio, the preview and the live sites.

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

You can customize the overview page by configuring the `extra` entry on the `overview` component to override existing
files and add additional ones:

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

If `with.databases` is enabled, the operator creates a MongoDB instance and the necessary secrets for the components to
access it.

### Component `mysql`

If `with.databases` is enabled, the operator creates a MySQL instance and the necessary secrets for the components to
access it.

### Component `nginx`

Runs an NGINX web server image, for example, to make static files available within the cluster. The server is expected to run on port 80; the environment variable NGINX_PORT is set to enable that.

### Component `solr`

The Solr type has two kinds: `leader` and `follower`. The name defaults to `solr-leader` and `solr-follower`,
respectively. Note that if you want to add more than one follower, you will need to assign individual names to the
components yourself.

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

The `milestone` property determines at which milestone a configured job will be started. After the job has completed,
the milestone will be advanced.

### Component `user-changes`

The User Changes application.

### Component `workflow-server`

The Workflow Server application.

The Solr collection is `studio`.