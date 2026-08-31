# Replacing CORBA for CAE/Headless-to-RLS Connections

## Purpose and Status

This document describes the technical work required to replace the current CORBA-based Unified API connection from live CAE and Headless components to Replication Live Servers (RLS) with gRPC.

It is a design and verification guide, not evidence that the target CoreMedia version permits a stateless delivery topology. The CoreMedia 2512.0.2 documentation establishes gRPC configuration, but it does not state that gRPC removes the connection, event, invalidation, or failover requirements which currently cause RLS affinity.

Use the CoreMedia 2512.0.2 documentation as the product reference. The operator-specific baseline is [Scaling](scaling.md), [Upgrade Path](upgrade-path.md), and [Customizing the Operator](customizing-the-operator.md).

## Current Deployment Model

### Components and Ordering

The operator reconciles a `CoreMediaContentCloud` custom resource into Kubernetes resources. `DefaultTargetState` creates RLS instances as delivery services, then starts live CAE and Headless components at `DeliveryServicesReady`. The ordering is deliberate: rendering workloads may start only after the RLS tier is ready.

The relevant implementation surfaces are:

| Responsibility | Current implementation |
|---|---|
| Desired CoreMedia topology | [DefaultTargetState](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/targetstate/DefaultTargetState.java) |
| Rendering-resource topology and RLS selection | [AbstractRenderingCorbaComponent](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/corba/AbstractRenderingCorbaComponent.java) |
| Shared UAPI/CORBA client properties and ports | [CorbaComponent](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/corba/CorbaComponent.java) |
| RLS state, replication, database mapping, and readiness | [ContentServerComponent](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/corba/ContentServerComponent.java) |
| UAPI credentials supplied from Secrets | [HasUapiClient](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/components/HasUapiClient.java) |
| Spring Boot property serialization | [SpringProperties](../src/main/java/com/tsystemsmms/cmcc/cmccoperator/utils/SpringProperties.java) |

### Why Rendering Is Coupled to RLS Today

When `spec.with.delivery.rls` is greater than zero, live rendering components use an RLS unless `forceMls` is set. `AbstractRenderingCorbaComponent` then:

1. Creates one CAE/Headless StatefulSet per RLS replica rather than one shared rendering StatefulSet.
2. Distributes the requested rendering replicas across those StatefulSets.
3. Replaces the RLS Service hostname with a pod-qualified DNS name such as `replication-live-server-0.replication-live-server` when more than one RLS exists.
4. Writes that IOR URL to `repository.url` for the live rendering component.
5. Changes labels, readiness aggregation, update strategy, and upgrade behavior to preserve the individual RLS-to-rendering delivery stacks.

The current topology is therefore more than a URL choice. It shapes names, selectors, HPA behavior, resource count, readiness, and zero-downtime upgrades.

### What Remains Stateful Regardless of Client Protocol

An RLS is a replicated Content Server with its own database instance, license, replication process, and lifecycle. Replacing the rendering client's transport does not eliminate those RLS responsibilities.

The operator also keeps RLS pods during upgrades, supplies a per-pod database mapping through `POD_INDEX`, and considers the RLS replicator in the startup health group. These behaviors must remain until a separate, evidence-backed architecture decision changes the RLS tier itself.

## CORBA and gRPC Product Facts

### CORBA Baseline

The operator's current client configuration is CORBA oriented:

- `repository.url` points to an IOR URL, for example `http://replication-live-server:8080/ior`.
- The operator exposes IOR, management, and CORBA service ports through `CorbaComponent`.
- RLS replication uses `replicator.publication-ior-url` to reach the Master Live Server (MLS).

CORBA gets its connection details through an HTTP request for the IOR. The CoreMedia documentation requires DNS resolution between all participants for both CORBA and gRPC.

### gRPC Baseline in CoreMedia 2512.0.2

CoreMedia clients can select CORBA or gRPC at component startup. Servers offer both protocols; CORBA remains the documented default in 2512.0.2.

For a Unified API client, gRPC requires the following configuration:

