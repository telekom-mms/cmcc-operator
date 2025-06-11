package com.tsystemsmms.cmcc.cmccoperator.reconciler;

import com.tsystemsmms.cmcc.cmccoperator.CoreMediaContentCloudReconciler;
import com.tsystemsmms.cmcc.cmccoperator.crds.*;
import com.tsystemsmms.cmcc.cmccoperator.utils.HttpResponseAdapter;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.internal.ExecWebSocketListener;
import io.fabric8.kubernetes.client.http.*;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

public abstract class AbstractCMCCReconcilerTest {

    @Autowired
    protected ApplicationContext applicationContext;
    @Autowired
    protected Operator operator;
    @Autowired
    protected KubernetesMockServer server;
    @MockitoSpyBean
    protected CoreMediaContentCloudReconciler reconciler;
    @Mock
    protected Context<?> context;
    @MockitoSpyBean
    protected KubernetesClient kubernetesClient;

    protected String namespace = "default";

    protected Map<Predicate<StandardHttpRequest>, Pair<HttpResponse<?>, Consumer<StandardHttpRequest>>>
            httpRequestOverrides = Collections.synchronizedMap(new HashMap<>());

    protected List<Function<CoreMediaContentCloud, CoreMediaContentCloud>> beforeReconcileHooks = new LinkedList<>();
    protected List<Consumer<UpdateControl<CoreMediaContentCloud>>> afterReconcileHooks = new LinkedList<>();

    AbstractCMCCReconcilerTest() {}

    AbstractCMCCReconcilerTest(String namespace) {
        this.namespace = namespace;
    }

    protected void setupReconcilerOverrides() {
        doAnswer(invocationOnMock -> {
            var cmcc = (CoreMediaContentCloud) invocationOnMock.getArgument(0);

            for (var hook : beforeReconcileHooks) {
                cmcc = hook.apply(cmcc);
            }
            var result = (UpdateControl<CoreMediaContentCloud>) invocationOnMock.callRealMethod();
            afterReconcileHooks.stream().forEach(hook -> hook.accept(result));
            return result;
        })
        .when(reconciler).reconcile(any(), any());
    }

    protected Function<CoreMediaContentCloud, CoreMediaContentCloud> addReconcilerOnBefore(Function<CoreMediaContentCloud, CoreMediaContentCloud> onBefore) {
        beforeReconcileHooks.add(onBefore);
        return onBefore;
    }

    protected void addReconcilerAfter(Consumer<UpdateControl<CoreMediaContentCloud>> onAfter) {
        afterReconcileHooks.add(onAfter);
    }

    protected CompletableFuture<UpdateControl<CoreMediaContentCloud>> getReconcileFuture() {
        CompletableFuture<UpdateControl<CoreMediaContentCloud>> future = new CompletableFuture<>();
        addReconcilerAfter(result -> {
            afterReconcileHooks.remove(this);
            future.complete(result);
        });
        return future;
    }

    protected void removeReconcilerOnBefore(Function<CoreMediaContentCloud, CoreMediaContentCloud> onBefore) {
        beforeReconcileHooks.remove(onBefore);
    }

    protected void addHttpRequestOverride(Predicate<StandardHttpRequest> condition, HttpResponseAdapter response, Consumer<StandardHttpRequest> callback) {
        httpRequestOverrides.put(condition, new ImmutablePair<>(response, callback));
    }

