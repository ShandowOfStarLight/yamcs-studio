package org.yamcs.studio.core.ui.connections;

import static org.yamcs.studio.core.ui.utils.TextUtils.forceString;
import static org.yamcs.studio.core.ui.utils.TextUtils.isBlank;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.yamcs.studio.core.ui.connections.YamcsConfiguration.AuthType;
import org.yamcs.studio.core.ui.utils.RCPUtils;

/**
 * A modal dialog for managing connection to Yamcs servers. Extracted out of preferences, because these kind of settings
 * are a lot more variable and depending on the user configuration.
 * <p>
 * The idea is that when you start Yamcs Studio for the very first time, it does not attempt any connection.
 * Auto-connect is an option.
 * <p>
 * Another thing we want to address with this dialog is to make it easy to manage different yamcs servers. Especially as
 * yamcs developers, we often have to switch from one server to another. Through this dialog these settings can be
 * managed and stored. The settings are stored using java (so inside the home directory). This makes it easier to
 * migrate from one Yamcs Studio to another.
 */
public class ConnectionsDialog extends Dialog {

    private static final Logger log = Logger.getLogger(ConnectionsDialog.class.getName());
    private static final String ITEM_STANDARD = "Standard";
    private static final String ITEM_KERBEROS = "Kerberos";

    private TableViewer connViewer;
    private Composite detailPanel;

    private ToolItem addServerButton;
    private ToolItem removeServerButton;

    private YamcsConfiguration selectedConfiguration;

    private Button autoConnect;
    private Text yamcsInstanceText;
    private Text yamcsHostText;
    private Text yamcsPortText;
    private Text nameText;

    private Label yamcsUserLabel;
    private Text yamcsUserText;
    private Label yamcsPasswordLabel;
    private Text yamcsPasswordText;

    private Button sslCheckbox;
    private Button savePasswordButton;
    private Text caCertFileText;
    private Combo authTypeCombo;

    private YamcsConfiguration chosenConfiguration;
    private String passwordForChosenConfiguration;

    public ConnectionsDialog(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        parent.getShell().setText("Yamcs Server Connections");
        Composite contentArea = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout();
        gl.marginHeight = 0;
        gl.verticalSpacing = 0;
        contentArea.setLayout(gl);

        ResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources(), contentArea);

        ToolBar editBar = new ToolBar(contentArea, SWT.NO_FOCUS);
        addServerButton = new ToolItem(editBar, SWT.NONE);
        addServerButton.setImage(resourceManager
                .createImage(RCPUtils.getImageDescriptor(ConnectionsDialog.class, "icons/obj16/server_add.png")));
        addServerButton.setToolTipText("Add Connection");
        addServerButton.addListener(SWT.Selection, evt -> {
            addServer();
            updateState();
        });

