package org.yamcs.studio.core.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.yamcs.YamcsException;
import org.yamcs.api.YamcsConnectionProperties;
import org.yamcs.api.rest.BulkRestDataReceiver;
import org.yamcs.api.rest.RestClient;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClient;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.YamcsManagement.YamcsInstance;
import org.yamcs.studio.core.YamcsPlugin;

import com.google.protobuf.MessageLite;

import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.HttpMethod;

/**
 * Provides passage to Yamcs. This covers both the REST and WebSocket API.
 */
public class YamcsClient implements WebSocketClientCallback {

    private static final Logger log = Logger.getLogger(YamcsClient.class.getName());

    private YamcsConnectionProperties yprops;
    private String userAgent;

    private volatile boolean connecting;
    private volatile boolean connected;

    private RestClient restClient;
    private WebSocketClient wsclient;

    private boolean retry = true;
    private boolean reconnecting = false;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private List<YamcsInstance> instances;

    private List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private List<WebSocketClientCallback> subscribers = new CopyOnWriteArrayList<>();

    private MessageBundler messageBundler;

    // Keep track of ongoing jobs, to respond to user cancellation requests.
    private ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor();
    private Map<IProgressMonitor, CompletableFuture<?>> cancellableJobs = new ConcurrentHashMap<>();

    public YamcsClient(String userAgent) {
        this.userAgent = userAgent;

        canceller.scheduleWithFixedDelay(() -> {
            cancellableJobs.forEach((monitor, future) -> {
                if (monitor.isCanceled()) {
                    future.cancel(false);
                    cancellableJobs.remove(monitor);
                } else if (future.isDone()) {
                    cancellableJobs.remove(monitor);
                }
            });
        }, 2000, 1000, TimeUnit.MILLISECONDS);

        messageBundler = new MessageBundler(this);
        executor.scheduleWithFixedDelay(messageBundler, 200, 400, TimeUnit.MILLISECONDS);
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        this.connectionListeners.add(connectionListener);
    }

    private FutureTask<YamcsConnectionProperties> doConnect() {
        if (connected) {
            disconnect();
        }

        restClient = new RestClient(yprops);
        restClient.setAutoclose(false);
        wsclient = new WebSocketClient(yprops, this);
        wsclient.setUserAgent(userAgent);
        wsclient.enableReconnection(true);

        FutureTask<YamcsConnectionProperties> future = new FutureTask<>(new Runnable() {
            @Override
            public void run() {
                String connectingTo = yprops.getHost() + ":" + yprops.getPort();
                int maxAttempts = 10;
                try {
                    if (reconnecting && !retry) {
                        log.warning("Retries are disabled, cancelling reconnection");
                        reconnecting = false;
                        return;
                    }

                    connecting = true;
                    connecting();
                    for (int i = 0; i < maxAttempts; i++) {
                        try {
                            log.fine(String.format("Connecting to %s attempt %s", connectingTo, i));
                            instances = restClient.blockingGetYamcsInstances();
                            if (instances == null || instances.isEmpty()) {
                                log.warning("No configured yamcs instance");
                                return;
                            }
                            String defaultInstanceName = instances.get(0).getName();
                            String instanceName = defaultInstanceName;
                            if (yprops.getInstance() != null) { // check if the instance saved in properties exists,
                                                                // otherwise use the default one
                                instanceName = instances.stream().map(yi -> yi.getName())
                                        .filter(s -> s.equals(yprops.getInstance()))
                                        .findFirst()
                                        .orElse(defaultInstanceName);
                            }
                            yprops.setInstance(instanceName);

                            ChannelFuture future = wsclient.connect();
                            future.get(5000, TimeUnit.MILLISECONDS);
                            // now the TCP connection is established but we have to wait for the websocket to be setup
                            // the connected callback will handle that

                            return;
                        } catch (Exception e) {
                            // For anything other than a security exception, re-try
                            log.log(Level.WARNING,
                                    "Connection to " + yprops.getHost() + ":" + yprops.getPort() + " failed", e);
                            Thread.sleep(5000);
                        }
                    }
                    connecting = false;
                    for (ConnectionListener cl : connectionListeners) {
                        cl.log(maxAttempts + " connection attempts failed, giving up.");
                        cl.connectionFailed(connectingTo,
                                new YamcsException(maxAttempts + " connection attempts failed, giving up."));
                    }
                    log.warning(maxAttempts + " connection attempts failed, giving up.");
                } catch (InterruptedException e) {
                    for (ConnectionListener cl : connectionListeners) {
                        cl.connectionFailed(connectingTo, new YamcsException("Thread interrupted", e));
                    }
                }
            };
        }, yprops);
        executor.submit(future);
        return future;
    }

