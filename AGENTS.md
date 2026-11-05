# CMCC Operator Agent Entry Point

## Scope and References

- This repository is a Java 17/Spring Boot Kubernetes operator built with Java Operator SDK and Fabric8. It reconciles `CoreMediaContentCloud` resources into CoreMedia Content Cloud infrastructure and application resources.
- Treat the sibling CoreMedia 2512.0.2 documentation as the versioned product reference; do not modify it when changing this operator.
- Start with [README.md](README.md) for installation and deployment flow. Use [docs/custom-resource.md](docs/custom-resource.md) for CRD semantics, [docs/scaling.md](docs/scaling.md) for delivery topology, [docs/upgrade-path.md](docs/upgrade-path.md) for zero-downtime behavior, and [docs/customizing-the-operator.md](docs/customizing-the-operator.md) for extension points.
- For the evidence, decisions, and implementation sequence of the CAE/Headless-to-RLS transport change, read [docs/corba-grpc-migration.md](docs/corba-grpc-migration.md).

## Architecture

- `CoreMediaContentCloudReconciler` owns reconciliation. `TargetState` computes the desired resources; `DefaultTargetState` supplies the standard CMCC topology and deployment milestones.
- Components implement `Component` and generate Kubernetes resources. Register component types through `ComponentBeanFactories`; do not put resource-generation rules in the reconciler.
- The `components/corba` package currently owns Content Server, CAE, and Headless rendering configuration. Ingress construction is isolated in `ingress`; update logic for existing Kubernetes objects belongs in `resource`.
- Delivery creation is milestone ordered: delivery services including RLS are ready before live CAE and Headless start. Preserve this dependency when changing topology.

## Build, Test, and Generated Resources

- Use `./gradlew test` for focused validation and `./gradlew build` for the full build.
- `build` generates the CRD from annotated Java classes and copies it to `k8s/`, `charts/cmcc-operator/crds/`, and `src/test/resources/`. Change CRD Java models first; never hand-edit only a copied CRD.
- Use `./gradlew build jibDockerBuild` to build a local operator image. For a local chart deployment, follow [docs/customizing-the-operator.md](docs/customizing-the-operator.md).
- Version 2 CRDs are breaking, and Helm does not update an existing CRD automatically. Keep CRD compatibility and migration effects explicit in changes.

## CAE/Headless and RLS Topology

- Today the rendering-to-RLS relationship is CORBA-driven and affinity based. `AbstractRenderingCorbaComponent` creates one live CAE/Headless StatefulSet per RLS and configures a rendering component against its specific RLS pod; `ContentServerComponent` owns RLS state and replication behavior.
- The current behavior and its rationale are described in [docs/scaling.md](docs/scaling.md). Changes to `with.delivery.rls`, `scalingTarget`, resource naming, or rendering replicas must preserve or deliberately replace this topology and its upgrade behavior.
- Cover these changes with reconciler tests that assert the produced StatefulSets, Services, properties, and resource names. Existing milestone and upgrade tests are the closest examples.

## Preparing a gRPC Migration

- CoreMedia 2512.0.2 supports CORBA and gRPC for Content Server clients. gRPC is enabled with `REPOSITORY_USEGRPC=true` and requires `repository.http-base-uri` plus `spring.grpc.client.channels.cap.address`; see Deployment Manual and Unified API connection guide.
- Do **not** infer that gRPC is stateless merely from the protocol. Before altering the RLS topology, verify for the target CoreMedia image/version whether gRPC channels, invalidation/event delivery, failover, and load balancing require RLS affinity. Treat vendor release notes and runtime configuration as the source of truth.
- Once the evidence confirms affinity is unnecessary, evolve the operator in this order:
  1. Add protocol-neutral client configuration and gRPC endpoint/HTTP-base-URI support while retaining the CORBA path for compatibility.
  2. Replace direct RLS-pod addressing and per-RLS rendering StatefulSets with a stable RLS Service only after the required connection semantics are verified.
  3. Reassess independent CAE/Headless and RLS scaling, upgrade sequencing, readiness checks, labels/selectors, and status messages; then update CRD documentation and topology tests.
- Keep the CORBA and gRPC paths independently testable during migration. Do not remove CORBA settings, RLS persistence, or upgrade safeguards in the same change that introduces gRPC.