        removeServerButton = new ToolItem(editBar, SWT.NONE);
        removeServerButton.setImage(resourceManager
                .createImage(RCPUtils.getImageDescriptor(ConnectionsDialog.class, "icons/obj16/server_remove.png")));
        removeServerButton.setToolTipText("Delete Connection");
        removeServerButton.addListener(SWT.Selection, evt -> {
            removeSelectedServer();
            updateState();
        });
        editBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        SashForm sash = new SashForm(contentArea, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));
        sash.setLayout(new FillLayout());
        createServerPanel(sash, resourceManager);

        // Create right side, but wrap it in another composite to force
        // dimensions even when invisible
        Composite detailPanelWrapper = new Composite(sash, SWT.NONE);
        gl = new GridLayout();
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        detailPanelWrapper.setLayout(gl);
        createDetailPanel(detailPanelWrapper, resourceManager);
        GridData gd = new GridData(GridData.FILL_BOTH);
        gd.widthHint = 250;
        detailPanel.setLayoutData(gd);

        sash.setWeights(new int[] { 55, 45 });

        ConnectionPreferences.getConfigurations().forEach(conf -> {
            connViewer.add(conf);
        });

        YamcsConfiguration lastConf = ConnectionPreferences.getLastUsedConfiguration();
        if (lastConf != null) {
            selectServer(lastConf);
        } else {
            selectFirstServer();
        }
        updateState();

        return contentArea;
    }

    private void updateState() {
        IStructuredSelection sel = (IStructuredSelection) connViewer.getSelection();
        if (sel.isEmpty()) {
            selectedConfiguration = null;
            detailPanel.setVisible(false);
            removeServerButton.setEnabled(false);
        } else {
            selectedConfiguration = (YamcsConfiguration) sel.getFirstElement();
            detailPanel.setVisible(true);
            removeServerButton.setEnabled(true);

            if (selectedConfiguration.getAuthType() == null
                    || selectedConfiguration.getAuthType() == AuthType.STANDARD) {
                yamcsUserLabel.setVisible(true);
                yamcsUserText.setVisible(true);
                yamcsPasswordLabel.setVisible(true);
                yamcsPasswordText.setVisible(true);
                savePasswordButton.setVisible(true);
            } else {
                yamcsUserLabel.setVisible(false);
                yamcsUserText.setVisible(false);
                yamcsPasswordLabel.setVisible(false);
                yamcsPasswordText.setVisible(false);
                savePasswordButton.setVisible(false);
            }
        }
    }

    @Override
    protected void okPressed() {
        // TODO maybe only save changes when "save" button is used (and only for the active detail pane)
        // Then connection naming is an optional feature.
        saveChanges();
        super.okPressed();
    }

    private void saveChanges() {
        if (selectedConfiguration != null) {
            chosenConfiguration = selectedConfiguration;
            passwordForChosenConfiguration = chosenConfiguration.getPassword();
        }
        ConnectionPreferences.setAutoConnect(autoConnect.getSelection());
        List<YamcsConfiguration> confs = new ArrayList<>();
        Object el;
        int i = 0;
        while ((el = connViewer.getElementAt(i++)) != null) {
            YamcsConfiguration conf = (YamcsConfiguration) el;
            if (!conf.isSavePassword()) {
                conf.setPassword(null);
            }
            confs.add(conf);
        }
        ConnectionPreferences.setConfigurations(confs);
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        parent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        autoConnect = new Button(parent, SWT.CHECK);
        autoConnect.setText("Connect on startup");
        autoConnect.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        autoConnect.setSelection(ConnectionPreferences.isAutoConnect());

        GridLayout layout = (GridLayout) parent.getLayout();
        layout.numColumns++;
        layout.makeColumnsEqualWidth = false;

        Button saveButton = createButton(parent, 123, "Save", false);
        saveButton.addListener(SWT.Selection, evt -> saveChanges());

        /*Button testButton = createButton(parent, 124, "Test", false);
        testButton.addListener(SWT.Selection, evt -> {
            System.out.println("Test connection");
        });*/

        super.createButtonsForButtonBar(parent);

        Button ok = getButton(IDialogConstants.OK_ID);
        ok.setText("Connect");
        setButtonLayoutData(ok);
    }

    private void selectServer(YamcsConfiguration conf) {
        connViewer.setSelection(new StructuredSelection(conf), true);
    }

    private void selectFirstServer() {
        if (connViewer.getTable().getItemCount() > 0) {
            connViewer.setSelection(new StructuredSelection(connViewer.getElementAt(0)), true);
        }
    }

    private void addServer() {
        YamcsConfiguration conf = new YamcsConfiguration();
        conf.setName("Untitled");
        conf.setPrimaryPort(8090);
        conf.setAuthType(AuthType.STANDARD);
        connViewer.add(conf);
        connViewer.setSelection(new StructuredSelection(conf), true);
        yamcsHostText.setFocus();
    }

    private void removeSelectedServer() {
        IStructuredSelection sel = (IStructuredSelection) connViewer.getSelection();
        YamcsConfiguration conf = (YamcsConfiguration) sel.getFirstElement();
        boolean confirmed = MessageDialog.openConfirm(connViewer.getTable().getShell(), "",
                "Do you really want to remove the server configuration '" + conf.getName() + "'?");
        if (confirmed) {
            connViewer.remove(conf);
            selectFirstServer();
        }
    }

    private Composite createServerPanel(Composite parent, ResourceManager resourceManager) {
        Composite serverPanel = new Composite(parent, SWT.NONE);
        GridData gd = new GridData(GridData.FILL_BOTH);
        serverPanel.setLayoutData(gd);
        TableColumnLayout tcl = new TableColumnLayout();
        serverPanel.setLayout(tcl);

        Image serverImage = resourceManager
                .createImage(RCPUtils.getImageDescriptor(ConnectionsDialog.class, "icons/obj16/server.gif"));

        connViewer = new TableViewer(serverPanel, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        connViewer.getTable().setHeaderVisible(true);
        connViewer.getTable().setLinesVisible(false);

        TableViewerColumn nameColumn = new TableViewerColumn(connViewer, SWT.NONE);
        nameColumn.getColumn().setText("Connection Name");
        nameColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public Image getImage(Object element) {
                return serverImage;
            }

            @Override
            public String getText(Object element) {
                YamcsConfiguration conf = (YamcsConfiguration) element;
                return conf.getName();
            }
        });
        tcl.setColumnData(nameColumn.getColumn(), new ColumnWeightData(400));

        connViewer.setContentProvider(new ArrayContentProvider());
        connViewer.addSelectionChangedListener(evt -> {
            IStructuredSelection sel = (IStructuredSelection) evt.getSelection();
            if (sel.getFirstElement() != null) {
                YamcsConfiguration conf = (YamcsConfiguration) sel.getFirstElement();
                yamcsInstanceText.setText(forceString(conf.getInstance()));
                sslCheckbox.setSelection(conf.isSsl());
                /// caCertFileText.setText(forceString(conf.getCaCertFile()));
                yamcsHostText.setText(forceString(conf.getPrimaryHost()));
                yamcsPortText.setText(forceString(conf.getPrimaryPort()));
                nameText.setText(forceString(conf.getName()));

                AuthType authType = (conf.getAuthType() == null) ? AuthType.STANDARD : conf.getAuthType();
                if (authType == AuthType.STANDARD) {
                    authTypeCombo.select(0);
                    yamcsUserText.setText(forceString(conf.getUser()));
                    yamcsPasswordText.setText(forceString(conf.getPassword()));
                    savePasswordButton.setSelection(conf.isSavePassword());
                } else if (authType == AuthType.KERBEROS) {
                    authTypeCombo.select(1);
                    yamcsUserText.setText("");
                    yamcsPasswordText.setText("");
                    savePasswordButton.setSelection(false);
                } else {
                    throw new IllegalArgumentException("Unexpected auth type " + authType);
                }

                updateState();
            }
        });

        connViewer.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2) {
                YamcsConfiguration c1 = (YamcsConfiguration) o1;
                YamcsConfiguration c2 = (YamcsConfiguration) o2;
                if (c1.getName() != null && c2.getName() != null) {
                    return c1.getName().compareTo(c2.getName());
                } else {
                    return 0;
                }
            }
        });

        return serverPanel;
    }

    private Composite createDetailPanel(Composite parent, ResourceManager resourceManager) {
        detailPanel = new Composite(parent, SWT.NONE);
        GridLayout gl = new GridLayout(3, false);
        gl.marginWidth = 0;
        gl.horizontalSpacing = 0;
        detailPanel.setLayout(gl);

        Label lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Host:");
        yamcsHostText = new Text(detailPanel, SWT.BORDER);
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        yamcsHostText.setLayoutData(gd);
        yamcsHostText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(yamcsHostText.getText()) && selectedConfiguration != null) {
                selectedConfiguration.setPrimaryHost(yamcsHostText.getText());
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setPrimaryHost(null);
            }
        });

        lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Port:");
        yamcsPortText = new Text(detailPanel, SWT.BORDER);
        gd = new GridData();
        gd.widthHint = 50;
        yamcsPortText.setLayoutData(gd);
        yamcsPortText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(yamcsPortText.getText()) && selectedConfiguration != null) {
                try {
                    int d = Integer.parseInt(yamcsPortText.getText());
                    selectedConfiguration.setPrimaryPort(d);
                } catch (NumberFormatException e) {
                    log.warning("Ignoring invalid number for primary port " + yamcsPortText.getText());
                    selectedConfiguration.setPrimaryPort(null);
                }
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setPrimaryPort(null);
            }
        });

        sslCheckbox = new Button(detailPanel, SWT.CHECK);
        gd = new GridData();
        sslCheckbox.setLayoutData(gd);
        sslCheckbox.setText("Use SSL");
        sslCheckbox.addListener(SWT.Selection, evt -> {
            selectedConfiguration.setSsl(sslCheckbox.getSelection());
        });

        lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Yamcs Instance:");
        yamcsInstanceText = new Text(detailPanel, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        yamcsInstanceText.setLayoutData(gd);
        yamcsInstanceText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(yamcsInstanceText.getText()) && selectedConfiguration != null) {
                selectedConfiguration.setInstance(yamcsInstanceText.getText());
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setInstance(null);
            }
        });

        /*lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("CA Certificate:");
        caCertFileText = new Text(detailPanel, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        caCertFileText.setLayoutData(gd);
        caCertFileText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(caCertFileText.getText()) && selectedConfiguration != null) {
                // selectedConfiguration.setUser(caCertFileText.getText());
            } else if (selectedConfiguration != null) {
                // selectedConfiguration.setUser(null);
            }
        });*/

        // Spacer
        lbl = new Label(detailPanel, SWT.NONE);
        gd = new GridData();
        gd.horizontalSpan = 3;
        lbl.setLayoutData(gd);

        lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Authentication:");
        gd = new GridData();
        gd.horizontalSpan = 3;
        lbl.setLayoutData(gd);

        lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Type:");
        authTypeCombo = new Combo(detailPanel, SWT.READ_ONLY);
        authTypeCombo.setItems(ITEM_STANDARD, ITEM_KERBEROS);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        authTypeCombo.setLayoutData(gd);
        authTypeCombo.addListener(SWT.Selection, evt -> {
            if (authTypeCombo.getSelectionIndex() == 0) {
                selectedConfiguration.setAuthType(AuthType.STANDARD);
            } else if (authTypeCombo.getSelectionIndex() == 1) {
                selectedConfiguration.setAuthType(AuthType.KERBEROS);
                selectedConfiguration.setUser(null);
                yamcsUserText.setText("");
                selectedConfiguration.setPassword(null);
                yamcsPasswordText.setText("");
                selectedConfiguration.setSavePassword(false);
                savePasswordButton.setSelection(false);
            } else {
                throw new IllegalArgumentException("Unexpected auth type " + authTypeCombo.getSelectionIndex());
            }
            updateState();
        });

        yamcsUserLabel = new Label(detailPanel, SWT.NONE);
        yamcsUserLabel.setText("User:");
        yamcsUserText = new Text(detailPanel, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        yamcsUserText.setLayoutData(gd);
        yamcsUserText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(yamcsUserText.getText()) && selectedConfiguration != null) {
                selectedConfiguration.setUser(yamcsUserText.getText());
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setUser(null);
            }
        });

        yamcsPasswordLabel = new Label(detailPanel, SWT.NONE);
        yamcsPasswordLabel.setText("Password:");
        yamcsPasswordText = new Text(detailPanel, SWT.BORDER | SWT.PASSWORD);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        yamcsPasswordText.setLayoutData(gd);
        yamcsPasswordText.addListener(SWT.KeyUp, evt -> {
            if (!isBlank(yamcsPasswordText.getText()) && selectedConfiguration != null) {
                selectedConfiguration.setPassword(yamcsPasswordText.getText());
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setPassword(null);
            }
        });

        // Spacer
        lbl = new Label(detailPanel, SWT.NONE);
        gd = new GridData();
        gd.horizontalSpan = 3;
        lbl.setLayoutData(gd);

        lbl = new Label(detailPanel, SWT.NONE);
        lbl.setText("Saved Connection Name:");
        gd = new GridData();
        gd.horizontalSpan = 3;
        lbl.setLayoutData(gd);

        nameText = new Text(detailPanel, SWT.BORDER);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 2;
        nameText.setLayoutData(gd);
        // Update the label in the left panel too
        nameText.addListener(SWT.KeyUp, evt -> {
            IStructuredSelection sel = (IStructuredSelection) connViewer.getSelection();
            YamcsConfiguration conf = (YamcsConfiguration) sel.getFirstElement();
            conf.setName(nameText.getText());
            connViewer.update(conf, null);

            if (!isBlank(nameText.getText()) && selectedConfiguration != null) {
                log.fine("Storing name " + nameText.getText());
                selectedConfiguration.setName(nameText.getText());
            } else if (selectedConfiguration != null) {
                selectedConfiguration.setName(null);
            }
        });

        savePasswordButton = new Button(detailPanel, SWT.CHECK);
        savePasswordButton.setText("Save Password");
        gd = new GridData();
        gd.horizontalSpan = 3;
        savePasswordButton.setLayoutData(gd);
        savePasswordButton.addListener(SWT.Selection, evt -> {
            selectedConfiguration.setSavePassword(savePasswordButton.getSelection());
        });

        return detailPanel;
    }

    public YamcsConfiguration getChosenConfiguration() {
        // Add our credentials back in (they could have been removed during
        // serialization)
        if (chosenConfiguration != null) {
            if (passwordForChosenConfiguration != null && !passwordForChosenConfiguration.isEmpty()) {
                chosenConfiguration.setPassword(passwordForChosenConfiguration);
            }
        }
        return chosenConfiguration;
    }
}