    protected void setupHttpClientOverrides() {
        doAnswer(invocationOnMock -> {
            var orgResult = invocationOnMock.callRealMethod();
            if (orgResult instanceof HttpClient httpClient) {
                httpClient = spy(httpClient);
                doAnswer(i -> {
                    if (!httpRequestOverrides.isEmpty()) {
                        var request = (StandardHttpRequest) i.getArgument(0);
//                        if ("application/json-patch+json".equals(request.getContentType()))
//                            System.out.println(MessageFormat.format("REQ: {0} {1} {2}", request.method(), request.bodyString(), request.uri()));
                        var overrideOp = httpRequestOverrides.entrySet().stream()
                                .filter(entry -> entry.getKey().test(request))
                                .map(Entry::getValue)
                                .findFirst();
                        if (overrideOp.isPresent()) {
                            var override = overrideOp.get();
                            var result = CompletableFuture.completedFuture(override.getLeft());
                            if (override.getRight() != null) {
                                override.getRight().accept(request);
                            }
                            return result;
                        }
                    }
                    return i.callRealMethod();
                })
                .when(httpClient)
                .sendAsync(any(), any());

                doAnswer(i -> {
                    final var wbBuilder = spy((WebSocket.Builder) i.callRealMethod());
                    doAnswer(i2 -> {
                        if (i2.getMock() instanceof StandardWebSocketBuilder wb) {
                            var request = wb.asHttpRequest();
                            var overrideOp = httpRequestOverrides.entrySet().stream()
                                    .filter(entry -> entry.getKey().test(request))
                                    .map(Entry::getValue)
                                    .findFirst();
                            if (overrideOp.isPresent()) {
                                var override = overrideOp.get();
                                var response = override.getLeft();
                                var result = CompletableFuture.completedFuture(response);
                                var socket = Mockito.mock(WebSocket.class);
                                var listener = (ExecWebSocketListener) i2.getArgument(0);
                                // send response to listener, stream id 1
                                ByteBuffer buf = ByteBuffer.allocate(response.bodyString().length() + 1);
                                buf.put((byte) 1);
                                buf.put(response.bodyString().getBytes());
                                listener.onMessage(socket, buf);
                                // set exitcode explicitly (otherwise null)
                                var exitCodeField = ExecWebSocketListener.class.getDeclaredField("exitCode");
                                exitCodeField.setAccessible(true);
                                ((CompletableFuture<Integer>)exitCodeField.get(listener)).complete(0);
                                // close connection
                                listener.onClose(socket, 200, "test-override");
                                // callback
                                if (override.getRight() != null) {
                                    override.getRight().accept(request);
                                }
                                return result;
                            }
                            return i2.callRealMethod();
                        }
                        return i2.callRealMethod();
                    })
                    .when(wbBuilder)
                    .buildAsync(any());
                    return wbBuilder;
                })
                .when(httpClient)
                .newWebSocketBuilder();
                return httpClient;
            }
            return orgResult;
        })
        .when(kubernetesClient).getHttpClient();
    }

    protected void scaleStsToSpec(String[] ...names) {
        Arrays.stream(names).flatMap(Arrays::stream).forEach(this::scaleStsToSpec);
    }

    protected void scaleStsToSpec(String ...stsNames) {
        Arrays.stream(stsNames).forEach(this::scaleStsToSpec);
    }

    protected void scaleStsToSpec(String stsName) {
        var sts = findStsWithName(stsName);
        if (sts == null) {
            throw new RuntimeException("Could not find sts with name: " + stsName);
        }
        var replicas = sts.getSpec().getReplicas() == null ? 1 : sts.getSpec().getReplicas();
        if (sts.getStatus() == null) {
            sts.setStatus(new StatefulSetStatus());
        }
        sts.getStatus().setReadyReplicas(replicas);
        sts.getStatus().setCurrentReplicas(replicas);
        sts.getStatus().setAvailableReplicas(replicas);
        sts.getStatus().setReplicas(replicas);
        kubernetesClient.resource(sts).updateStatus();
    }

    protected void scaleSts(String stsName, int replicas) {
        var sts = findStsWithName(stsName);
        if (sts.getStatus() == null) {
            sts.setStatus(new StatefulSetStatus());
        }
        sts.getStatus().setReadyReplicas(replicas);
        sts.getStatus().setCurrentReplicas(replicas);
        sts.getStatus().setAvailableReplicas(replicas);
        sts.getStatus().setReplicas(replicas);
        kubernetesClient.resource(sts).updateStatus();
    }

    protected CoreMediaContentCloud reconcile() {
        var result = reconciler.reconcile(getCmcc(), context);
        return result.getResource().orElse(null);
    }

    protected void createPod(String nameOfSts) {
        var rlsMetadata = findStsWithName(nameOfSts).getMetadata();
        rlsMetadata.setName(nameOfSts + "-0");
        var pod = new PodBuilder()
                .withMetadata(rlsMetadata)
                .withSpec(new PodSpecBuilder()
                        .withContainers(new ContainerBuilder()
                                .build())
                        .build())
                .build();
        this.kubernetesClient.resource(pod).create();
    }

    protected CoreMediaContentCloud getCmcc() {
        return getCmccResource().list().getItems().get(0);
    }

    protected NonNamespaceOperation<CoreMediaContentCloud, KubernetesResourceList<CoreMediaContentCloud>, Resource<CoreMediaContentCloud>> getCmccResource() {
        return kubernetesClient.resources(CoreMediaContentCloud.class).inNamespace(namespace);
    }