| Setting | Required role | Meaning |
|---|---|---|
| `REPOSITORY_USEGRPC=true` | Environment variable or Java system property | Enables gRPC and disables CORBA for server communication. It is not a Spring Boot property. |
| `spring.grpc.client.channels.cap.address` | Spring property | Content Server gRPC endpoint address. |
| `repository.http-base-uri` | Spring property | HTTP base URI for non-gRPC Content Server servlets, such as blob access. |
| `spring.grpc.client.channels.cap.negotiation-type` | Spring property when TLS is required | gRPC transport security; documented default is `plaintext`. |

For an RLS replicator that itself connects to the MLS through gRPC, the required settings are separate:

| Setting | Meaning |
|---|---|
| `REPOSITORY_USEGRPC=true` | Enables gRPC for the RLS-to-MLS client connection. |
| `spring.grpc.client.channels.cap.address` | MLS gRPC endpoint. |
| `replicator.publication-http-base-uri` | MLS HTTP base URI for the replicator. |

`replicator.publication-ior-url` is effective only for CORBA. Do not remove it while CORBA compatibility remains supported.

The 2512.0.2 documentation also notes that the Workflow Server does not provide gRPC client services in release 2512.0. Mixed-version systems must be checked against the target release notes. A component that needs a Workflow connection may still require CORBA-era URL configuration in that release; do not disable Workflow connections as a shortcut without verifying component requirements.

Detailed vendor references:

- Deployment Manual: Unified API Spring Boot client properties
- Deployment Manual: RLS replicator properties
- Unified API Developer Manual: creating CORBA and gRPC connections
- Content Server Manual: delivery and RLS architecture

## Transport Is Not a Statelessness Guarantee

The desired end state may be a shared RLS Service with independently scalable CAE/Headless workloads. That conclusion must be earned through a product verification, not inferred from gRPC.

The Unified API documentation describes an open `CapConnection` as long-lived and backed by active threads. A gRPC channel may likewise be long-lived, and applications may rely on repository events and invalidations. Therefore distinguish these questions:

| Question | Required evidence before changing topology |
|---|---|
| Can a CAE/Headless client reconnect to another RLS after a pod failure? | Vendor statement and fault-injection test. |
| Can a Kubernetes Service load-balance gRPC channels without breaking repository events or invalidation? | Vendor guidance and live test with multiple RLS endpoints. |
| Are sessions, caches, authorization, or event cursors bound to one RLS? | Product architecture confirmation and runtime observation. |
| Does an RLS Service expose a gRPC endpoint on the target image? | Image configuration and Kubernetes Service inspection. |
| Is the gRPC endpoint port stable and configurable? | Target image documentation; do not assume the example port `40165`. |
| Is TLS mandatory for the target cluster? | Platform policy and a working `negotiation-type` configuration. |
| Is the deployment/upgrade release compatible with gRPC Workflow access? | CoreMedia version matrix and component-specific configuration. |

Only a confirmed negative answer to the affinity questions permits removal of the per-RLS rendering StatefulSets.

## Migration Options

### Option A: gRPC Transport, Existing RLS Affinity

Keep the current number of CAE/Headless StatefulSets and their direct RLS-pod relationship. Replace only the Unified API transport properties and expose the required gRPC endpoint.

This is the lowest-risk first production step. It proves gRPC configuration and workload compatibility while retaining the established scaling and zero-downtime semantics.

### Option B: gRPC Transport, Shared RLS Service

After affinity has been disproved, configure each live CAE/Headless workload against a stable RLS Service. Create one rendering StatefulSet per component kind and allow its replicas to scale independently from RLS count.

This simplifies CAE/Headless scaling, but it changes the meaning of labels, readiness, resource names, HPA selectors, and the upgrade model. It requires its own migration and rollback plan.

### Option C: Remove or Redesign RLS

Do not couple this to the CORBA-to-gRPC work. RLS replication and per-RLS databases solve delivery-side scaling and availability independently of the rendering transport. Any change to the RLS tier is a separate architecture decision.

**Recommended path:** Option A first, then consider Option B only after the verification gate is passed. Option C is out of scope.

## Implementation Plan

### Phase 0: Establish the Product Contract

Before changing Java code, record the exact CoreMedia image tags and obtain the answers in the verification table above. Build a small delivery installation with at least two RLS instances and two rendering instances. Capture:

