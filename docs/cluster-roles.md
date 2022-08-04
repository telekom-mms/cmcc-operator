# Cluster Roles and Rights

The operator requires a number of rights to be able to do its job. The Helm chart creates ClusterRole and ClusterRoleBinding objects for them.

## Cluster Resources Access

| Namespace              | Resource                        | Verbs                            | Description                                                                             | Only if       |
|------------------------|---------------------------------|----------------------------------|-----------------------------------------------------------------------------------------|---------------|
| ``                     | `configmaps`                    | *                                | Read specification (instead of CRD) from config map, and create the overview config map |               |
| ``                     | `persistentvolumeclaims`        | *                                | Storage for databases and caches                                                        |               |
| ``                     | `secrets`                       | *                                | Create and read secrets for database and API login                                      | manageSecrets |
| ``                     | `services`                      | *                                | Create services for both internal and external access to components                     |               |
| `apps`                 | `statefulsets`                  | *                                | Components are managed through StatefulSets                                             |               |
| `batch`                | `jobs`                          | *                                | Content is imported using a job                                                         |               |
| `networking.k8s.io`    | `ingresses`                     | *                                | To route HTTP requests from the outside to the workload pods, ingresses are needed      |               |
| `apiextensions.k8s.io` | `customresourcedefinitions`     | `get`, `list`                    | Read specification schema from CRD definition                                           | useCrd        |
| `cmcc.tsystemsmms.com` | `coremediacontentclouds`        | `get`, `list`, `update`, `watch` | Read specification from CRD                                                             | useCrd        |
| `cmcc.tsystemsmms.com` | `coremediacontentclouds/status` | `get`, `list`, `update`, `watch` | Read specification from CRD                                                             | useCrd        |

Access to certain resources is only required if the operator is run with the config option listed in the "Only if" column. By default, all options are enabled. See [Configuring the Operator](../README.md#configuring-the-operator) for details.