    @Nullable
    protected StatefulSet findStsWithName(String name) {
        return getAllStatefulSets().stream()
                .filter(x -> x.getMetadata().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    protected void initStatusOnAllStatefulSets() {
        getAllStatefulSets().forEach(sts -> {
            if (sts.getStatus() == null) {
                sts.setStatus(new StatefulSetStatus());
            }
            sts.getStatus().setReadyReplicas(0);
            sts.getStatus().setCurrentReplicas(0);
            sts.getStatus().setAvailableReplicas(0);
            sts.getStatus().setReplicas(0);
            // [{"op":"replace","path":"/spec/template/metadata/annotations","value":{"kubectl.kubernetes.io/restartedAt":"2025-03-19T13:36:05.006"}}]
            sts.getSpec().getTemplate().getMetadata().setAnnotations(Map.of("kubectl.kubernetes.io/restartedAt", ""));
            kubernetesClient.resource(sts).update();
        });
    }

    protected List<StatefulSet> getAllStatefulSets() {
        return getAllStatefulSetResources().list().getItems();
    }

    protected NonNamespaceOperation<StatefulSet, KubernetesResourceList<StatefulSet>, Resource<StatefulSet>> getAllStatefulSetResources() {
        return kubernetesClient.resources(StatefulSet.class).inNamespace(namespace);
    }

    protected static final List<Class<? extends HasMetadata>> ALL_KUBERNETES_RESOURCE_TYPES = Arrays.asList(
            ConfigMap.class,
            Ingress.class,
            Job.class,
            PersistentVolumeClaim.class,
            Secret.class,
            Service.class,
            StatefulSet.class);
    protected void deleteAllResources() {
        for (Class<? extends HasMetadata> c : ALL_KUBERNETES_RESOURCE_TYPES) {
            kubernetesClient.resources(c).inNamespace(namespace).delete();
        }
    }

    protected CoreMediaContentCloud createCoreMediaContentCloud() {
        Yaml yaml = new Yaml(new Constructor(CoreMediaContentCloud.class, new LoaderOptions()));
        CoreMediaContentCloud cmcc = yaml.load(NamespaceScopedCMCCReconcilerTest.class.getClassLoader().getResourceAsStream("cmcc.yaml"));
        cmcc.getMetadata().setNamespace(namespace);
        return cmcc;
    }

    protected Set<StatefulSet> waitForStatefulSets(String[] ...names) {
        return Arrays.stream(names).flatMap(Arrays::stream).map(this::waitForStatefulSet).collect(Collectors.toSet());
    }

    protected Set<StatefulSet> waitForStatefulSets(String ...names) {
        return Arrays.stream(names).map(this::waitForStatefulSet).collect(Collectors.toSet());
    }

    protected StatefulSet waitForStatefulSet(String name) {
        return getAllStatefulSetResources().withName(name).waitUntilCondition(Objects::nonNull, 10, SECONDS);
    }

    protected StatefulSet waitForStatefulSet(int readyReplicas, String name) {
        return getAllStatefulSetResources().withName(name).waitUntilCondition(sts ->
                sts != null && sts.getStatus() != null &&
                sts.getStatus().getReadyReplicas() == readyReplicas, 10, SECONDS);
    }

    protected Set<StatefulSet> waitForStatefulSets(int readyReplicas, String ...names) {
        return Arrays.stream(names).map(name -> waitForStatefulSet(readyReplicas, name)).collect(Collectors.toSet());
    }

    protected Set<StatefulSet> waitForStatefulSets(int readyReplicas, String[] ...names) {
        return Arrays.stream(names).flatMap(Arrays::stream).map(name -> waitForStatefulSet(readyReplicas, name)).collect(Collectors.toSet());
    }

    protected CoreMediaContentCloud waitForMilestone(Milestone milestone) {
        try {
            return getCmccResource().withName(getCmcc().getMetadata().getName())
                    .waitUntilCondition(cmcc -> cmcc != null && cmcc.getStatus().getMilestone().compareTo(milestone) >= 0, 10, SECONDS);
        } catch (KubernetesClientTimeoutException e) {
            throw new RuntimeException("Times out waiting for Milestone " + milestone, e);
        }
    }

    protected String extractNameFromUriPath(StandardHttpRequest r) {
        return new File(r.uri().getPath()).getName();
    }

    protected CoreMediaContentCloud createCmcc() {
        var spec = new CoreMediaContentCloudSpec();
        spec.setComment("Test");
        var defaults = new ComponentDefaults();
        defaults.setIngressDomain("127.0.0.1.nip.io");
        spec.setDefaults(defaults);
        var withOptions = new WithOptions();
        withOptions.setDatabases(true);
        withOptions.setContentImport(true);
        withOptions.setManagement(true);
        var delivery = new WithOptions.WithDelivery();
        delivery.setMaxCae(new IntOrString(2));
        delivery.setMinCae(new IntOrString(2));
        delivery.setRls(new IntOrString(2));
        withOptions.setDelivery(delivery);
        spec.setWith(withOptions);
        var siteMapping = new SiteMapping();
        siteMapping.setHostname("corporate");
        siteMapping.setPrimarySegment("corporate");
        siteMapping.setUrlMapper("blueprint");
        spec.setSiteMappings(Set.of(siteMapping));

        CoreMediaContentCloud cmcc = new CoreMediaContentCloud();
        cmcc.setApiVersion("v2");
        cmcc.setMetadata(new ObjectMetaBuilder().withName("test-cmcc").build());
        cmcc.setSpec(spec);
        return cmcc;
    }
}
