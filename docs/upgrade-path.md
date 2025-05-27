# Upgrade Path

The "Upgrade path" allows to apply the idea of versions to a complete CMCC system. Every new release then may 
contain a new version which is not just a deployment of all components with different images. 

Instead, the process of iterating all milestones is again started. Also, the "Zero-Downtime" approach makes sure 
that the frontend delivery keeps working throughout the whole upgrade process. 

## Versioning

The "Upgrade path" can be utilized by working with the "version" property. 

In the CMCC resource spec you need to set the version property to a non-empty value. It can have any format, there 
are no specific constraints. Still it is advisable to use a rather **short and simple** format as 
the (cleaned and simplified) version is being used as a suffix for the Solr follower services.

The currently (successfully) deployed version can always be found in the status of a CMCC resource (`currentVersion`). 

## The Upgrade Process in Detail

When a CMCC resource is deployed with a version property deviating from the `currentVersion` the following takes place:

 - In the status the property `targetVersion` is set to this new version
 - The Milestone is set back to "DeploymentStarted"
 - Start the whole process of iterating through all milestones until "Ready" is reached again. As the 
   Operator now works in "upgrade mode" it ensures the Zero-Downtime-Deployment (see below)
 - `currentVersion` is set and `targetVersion` is cleared when Milestone "Ready" is reached

```mermaid
stateDiagram-v2
   [*] --> DeploymentStarted
   DeploymentStarted --> DatabasesReady
   DatabasesReady --> ContentServerInitialized
   ContentServerInitialized --> ContentServerReady
   ContentServerReady --> ManagementReady
   ManagementReady --> DeliveryServicesReady
   DeliveryServicesReady --> Ready
   Ready --> DeploymentStarted : "version" changed / <br/> upgrade started
```
(for this diagram to work in IntelliJ, install [this](https://www.jetbrains.com/guide/go/tips/mermaid-js-support-in-markdown/))

## Zero-Downtime-Deployments

**Note:** This feature only works when working with at least 2 RLS instances and at least 2 CAE instances. See [Scaling](scaling.md) for details.

The general idea here is that at least one combination of Delivery Stacks (RLS + Solr-Follower + CAE/Headless) 
is kept running independently while all other parts of the CMCC system are upgraded to the new version. 
When the upgraded system is up again (including an upgraded Delivery Stack)

This includes:
 - Replication/Synchronization for all RLSs and Solr Followers is turned off right before the Milestone is set to "DeploymentStarted"
 - Re-importing new themes (and optionally test content)
 - Re-indexing feeders
 - Waiting for Replication/Synchronization before booting upgraded delivery threads (like CAEs)
 - Keeping one Delivery Stack untouched until all other components are upgraded and ready again

Workflow with additional Upgrade steps
```mermaid
stateDiagram-v2
    [*] --> DeploymentStarted : Set "targetVersion"<br/>Stop RLS replication<br/>Stop Solr sync
    DeploymentStarted --> DatabasesReady
    DatabasesReady --> ContentServerInitialized : Upgrade CMS, MLS, etc.
    ContentServerInitialized --> ContentServerReady : Run "init-cms" Import Job
    ContentServerReady --> ManagementReady : Upgrade RLS #1+, Solr-Follower, etc.<br/>Keep RLS #0 untouched<br/>Keep Solr Follower #0 untouched
    ManagementReady --> DeliveryServicesReady : Upgrade CAE Feeder, etc.<br/>Keep CAEs/Headless connected to RLS #0 untouched
    DeliveryServicesReady --> Ready : Upgrade CAE/Headless<br/>Keep CAEs/Headless connected to RLS #0 untouched
    Ready --> Healing : Upgrade<br/> remaining RLS,<br/>Solr-Follower and<br/> CAE/Headless
    Healing --> Ready : Upgrade finished<br/>Set "currentVersion"<br/>Clear "targetVersion"

```
(for this diagram to work in IntelliJ, install [this](https://www.jetbrains.com/guide/go/tips/mermaid-js-support-in-markdown/))