- generated CAE/Headless environment variables and `SPRING_APPLICATION_JSON`;
- RLS Service ports and endpoint addresses;
- CAE/Headless startup, content reads, blob retrieval, and invalidation behavior;
- RLS failover while rendering traffic is active;
- behavior during a versioned upgrade; and
- the behavior when a gRPC channel is reused or re-established through the Service.

Do not use production traffic as the first test of this contract.

### Phase 1: Add Protocol-Neutral Configuration

Separate the concept of a UAPI client from the historical `corba` package name before expanding topology. The implementation should be able to render either a CORBA configuration or a gRPC configuration from an explicit, versioned operator setting.

The setting must distinguish at least these independent links:

| Link | Current protocol configuration | Future gRPC configuration |
|---|---|---|
| Live CAE/Headless to RLS or MLS | `repository.url` | `REPOSITORY_USEGRPC`, CAP channel address, HTTP base URI, optional TLS negotiation |
| RLS replicator to MLS | `replicator.publication-ior-url` | `REPOSITORY_USEGRPC`, CAP channel address, `replicator.publication-http-base-uri`, optional TLS negotiation |
| Other Unified API clients | Existing `repository.url` or client-specific URLs | Assess individually; do not change automatically |

Keep secret handling through `HasUapiClient`; gRPC changes endpoints and transport configuration, not the repository credentials. The existing `SpringProperties` utility emits Spring properties via `SPRING_APPLICATION_JSON`; `REPOSITORY_USEGRPC` needs separate environment/system-property treatment because CoreMedia documents it as non-Spring-Boot configuration.

Implement a distinct helper for each address type:

- CORBA IOR URL: `http://<service>:8080/ior`.
- HTTP base URI: `http://<service>:<http-port>` without the `/ior` suffix.
- gRPC endpoint address: `<service>:<grpc-port>`.

Do not derive the latter two by string replacement on an IOR URL. Their formats and ports are different contracts.

### Phase 2: Preserve Compatibility and Rollback

Introduce gRPC as an explicit opt-in. The default must preserve the existing CORBA resource shape and settings until the team deliberately changes the compatibility policy.

For the initial rollout:

1. Generate both required Service ports and client settings only for explicitly selected protocol paths.
2. Retain CORBA IOR configuration for components still using CORBA, including the RLS replicator where applicable.
3. Keep the existing direct pod DNS mapping and one-rendering-StatefulSet-per-RLS topology.
4. Upgrade one non-production installation, verify it, then upgrade a versioned multi-RLS installation.
5. Roll back by restoring the selected protocol and image configuration, not by deleting RLS databases or changing RLS replica counts.

The concrete CRD field name and compatibility version must be chosen with the project's CRD policy. Additive fields are preferable. Generated CRDs originate from Java models and are copied to `k8s/`, `charts/cmcc-operator/crds/`, and `src/test/resources/` during `./gradlew build`; never edit only a copied YAML file.

### Phase 3: Remove Affinity Only After Proof

When Phase 0 verifies that the rendering-to-RLS link can be shared safely, change the following as one topology migration:

| Current behavior | Shared-service target | Code area |
|---|---|---|
| Rendering component points to a pod-qualified RLS hostname | Rendering component points to a stable RLS Service | `AbstractRenderingCorbaComponent` replacement/refactor |
| One rendering StatefulSet per RLS | One StatefulSet per rendering component kind | Rendering resource generation |
| Replica count is distributed across RLS-indexed StatefulSets | Replica count belongs directly to CAE or Headless | Scaling implementation and status messages |
| `sts-index` labels and index-aware selectors | Simplified selectors without RLS topology meaning | Label and readiness handling |
| Upgrade preserves individual delivery stacks | Upgrade strategy based on verified service failover semantics | Rendering and RLS upgrade paths |

Do not remove index-aware behavior in isolation. The affected code also implements partition selection, `OnDelete` updates, fast final pod replacement, readiness aggregation, and zero-downtime safeguards.

## Required Test Coverage

### Unit and Reconciler Tests

Extend the mocked Kubernetes reconciler tests before changing production defaults. In particular, assert:

