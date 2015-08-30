package org.yamcs.studio.core;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.ws.YamcsConnectionProperties;
import org.yamcs.studio.core.web.RestClient;

/**
 * Handles external connections and its related state. This logic was originally in YamcsPlugin, but
 * in an attempt to improve readability, they were bundled up here.
 *
 * TODO this should eventually move into UI, or the UI-parts should be filtered out of it.
 */
public class ConnectionManager {

    private static final Logger log = Logger.getLogger(ConnectionManager.class.getName());

    public enum ConnectionStatus {
        Disconnected, // no clients (WebSocket, HornetQ) are connected to Yamcs server
        Connecting,
        Connected, // all clients are connected
        Disconnecting,
        ConnectionFailure,
    }

    private Set<StudioConnectionListener> studioConnectionListeners = new HashSet<>();

    private YamcsCredentials creds;
    private ConnectionInfo connectionInfo;
    private ConnectionMode mode;
    private ConnectionStatus connectionStatus;

    private RestClient restClient;
    private WebSocketRegistrar webSocketClient;

    public static ConnectionManager getInstance() {
        return YamcsPlugin.getDefault().getConnectionManager();
    }

    public boolean isConnected() {
        return connectionStatus == ConnectionStatus.Connected;
    }

    public void addStudioConnectionListener(StudioConnectionListener listener) {
        studioConnectionListeners.add(listener);
        // TODO this if should probably include 'whether we are currently connected'. ConnectionStatus ?
        if (restClient != null && webSocketClient != null)
            listener.onStudioConnect(getWebProperties(), getHornetqProperties(), restClient, webSocketClient);
    }

    public void removeStudioConnectionListener(StudioConnectionListener listener) {
        studioConnectionListeners.remove(listener);
    }

    public void setYamcsCredentials(YamcsCredentials creds) {
        this.creds = creds;
    }

    public void connect(ConnectionInfo connectionInfo, YamcsCredentials creds) {
        connect(connectionInfo, creds, ConnectionMode.PRIMARY);
    }

    public void connect(ConnectionInfo connectionInfo, YamcsCredentials creds, ConnectionMode mode) {
        this.connectionInfo = connectionInfo;
        this.creds = creds;
        this.mode = mode;

        setConnectionStatus(ConnectionStatus.Connecting);

        // (re)establish the connections to the yamcs server

        // common properties
        YamcsConnectionProperties webProps = getWebProperties();

        // Create a new REST Client. This doesn't make a connection, but it does
        // start a new thread pool. We could improve this in the future so that
        // we stick to just one instance while updating its conn. properties.
        restClient = new RestClient(webProps, creds);

        // WebSocket
        webSocketClient = new WebSocketRegistrar(webProps, creds);
        YamcsPlugin.getDefault().addMdbListener(webSocketClient);

        // We start other clients as well
        log.info("Connecting web socket");
        new Thread() {
            @Override
            public void run() {
                try {
                    webSocketClient.connect(() -> setupConnections());
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Could not connect", e);
                    Display.getDefault().asyncExec(() -> {
                        String detail = (e.getMessage() != null) ? e.getMessage() : e.getClass().getSimpleName();
                        MessageDialog.openError(Display.getDefault().getActiveShell(),
                                webProps.getYamcsConnectionString(), "Could not connect. " + detail);
                        // TODO attempt failover
                    });
                }
            }
        }.start();
    }

    public void disconnect() {
        synchronized (this) {
            if (connectionStatus == ConnectionStatus.Disconnected
                    || connectionStatus == ConnectionStatus.Disconnecting)
                return;

            if (connectionStatus != ConnectionStatus.ConnectionFailure)
                setConnectionStatus(ConnectionStatus.Disconnecting);
        }

        // WebSocket
        if (webSocketClient != null) {
            YamcsPlugin.getDefault().removeMdbListener(webSocketClient);
            webSocketClient.shutdown();
        }
        webSocketClient = null;

        // REST
        if (restClient != null)
            restClient.shutdown();
        restClient = null;

        // Notify all studio connection listeners of disconnect
        for (StudioConnectionListener scl : studioConnectionListeners) {
            try {
                scl.onStudioDisconnect();
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unable to disconnect listener " + scl + ".", e);
            }
        }

        if (connectionStatus != ConnectionStatus.ConnectionFailure)
            setConnectionStatus(ConnectionStatus.Disconnected);
    }

