# Custom Resource CoreMediaContentClouds

The Custom Resource `CoreMediaContentClouds` (`cmcc` for short) defines all aspects of a CoreMedia Content Cloud
installation to be deployed. This document explains all properties and their use.

<!-- npx markdown-toc --maxdepth 3 -i docs/custom-resource.md-->

# Table of Contents

<!-- toc -->

- [Custom Resource Properties Status](#custom-resource-properties-status)
- [Custom Resource Properties Specification](#custom-resource-properties-specification)
- [Enabling Convenience Options `with`](#enabling-convenience-options-with)
  * [Local database servers `with.databases`](#local-database-servers-withdatabases)
  * [Delivery Components `with.delivery`](#delivery-components-withdelivery)
  * [Management Components `with.management`](#management-components-withmanagement)
- [Using Pre-Existing Secrets](#using-pre-existing-secrets)
  * [Overview of Secrets for Components](#overview-of-secrets-for-components)
  * [`clientSecretRef.jdbc`](#clientsecretrefjdbc)
  * [`clientSecretRef.mongodb`](#clientsecretrefmongodb)
  * [`clientSecretRef.solr`](#clientsecretrefsolr)
  * [`clientSecretRef.uapi`](#clientsecretrefuapi)
- [Running Additional Jobs](#running-additional-jobs)
  * [Running a Job During Deployment](#running-a-job-during-deployment)
  * [Running a Job After Deployment](#running-a-job-after-deployment)
- [Automatic Generation of Ingresses and Site Mappings `siteMappings`](#automatic-generation-of-ingresses-and-site-mappings-sitemappings)
  * [Site Mappings](#site-mappings)
  * [FQDN Aliases](#fqdn-aliases)
  * [Configuring the Ingress Builder](#configuring-the-ingress-builder)
  * [Ingress Annotations](#ingress-annotations)
- [Components](#components)
  * [Component `blob-server`](#component-blob-server)
  * [Component `content-feeder`](#component-content-feeder)
  * [Component `cae`](#component-cae)
  * [Component `cae-feeder`](#component-cae-feeder)
  * [Component `content-server`](#component-content-server)
  * [Component `overview`](#component-overview)
  * [Component `elastic-worker`](#component-elastic-worker)
  * [Component `generic-client`](#component-generic-client)
  * [Component `mongodb`](#component-mongodb)
  * [Component `mysql`](#component-mysql)
  * [Component `nginx`](#component-nginx)
  * [Component `solr`](#component-solr)
  * [Component `studio-client`](#component-studio-client)
  * [Component `studio-server`](#component-studio-server)
  * [Component `management-tools`](#component-management-tools)
  * [Component `user-changes`](#component-user-changes)
  * [Component `workflow-server`](#component-workflow-server)

<!-- tocstop -->

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
7. `Healing`: The milestone was `Ready`, but at least one of the components became unhealthy. Once all components are
   running again, the milestone will become `Ready` again.
8. `RunJob`: a job is currently executing. The milestone will reach `Ready` again after it has finished.
9. `Never`: Special state that will never be reached, can be used on components to define them, but have the operator
   never create the resources for them. See below [Running Additional Jobs](#running-additional-jobs).

## Custom Resource Properties Specification

The `spec` field defines these properties to allow you to deploy a CoreMedia installation. Whenever possible, these
properties have suitable defaults.

| Property                            | Type                 | Default                                           | Description                                                                                                                                  |
|-------------------------------------|----------------------|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `comment`                           | String               | ""                                                | Arbitrary comment, can be used to force an update to the resource                                                                            |
| `components`                        | array                | []                                                | List of CoreMedia components to be created. See below for available components and their parameters                                          |
| `clientSecretRefs`                  | map of map of object | –                                                 | Pre-existing secrets to use, see below                                                                                                       |
| `defaults`                          | object               | –                                                 | Default values for components                                                                                                                |
| `defaults.annotations`              | object               | –                                                 | Annotations to apply to all components' pods.                                                                                                |
| `defaults.curlImage`                | String               | `docker.io/alpine/curl:latest`                    | A Docker image with curl available. Used in init containers to wait for Content Server components to become available.                       |
| `defaults.image`                    | object               | –                                                 | Defaults for the image specification                                                                                                         |
| `defaults.image.registry`           | String               | ""                                                | Docker Image Registry to pull images from                                                                                                    |
| `defaults.image.tag`                | String               | `latest`                                          | Docker Image Tag to pull images from                                                                                                         |
| `defaults.image.pullPolicy`         | String               | `IfNotPresent`                                    | default imagePullPolicy                                                                                                                      |
| `defaults.ingressDomain`            | String               | ""                                                | Fully qualified domain name to append to ingress host names                                                                                  |
| `defaults.insecureDatabasePassword` | String               | ""                                                | **DO NOT SET**. See below for more information.                                                                                              |
| `defaults.javaOpts`                 | String               | `-XX:MinRAMPercentage=75 -XX:MaxRAMPercentage=90` | For Java components, use these JVM options.                                                                                                  |
| `defaults.namePrefix`               | String               | ""                                                | Prefix resources with this name plus '-'.                                                                                                    |
| `defaults.previewHostname`          | String               | `preview`                                         | Hostname of the preview CAE. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended. |
| `defaults.resources`                | resources            | –                                                 | Default resource limits and requests for components. See below [Components](#components)                                                     |
| `defaults.studioHostname`           | String               | `studio`                                          | Hostname of the Studio. Unless it is a fully-qualified domain name, the `namePrefix` and the `ingressDomain` will be pre- and appended.      |
| `defaultIngressTls`                 | object               | –                                                 | Defaults for the site mapping TLS settings, see below                                                                                        |
| `job`                               | String               | ""                                                | name of a component to run as a job, see below                                                                                               |
| `licenseSecrets`                    | object               | –                                                 | Names of the secrets containing the license                                                                                                  |
| `licenseSecrets.CMSLicense`         | String               | `license-cms`                                     | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `licenseSecrets.MLSLicense`         | String               | `license-mls`                                     | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `licenseSecrets.RLSLicense`         | String               | `license-rls`                                     | Name of the secret containing a `license.zip` entry with the appropriate file contents                                                       |
| `siteMappings`                      | array                | –                                                 | Mappings between DNS names and site segments, see below                                                                                      |
| `with`                              | object               | –                                                 | Optional special components and configurations                                                                                               |
| `with.databases`                    | boolean              | false                                             | Create both a MariaDB and MongoDB server, and schemas and secrets for all components that require them                                       |
| `with.databasesOverride`            | object               | –                                                 | If `with.databases` is `true`, override the creation for specific kinds.                                                                     |
| `with.databasesOverride.`*kind*     | boolean              | true                                              | When set to `false`, do not create database and secrets for *kind*. If set to true, or the entry is missing, do create them.                 |
| `with.delivery`                     | object               | –                                                 | Create all components required for a CoreMedia delivery stage                                                                                |
| `with.delivery.rls`                 | int                  | 0                                                 | Number of Replication Live Servers to create                                                                                                 |
| `with.delivery.minCae`              | int                  | 0                                                 | Minimum number of CAEs                                                                                                                       |
| `with.delivery.maxCae`              | int                  | 0                                                 | Maximum number of CAEs                                                                                                                       |
| `with.ingressAnnotations`           | map                  | –                                                 | Additional annotation to add to all Ingress resources                                                                                        |
| `with.ingressSeoHandler`            | String               | `/blueprint/servlet/service/robots`               | Path to handler that will receive requests for `robots.txt` and `sitemap.xml`.                                                               |
| `with.management`                   | boolean              | true                                              | Create all components required for a CoreMedia management stage                                                                              |
| `with.resources`                    | boolean              | true                                              | Apply resource limits and requests to all components. Also see `defaults.resources` and  [Components](#components)                           |

## Enabling Convenience Options `with`

### Local database servers `with.databases`

When `with.databases` is enabled, the operator will add a MariaDB and a MongoDB server to the components, and create
appropriate database schemas and users, including the secrets needed for the components to access them.

The schema/database name as well as the username is automatically determined by the component, see below. The password
for each of these accounts is generated randomly on creation.

If you would like to connect to the databases with a constant password, you can set `defaults.insecureDatabasePassword`
. **DANGER You should only set this property if you are certain that the database server is only accessible over the
network to authorized users.**

You can further customize which databases and secrets are created by setting `with.databasesOverride.`*kind* to `false`
for those kinds that you do want to manage the services and secrets yourself.
See [Overview of Secrets for Components](#overview-of-secrets-for-components) for a list of kinds.

### Delivery Components `with.delivery`

The operator can create Replication Live Servers and Live CAEs.

`with.delivery.minCae` and `with.delivery.maxCae` determine how many Live CAEs should be created. The defaults are
both `0`, which disables the creation of Live CAEs. When `with.delivery.minCae` is set to a value equal or larger
than `with.delivery.maxCae`, that fixed number of Live CAEs will be configured.

`with.delivery.rls` determines the number of Replication Live Servers the operator should create. The default of `0`
means that all CAEs will be connected to the Master Live Server, and no RLS will be created. CAEs connect to the RLS
through a service that load-balances connections to all available RLS.

**Note**: While it is possible to increase the number of RLS after the CoreMedia installation has been created, the
operator currently cannot create additional database schemas for any new RLS, and can not clone database contents from
the Master Live Server to any new RLS. If you want to increase the number of RLS after initial setup, you would need to
take care of that manually.

**Future functionality**: When `with.delivery.minCae` is set to a value smaller than `with.delivery.maxCae`, a
horizontal pod autoscaler will be configured that will scale the number of CAEs from the minimum to the maximum amount
based on the CPU load of the CAEs.

#### Example: development setup with one Live CAE

`with.delivery.rls=0`, `with.delivery.minCae=1`, `with.delivery.maxCae=0` will create exactly one CAE which will be
connected to the Master Live Server.

#### Example: two RLS and two CAEs

`with.delivery.rls=2`, `with.delivery.minCae=2`, `with.delivery.maxCae=0` will create two RLS and two CAEs.

#### Example: two RLS with auto scaling (future functionality)

`with.delivery.rls=2`, `with.delivery.minCae=1`, `with.delivery.maxCae=10` will create two RLS and one CAE. A horizontal
pod autoscaler will be set up that will scale the number of CAEs to up to 10.

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

| Component type/kind    | `jdbc`        | `mongodb`   | `solr`    | `uapi`      | Description |
|------------------------|---------------|-------------|-----------|-------------|-------------|
| `content-server`/`cms` | `management`  |             |           | `publisher` | To the MLS  |
| `content-server`/`mls` | `master`      |             |           |             |             |
| `content-server`/`rls` | `replication` |             |           | `publisher` | To the MLS  |
| `cae`/`live`           |               | `blueprint` | `live`    | `webserver` |             |
| `cae`/`preview`        |               | `blueprint` | `preview` | `webserver` |             |
| `cae-feeder`/`live`    | `mcaefeeder`  | `blueprint` | `live`    | `webserver` |             |
| `cae-feeder`/`preview` | `caefeeder`   | `blueprint` | `preview` | `webserver` |             |
| `content-feeder`       |               | `blueprint` | `studio`  | `feeder`    |             |
| `elastic-worker`       |               | `blueprint` | `studio`  | `webserver` |             |
| `studio-server`        | `studio`      | `blueprint` | `studio`  | `studio`    |             |
| `user-changes`         |               | `blueprint` | `         | `studio`    |             |
| `workflow-server`      | `management`  | `blueprint` |           | `workflow`  |             |

You can override the schema/user names for each component by adding an entry to the components `schemas` map, for
example:

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

### `clientSecretRef.solr`

While Solr does not use authentication (unless explicitly enabled through a plugin), the operator can use secrets to
configure the Solr Core/collection name and server URL. For each collection, two secrets are used: *schemaname*`-leader`
and *schemaname*`-follower`. The leader can be used to connect to the Solr leader (used for indexing and quick
turnaround querying in the Studio and preview); the follower secret connects to the follower instances that replicate
the cores from the leader, for example in the Live CAE.

If any of the following secret references are defined in `clientSecretRefs.solr`, the operator will configure the client
components to use the `url` property from each for the connection string. If no such secret reference is defined, the
connection URL will be set directly based on the Solr service names.

* `solr-live-follower`
* `solr-live-leader`
* `solr-preview-leader`
* `solr-studio-leader`

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

| Property             | Type            | Default | Description                                                                                                                                                          |
|----------------------|-----------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hostname`           | String          | –       | Short name of the mapping; also used as the key for this entry.                                                                                                      |
| `fqdn`               | String          | –       | DNS name, either a fully-qualified domain name (FQDN, www.example.com). Defaults to the `hostname` with the default ingress domain appended.                         |
| `fqdnAliases`        | array of String | –       | Additional FQDNs that should also map to this host. Note that the CAE will only generate links to `fqdn`.                                                            |
| `primarySegment`     | String          | –       | Primary site segment; this is the site for the `/` URI.                                                                                                              |
| `additionalSegments` | array of String | –       | Segments of additional sites served from this host.                                                                                                                  |
| `tls`                | object          | –       | TLS settings for this host                                                                                                                                           |
| `tls.enabled`        | boolean         | true    | Should TLS be enabled for this ingress                                                                                                                               |
| `tls.secretName`     | object          | –       | Name of the secret that stores the certificate and key for this hostname. Some Ingress controllers allow this to be empty, they will then use a default certificate. |

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

### FQDN Aliases

In certain situations, it might be beneficial to have the Ingress controller recognize more than one hostname for a
site. For example, you might want your monitoring system to use a direct access to the Kubernetes cluster, while end
users access the site through a load balancer, TLS termination appliance, or a web-application firewall. Or you might
need to configure an origin URL for your CDN to use.

You can define additional hostnames with `siteMappings.fqdnAliases`. For each of these hosts, a set of Ingress
resources will be built. You can use the empty string for an alias entry to have the operator generate a hostname, as
described above.

**Note** The CAE will only create URLs pointing to the FQDN of the site, so you will need to make sure that those URLs
always work.

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

### Ingress Annotations

In some cases, it might be necessary to add additional annotations to the generated Ingress object. For example, to
enable sticky sessions, the annotation `nginx.ingress.kubernetes.io/affinity: cookie` needs to be added. You can
add these by setting `with.ingressAnnotations` to the desired annotations.

```yaml
with:
  ingressAnnotations:
     "nginx.ingress.kubernetes.io/affinity": "cookie"
```

### `robots.txt` and `sitemap.xml`

In order for search engines to properly index a site, a 
[`robots.txt`](https://en.wikipedia.org/wiki/Robots_exclusion_standard) and one or more 
[XML Sitemaps](https://en.wikipedia.org/wiki/Sitemaps) should be made available at specific URLs. The operator generates
ingress rules for the preview and all live sites, mapping requests for `robots.txt` and `sitemap.*\.xml` to the
value of `with.ingressSeoHandler` plus the site segment (or `preview` in case of the preview), plus the file name
requested. You will need to supply a compatible handler in your CAE to handle requests for it.

For example, the request `https://corporate.example.de/sitemap-0.xml` will be mapped to 
`/blueprint/servlet/service/robots/corporate-de-de/sitemap-0.xml`.

## Components

`components` specifies a list of CoreMedia components and their parameters. The only required parameter is `type`, which
specifies the type of component, and for some component types, `kind` as a sub-type.

| Property           | Type           | Default         | Description                                                                                                                                  |
|--------------------|----------------|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `type`             | String         | –               | Required type of the component                                                                                                               |
| `kind`             | String         | –               | Sub-type, required for some component types                                                                                                  |
| `name`             | String         | type            | The name of the component and the resources created for it. Defaults to a type-specific name, typically the type itself                      |
| `annotations`      | object         | –               | Additional annotations to add to the pods of this component.                                                                                 |
| `args`             | list of String | –               | Args for the main container of the main pod. Defaults to unset, using the default from the image.                                            |
| `env`              | list of EnvVar | –               | Additional environment variables to be made available to the containers                                                                      |
| `image`            | object         | –               | Specification of the Docker Image to use for the main container of the main pod of the component                                             |
| `image.registry`   | String         | `coremedia`     | Docker Image Registry to pull images from                                                                                                    |
| `image.repository` | String         | see description | Docker Image Repository to pull images from. Default is type-specific based on the standard blueprint image names.                           |
| `image.tag`        | String         | latest          | Docker Image Tag to pull images from                                                                                                         |
| `image.pullPolicy` | String         | `IfNotPresent`  | default imagePullPolicy                                                                                                                      |
| `milestone`        | enum           | `DatabaseReady` | Milestone that has to be reached before this component gets created.                                                                         |
| `resources`        | object         | -               | `limits` and `resources`                                                                                                                     | 
| `schemas`          | map            | –               | The name of the JDBC, MongoDB, and/or UAPI schemas that should be used for this component. Overrides the built-in default for the component. |

### Specification

#### Milestone

The milestone determines at which point during the bringup the component will be added. You can set the
milestone `Never` to define but disable a component.

#### Resources

Each Kubernetes pod is optionally subject to resource management.
See [Workload Resources, Pod](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/#resources)
for details. The object consists of two properties, `limits` and `requests`, which in turn each have properties
according to the cluster Kubernetes version. Typically, you will want to set `cpu` and `memory`.

You can use `defaults.resources` to define resource limits and requests for all pods, and you can specify individual
values for each component. If a component has more than one pod, all pods receive the same resources.

You can disable applying resource requirements by setting `with.resources` to `false`; in this case, all resource
specifications are ignored.

Example:
```yaml
components:
  - type: cae
    kind: preview
    resources:
      limits:
        cpu: 500m
defaults:
  resources:
    limits:
      cpu: 2
```

CPU time is limited to 2 cores by default; the preview CAE will only be allowed half a core.

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

### Component `generic-client`

The Generic-Client can be used for a variety of applications that require connections to the content-server, mongodb and
solr. Most of its implementation is provided by the abstract CorbaComponent, resulting in persistent volume claims to
standard coremedia caches as well as a service that exposes 8080, 8081 and 8083 ports. Thus, supplying an
image-repository via the component-spec is mandatory, as the component class can not presume the correct repository
beforehand. Most of its base configuration can be set in the component's `extra` section, using the following keys:

| Property                | Type      | Optional  | Default                     | Description                                                                                                                                                                                                                                    |
|-------------------------|-----------|-----------|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `uapi-connection`       | String    | no        | -                           | The uapi-user that is used to authenticate against the content-server (ends up as repository.user env).                                                                                                                                        |
| `content-server-type`   | String    | yes       | `cms`                       | Set the value to `mls` for establishing a connection to the master-live-server instead of the content-management-server.                                                                                                                       |
| `solr-server`           | String    | no        | -                           | Specifies the solr server type that is utilized by the client. Can be either `leader` or `follower`.                                                                                                                                           |
| `solr-collection`       | String    | no        | -                           | The collection that solr should use as the index.                                                                                                                                                                                              |
| `solr-collection-prefix`| String    | yes       | upper-case name of component| The prefix used for writing the solr-collection as an env var as in `SOLR_${solr-collection-prefix}_COLLECTION`. Setting the parameter to CAE is helpful for apps that establish a solr connection using the cm SolrCaeConfigurationProperties.|

### Component `mongodb`

If `with.databases` is enabled, the operator creates a MongoDB instance and the necessary secrets for the components to
access it.

### Component `mysql`

If `with.databases` is enabled, the operator creates a MySQL instance and the necessary secrets for the components to
access it.

### Component `nginx`

Runs an NGINX web server image, for example, to make static files available within the cluster. The server is expected
to run on port 80; the environment variable NGINX_PORT is set to enable that.

### Component `solr`

The Solr component creates one or more Solr instances, controlled by the `extra.replicas` property.
With `extra.replicas=1`, a single Solr leader instance is created. With `extra.replicas=2` or higher, one or more
follower instances are created that replicate the leader automatically.

The operator will automatically create the cores in the followers, by executing the core admin API request, as
documented in the Search Manual.

The operator creates two services for Solr:

* `solr`, which maps to both the leader and any followers, suitable for any component that only wants to query the
  index, and
* `solr-leader` that maps to just the leader, suitable for components that need to update an index.

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