- CORBA remains the generated default during the compatibility period.
- gRPC mode generates `REPOSITORY_USEGRPC`, CAP endpoint, HTTP base URI, and the negotiated transport settings as intended.
- The RLS replicator generates its gRPC-specific configuration separately from the rendering client.
- gRPC mode exposes the required Service port with the actual target-image port.
- Credentials remain Secret references and are not copied into generated configuration values.
- Multi-RLS gRPC mode still creates the existing indexed StatefulSets during Option A.
- Shared-service mode, if enabled later, creates the intended unsharded rendering StatefulSet and selectors.
- `forceMls`, zero RLS, preview components, and explicit user-supplied component configuration retain their intended behavior.

[MilestonesCMCCReconcilerTest](../src/test/java/com/tsystemsmms/cmcc/cmccoperator/reconciler/MilestonesCMCCReconcilerTest.java) and [UpgradeCMCCReconcilerTest](../src/test/java/com/tsystemsmms/cmcc/cmccoperator/reconciler/UpgradeCMCCReconcilerTest.java) are the closest topology and lifecycle test anchors.

### Integration and Operational Tests

Run these against the exact target images, not only the Fabric8 mock server:

1. A one-RLS delivery installation with CAE and Headless configured for gRPC.
2. A two-RLS installation exercising both normal rendering and content invalidation.
3. RLS-pod failure while traffic continues through the rendering tier.
4. Rendering-pod restart and scale-out while gRPC channels exist.
5. RLS replication interruption and recovery.
6. A versioned multi-RLS zero-downtime upgrade using the operator's `version` flow.
7. TLS negotiation, if production requires it.
8. Protocol rollback to the supported compatibility configuration.

The success criteria are not merely pod readiness. They include successful repository reads, blob access through the HTTP base URI, fresh rendered content after invalidation, healthy RLS replication, no unexpected affinity failures, and a completed `Ready` milestone.

## Upgrade and Rollback Constraints

The existing zero-downtime implementation assumes a delivery stack of RLS, Solr follower, and CAE/Headless. With multiple RLS, it deliberately preserves one stack while updating the other stacks.

Any shared-service implementation must demonstrate an equivalent availability guarantee before replacing this strategy. A Kubernetes Service that sends new channels to a newly upgraded RLS is not automatically safe for existing long-lived channels or repository-event processing.

Do not combine these in one deployment change:

- protocol selection;
- rendering topology collapse;
- RLS replica-count changes;
- database schema or data migration; and
- CRD version migration.

Each introduces a different rollback surface. Stage them so a failed gRPC rollout can return to the documented CORBA topology without data operations.

## Completion Criteria

The CORBA replacement is ready to become the default only when all statements below are true:

- The target CoreMedia version and image expose and support the selected gRPC endpoints.
- The operator produces valid gRPC and HTTP-base-URI configuration for every changed link.
- The role of the RLS Service in channel routing, failover, events, and invalidation is documented by CoreMedia or verified in integration tests.
- The desired topology has passed fault, scale, and versioned-upgrade tests.
- CORBA compatibility and rollback behavior are documented for existing deployments.
- Java CRD models, generated CRDs, Helm charts, examples, operator documentation, and tests agree.
- No generated Secret, ConfigMap, log line, or status field exposes repository credentials or session tokens.

## Open Questions for CoreMedia or the Image Maintainer

Resolve these questions before Phase 3:

1. Does the CAE/Headless gRPC Unified API client require affinity to a particular RLS for the duration of a channel or event subscription?
2. Does a Kubernetes Service correctly balance both initial and re-established gRPC channels among RLS pods?
3. Which exact gRPC ports, health endpoints, and TLS settings are exposed by each target Content Server image?
4. Is `repository.http-base-uri` sufficient for all blob and servlet traffic in the target CAE/Headless image configuration?
5. Which client components require a Workflow Server connection, and from which CoreMedia version is that connection fully gRPC-capable?
6. Are there supported retry, keepalive, deadline, or load-balancer settings for a multi-RLS gRPC deployment?
7. Does the vendor provide a supported migration path for switching a running CAE/Headless deployment from CORBA to gRPC without a restart?

Until these are answered, implement only the compatibility-preserving gRPC transport path and retain the present RLS affinity.