    // Likely not on the swt thread
    void setupConnections() {
        log.fine("WebSocket connected");
        // Need to improve this code. Currently doesn't support changing connections
        //boolean doSetup = (this.clientInfo == null);
        YamcsAuthorizations.getInstance().getAuthorizations();
        YamcsPlugin.getDefault().loadParameters();
        YamcsPlugin.getDefault().loadCommands();

        studioConnectionListeners.forEach(l -> {
            l.onStudioConnect(getWebProperties(), getHornetqProperties(), restClient, webSocketClient);
        });
        setConnectionStatus(ConnectionStatus.Connected);
    }

    public void connectionFailure(String errorMessage) {
        Display.getDefault().asyncExec(() -> {
            askSwitchNode(errorMessage);
        });
    }

    private void askSwitchNode(String errorMessage) {
        String message = "Connection error with " + mode.getPrettyName() + " Yamcs Server.";
        if (errorMessage != null && errorMessage != "") {
            message += "\nDetails:" + errorMessage;
        }
        ConnectionMode nextMode = (mode == ConnectionMode.PRIMARY) ? ConnectionMode.FAILOVER : ConnectionMode.PRIMARY;
        message += "\n\n" + "Would you like to switch connection to the " + nextMode.getPrettyName() + " Yamcs Server now?";
        MessageDialog dialog = new MessageDialog(null, "Connection Error", null, message,
                MessageDialog.QUESTION, new String[] { "Yes", "No" }, 0);
        if (dialog.open() == Dialog.OK) {
            Display.getDefault().asyncExec(() -> {
                disconnect();
                try {
                    switchNode();
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Could not switch node", e);
                    notifyConnectionFailure(e.getMessage());
                }
            });
        } else {
            abortSwitchNode();
        }
    }

    public void switchNode() {
        if (mode == ConnectionMode.PRIMARY) {
            log.info("Switching to failover server");
            mode = ConnectionMode.FAILOVER;
        } else {
            log.info("Switching back to primary server");
            mode = ConnectionMode.PRIMARY;
        }

        disconnect();
        connect(connectionInfo, creds, mode);
    }

    public void notifyConnectionFailure(String errorMessage) {
        synchronized (this) {
            if (connectionStatus != ConnectionStatus.Connected && connectionStatus != ConnectionStatus.Connecting)
                return;
            setConnectionStatus(ConnectionStatus.ConnectionFailure);
        }
        disconnect();
        connectionFailure(errorMessage);
    }

    public void notifyUnauthorized() {
        MessageDialog.openError(Display.getCurrent().getActiveShell(), "Connect", "Unauthorized");
    }

    public boolean isPrivilegesEnabled() {
        // TODO we should probably control this from the server, rather than here. Just because
        // the creds are null, does not really mean anything. We could also send creds to an
        // unsecured yamcs server. It would just ignore it, and then our client state would
        // be wrong
        return creds != null;
    }

    public RestClient getRestClient() {
        return restClient;
    }

    public WebSocketRegistrar getWebSocketClient() {
        return webSocketClient;
    }

    public YamcsConnectionProperties getWebProperties() {
        return connectionInfo.getConnection(mode);
    }

    private YamcsConnectData getHornetqProperties() {
        YamcsConnectionProperties yprops = getWebProperties();
        YamcsConnectData hornetqProps = new YamcsConnectData();
        hornetqProps.host = yprops.getHost();
        hornetqProps.port = 5445; // Hardcoded, we need to get rid of hornetq anyway
        hornetqProps.instance = yprops.getInstance();
        if (creds != null) {
            hornetqProps.username = creds.getUsername();
            hornetqProps.password = creds.getPasswordS();
            hornetqProps.ssl = true;
        }
        return hornetqProps;
    }

    private void abortSwitchNode() {
        if (connectionStatus == ConnectionStatus.ConnectionFailure)
            setConnectionStatus(ConnectionStatus.Disconnected);
    }

    private void setConnectionStatus(ConnectionStatus connectionStatus) {
        log.info(String.format("[%s] %s", mode, connectionStatus));
        this.connectionStatus = connectionStatus;
    }

    public void shutdown() {
        if (restClient != null)
            restClient.shutdown();
        if (webSocketClient != null)
            webSocketClient.shutdown();
    }
}