    @Override
    public void connecting() {
        for (ConnectionListener cl : connectionListeners) {
            cl.connecting(null);
        }
    }

    @Override
    public void connected() {
        messageBundler.clearQueue();

        connected = true;
        for (ConnectionListener listener : connectionListeners) {
            listener.connected(null);
        }
    }

    @Override
    public void disconnected() {
        if (connected) {
            log.warning("Connection to " + yprops + " lost");
        }
        for (ConnectionListener listener : connectionListeners) {
            listener.disconnected();
        }
    }

    public void disconnect() {
        log.info("Disconnection requested");
        if (!connected) {
            return;
        }
        wsclient.disconnect();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isConnecting() {
        return connecting;
    }

    public Future<YamcsConnectionProperties> connect(YamcsConnectionProperties yprops) {
        this.yprops = yprops;
        return doConnect();
    }

    public YamcsConnectionProperties getYamcsConnectionProperties() {
        return yprops;
    }

    public List<String> getYamcsInstances() {
        if (instances == null) {
            return null;
        }
        return instances.stream().map(r -> r.getName()).collect(Collectors.toList());
    }

    public void subscribe(WebSocketRequest req, WebSocketClientCallback messageHandler) {
        if (!subscribers.contains(messageHandler)) {
            subscribers.add(messageHandler);
        }
        messageBundler.queue(req);
    }

    public void sendWebSocketMessage(WebSocketRequest req) {
        messageBundler.queue(req);
    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {

        // Stop processing messages on shutdown
        YamcsPlugin plugin = YamcsPlugin.getDefault();
        if (plugin == null) {
            return;
        }

        subscribers.forEach(s -> s.onMessage(data));
    }

    public CompletableFuture<byte[]> get(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.GET, uri, msg);
    }

    public CompletableFuture<Void> streamGet(String uri, MessageLite msg, BulkRestDataReceiver receiver) {
        return doRequestWithDelimitedResponse(HttpMethod.GET, uri, msg, receiver);
    }

    public CompletableFuture<byte[]> post(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.POST, uri, msg);
    }

    public CompletableFuture<byte[]> patch(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.PATCH, uri, msg);
    }

    public CompletableFuture<byte[]> put(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.PUT, uri, msg);
    }

    public CompletableFuture<byte[]> delete(String uri, MessageLite msg) {
        return requestAsync(HttpMethod.DELETE, uri, msg);
    }

    private <S extends MessageLite> CompletableFuture<byte[]> requestAsync(HttpMethod method, String uri,
            MessageLite requestBody) {
        CompletableFuture<byte[]> cf;
        if (requestBody != null) {
            cf = restClient.doRequest(uri, method, requestBody.toByteArray());
        } else {
            cf = restClient.doRequest(uri, method);
        }

        String jobName = method + " /api" + uri;
        scheduleAsJob(jobName, cf, Job.SHORT);
        return cf;
    }

    private <S extends MessageLite> CompletableFuture<Void> doRequestWithDelimitedResponse(HttpMethod method,
            String uri, MessageLite requestBody, BulkRestDataReceiver receiver) {
        CompletableFuture<Void> cf;
        if (requestBody != null) {
            cf = restClient.doBulkGetRequest(uri, requestBody.toByteArray(), receiver);
        } else {
            cf = restClient.doBulkGetRequest(uri, receiver);
        }

        String jobName = method + " /api" + uri;
        scheduleAsJob(jobName, cf, Job.LONG);
        return cf;
    }

    private void scheduleAsJob(String jobName, CompletableFuture<?> cf, int priority) {
        Job job = Job.create(jobName, monitor -> {
            cancellableJobs.put(monitor, cf);

            try {
                cf.get();
                return Status.OK_STATUS;
            } catch (CancellationException | InterruptedException e) {
                return Status.CANCEL_STATUS;
            } catch (ExecutionException e) {
                log.log(Level.SEVERE, "Exception while executing job '" + jobName + "'", e.getCause());
                return Status.OK_STATUS;
            }
        });
        job.setPriority(Job.LONG);
        job.schedule();
    }

    public WebSocketClient getWebSocketClient() {
        return wsclient;
    }

    /**
     * Performs an orderly shutdown of this service
     */
    public void shutdown() {
        restClient.close(); // Shuts down the thread pool
        wsclient.shutdown();
        executor.shutdown();
        canceller.shutdown();
    }
}
