# Scaling

Scaling in CoreMedia can only be achieved in a rather limited manner. This is due to different aspects, i.e. because 
CAEs cannot (yet) freely communicate with any available RLS. Instead, they need persistent connections to "their"
RLS instance. The RLS instances also need a dedicated DB schema / connection.

The operator offers a means for scaling the CMCC custom resource object meaning you "scale the CoreMedia instance".
You can configure whose actual component's replicas is scaled up/down when the CMCC resource is being scaled. In the 
spec you find the field `spec.scalingTarget` which can have the value `cae`(default) oder `headless`.

That enables you to directly "scale" the CMCC resource by means of the regular k8s capabilities. I.e.
```
kubectl scale --namespace <namespace> --replicas=6 cmcc/<releasename>
```

Of course you could argue that it may also be necessary to scale the RLS instances. Yet in most cases this is a 
"second level" scaling. RLS instances need a dedicated DB schema and need to catch up with their replication. That
takes time and renders a "live" scaling useless in critical load situations. It is far more likely that rendering
components like Headless or CAE are to be scaled to handle an increased load.

## RLS coupling to CAE/Headless

CORBA and other CoreMedia aspects do enforce a dedicated connection between "Content-Rendering-Components" (CAE, 
Headless) and their corresponding RLs instance.

The operator ensures this by 
 - deploying a single RLS StatefulSet with 'n' replicas (as defined in the CMCC `spec.with.delivery.rls`)
 - deploying **'n' CAE/Headless StatefulSets** (one per RLS pod)
   - The replicas field of these CAE (or headless respectively) StatefulSets is then calculated like this: 
     The number defined in the CMCC `spec.scaling` is distributed across all CAE/Headless StatefulSets

Example:
```
spec.scalingTarget = cae
spec.with.delivery.rls = 2
spec.with.delivery.minCae = 2
spec.with.delivery.maxCae = 6
```
This setup will initially lead to the following:
 - Deployments
   - 1 RLS StatefulSet, replicas = 2
   - 2 CAE StatefulSets, each having replicas = 1
 - Behaviour
    - Scaling the CMCC resource to 6 will change `replicas` on
      - each CAE StatefulSets = 3
    - Scaling the CMCC resource to 5 will change `replicas` on 
      - the 1st CAE StatefulSets = 3
      - the 2nd CAE StatefulSets = 2

## Autoscaling

Using a regular kubernetes HPA you can implement an autoscaling mechanism. Just target it against the CMCC resource and
set up the metrics and the limits

### Example of autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: CMCC-{{ .Release.Name }}-Autoscaler
  labels:
    {{- include "cmcc.labels" . | nindent 4 }}
spec:
  minReplicas: 2
  maxReplicas: 6
  scaleTargetRef:
    apiVersion: cmcc.tsystemsmms.com/v2
    kind: CoreMediaContentCloud
    name: {{ .Release.Name }}
  behavior:
    scaleDown:
      policies:
      - periodSeconds: 15
        type: Percent
        value: 100
      selectPolicy: Max
      stabilizationWindowSeconds: 60
    scaleUp:
      policies:
      - periodSeconds: 15
        type: Pods
        value: 1
      - periodSeconds: 15
        type: Percent
        value: 100
      selectPolicy: Max
      stabilizationWindowSeconds: 30
  metrics:
  - resource:
      name: cpu
      target:
        averageValue: "1"
        type: AverageValue
    type: Resource
```