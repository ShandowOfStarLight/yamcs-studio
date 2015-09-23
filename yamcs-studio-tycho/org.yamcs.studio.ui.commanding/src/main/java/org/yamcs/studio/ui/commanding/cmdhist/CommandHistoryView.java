package org.yamcs.studio.ui.commanding.cmdhist;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnLayoutData;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.part.ViewPart;
import org.yamcs.protobuf.Commanding.CommandHistoryAttribute;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.studio.core.model.CommandingCatalogue;
import org.yamcs.studio.core.ui.utils.CenteredImageLabelProvider;
import org.yamcs.studio.core.ui.utils.RCPUtils;

public class CommandHistoryView extends ViewPart {

    private static final Logger log = Logger.getLogger(CommandHistoryView.class.getName());

    public static final String COL_COMMAND = "Command";
    public static final String COL_SRC_ID = "Src.ID";
    public static final String COL_SRC = "Src";
    public static final String COL_SEQ_ID = "Seq.ID";
    public static final String COL_PTV = "PTV";
    public static final String COL_T = "T";

    // Prefix used in command attribute names
    private static final String ACK_PREFIX = "Acknowledge_";

    // Ignored for dynamic columns, most of these are actually considered fixed columns.
    private static final List<String> IGNORED_ATTRIBUTES = Arrays.asList("cmdName", "binary",
            CommandHistoryRecordContentProvider.ATTR_USERNAME,
            CommandHistoryRecordContentProvider.ATTR_SOURCE,
            CommandHistoryRecordContentProvider.ATTR_FINAL_SEQUENCE_COUNT,
            CommandHistoryRecordContentProvider.ATTR_TRANSMISSION_CONSTRAINTS,
            CommandHistoryRecordContentProvider.ATTR_COMMAND_FAILED);

    private LocalResourceManager resourceManager;
    private Image greenBubble;
    private Image redBubble;
    private Image grayBubble;
    private Image waitingImage;

    private Composite parent;
    private TableViewer tableViewer;
    private CommandHistoryViewerComparator tableViewerComparator;

    // Store layouts for when a new tcl is set. Because TCLs trigger only once, and we need dynamic columns
    private Map<TableColumn, ColumnLayoutData> layoutDataByColumn = new HashMap<>();

    private CommandHistoryRecordContentProvider tableContentProvider;
    private Set<String> dynamicColumns = new HashSet<>();

    @Override
    public void createPartControl(Composite parent) {
        this.parent = parent;
        resourceManager = new LocalResourceManager(JFaceResources.getResources(), parent);
        greenBubble = resourceManager.createImage(RCPUtils.getImageDescriptor(CommandHistoryView.class, "icons/obj16/ok.png"));
        redBubble = resourceManager.createImage(RCPUtils.getImageDescriptor(CommandHistoryView.class, "icons/obj16/nok.png"));
        grayBubble = resourceManager.createImage(RCPUtils.getImageDescriptor(CommandHistoryView.class, "icons/obj16/undef.png"));
        waitingImage = resourceManager.createImage(RCPUtils.getImageDescriptor(CommandHistoryView.class, "icons/obj16/waiting.png"));

        TableColumnLayout tcl = new TableColumnLayout();
        parent.setLayout(tcl);

        tableViewer = new TableViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
        tableViewer.getTable().setHeaderVisible(true);
        tableViewer.getTable().setLinesVisible(true);

        addFixedColumns();
        applyColumnLayoutData(tcl);

        tableContentProvider = new CommandHistoryRecordContentProvider(tableViewer);
        tableViewer.setContentProvider(tableContentProvider);
        tableViewer.setInput(tableContentProvider); // ! otherwise refresh() deletes everything...

        tableViewerComparator = new CommandHistoryViewerComparator();
        tableViewer.setComparator(tableViewerComparator);

        CommandingCatalogue.getInstance().addCommandHistoryListener(cmdhistEntry -> {
            Display.getDefault().asyncExec(() -> processCommandHistoryEntry(cmdhistEntry));
        });
    }

    public void clear() {
        tableContentProvider.clearAll();
    }

    public void enableScrollLock(boolean enabled) {
        tableContentProvider.enableScrollLock(enabled);
    }

    private void addFixedColumns() {
        TableViewerColumn gentimeColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        gentimeColumn.getColumn().setText(COL_T);
        gentimeColumn.getColumn().addSelectionListener(getSelectionAdapter(gentimeColumn.getColumn()));
        gentimeColumn.getColumn().setToolTipText("Generation Time");
        gentimeColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((CommandHistoryRecord) element).getGenerationTime();
            }
        });
        layoutDataByColumn.put(gentimeColumn.getColumn(), new ColumnPixelData(150));

        TableViewerColumn nameColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        nameColumn.getColumn().setText(COL_COMMAND);
        nameColumn.getColumn().addSelectionListener(getSelectionAdapter(nameColumn.getColumn()));
        nameColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((CommandHistoryRecord) element).getSource();
            }
        });
        layoutDataByColumn.put(nameColumn.getColumn(), new ColumnWeightData(200));

        TableViewerColumn originColumn = new TableViewerColumn(tableViewer, SWT.NONE);
        originColumn.getColumn().setText(COL_SRC);
        originColumn.getColumn().addSelectionListener(getSelectionAdapter(originColumn.getColumn()));
        originColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                CommandHistoryRecord rec = (CommandHistoryRecord) element;
                return rec.getUsername() + "@" + rec.getOrigin();
            }
        });
        layoutDataByColumn.put(originColumn.getColumn(), new ColumnPixelData(200));

        TableViewerColumn seqIdColumn = new TableViewerColumn(tableViewer, SWT.CENTER);
        seqIdColumn.getColumn().setText(COL_SRC_ID);
        seqIdColumn.getColumn().addSelectionListener(getSelectionAdapter(seqIdColumn.getColumn()));
        seqIdColumn.getColumn().setToolTipText("Client ID");
        seqIdColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return String.valueOf(((CommandHistoryRecord) element).getSequenceNumber());
            }
        });
        layoutDataByColumn.put(seqIdColumn.getColumn(), new ColumnPixelData(50));

        TableViewerColumn ptvColumn = new TableViewerColumn(tableViewer, SWT.CENTER);
        ptvColumn.getColumn().setText(COL_PTV);
        ptvColumn.getColumn().addSelectionListener(getSelectionAdapter(ptvColumn.getColumn()));
        ptvColumn.getColumn().setToolTipText("Pre-Transmission Verification");
        ptvColumn.setLabelProvider(new CenteredImageLabelProvider() {
            @Override
            public Image getImage(Object element) {
                CommandHistoryRecord rec = (CommandHistoryRecord) element;
                switch (rec.getPTVInfo().getState()) {
                case UNDEF:
                    return grayBubble;
                case NA:
                case OK:
                    return greenBubble;
                case PENDING:
                    return waitingImage;
                case NOK:
                    return redBubble;
                default:
                    log.warning("Unexpected PTV state " + rec.getPTVInfo().getState());
                    return grayBubble;
                }
            }

            @Override
            public String getToolTipText(Object element) {
                CommandHistoryRecord rec = (CommandHistoryRecord) element;
                if (rec.getPTVInfo().getFailureMessage() != null)
                    return rec.getPTVInfo().getFailureMessage();
                else
                    return super.getToolTipText(element);
            }
        });
        layoutDataByColumn.put(ptvColumn.getColumn(), new ColumnPixelData(50));

        TableViewerColumn finalSeqColumn = new TableViewerColumn(tableViewer, SWT.CENTER);
        finalSeqColumn.getColumn().setText(COL_SEQ_ID);
        finalSeqColumn.getColumn().addSelectionListener(getSelectionAdapter(finalSeqColumn.getColumn()));
        finalSeqColumn.getColumn().setToolTipText("Final Sequence Count");
        finalSeqColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                CommandHistoryRecord rec = (CommandHistoryRecord) element;
                return (rec.getFinalSequenceCount() != null) ? String.valueOf(rec.getFinalSequenceCount()) : "-";
            }
        });
        layoutDataByColumn.put(finalSeqColumn.getColumn(), new ColumnPixelData(50));

        // TODO use IMemento or something
        tableViewer.getTable().setSortColumn(gentimeColumn.getColumn());
        tableViewer.getTable().setSortDirection(SWT.DOWN);
        getViewSite().setSelectionProvider(tableViewer);
    }

    private void applyColumnLayoutData(TableColumnLayout tcl) {
        layoutDataByColumn.forEach((k, v) -> tcl.setColumnData(k, v));
    }

    public void processCommandHistoryEntry(CommandHistoryEntry cmdhistEntry) {
        // Maybe we need to update structure
        for (CommandHistoryAttribute attr : cmdhistEntry.getAttrList()) {
            if (IGNORED_ATTRIBUTES.contains(attr.getName()))
                continue;

            String shortName = attr.getName()
                    .replace(ACK_PREFIX, "")
                    .replace(CommandHistoryRecord.STATUS_SUFFIX, "")
                    .replace(CommandHistoryRecord.TIME_SUFFIX, "");
            if (!dynamicColumns.contains(shortName)) {
                TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.CENTER);
                column.getColumn().setText(shortName);
                column.getColumn().addSelectionListener(getSelectionAdapter(column.getColumn()));
                column.setLabelProvider(new ColumnLabelProvider() {
                    @Override
                    public String getText(Object element) {
                        String text = ((CommandHistoryRecord) element).getTextForColumn(shortName);
                        return (text != null) ? text : null;
                    }

                    @Override
                    public String getToolTipText(Object element) {
                        return ((CommandHistoryRecord) element).getTooltipForColumn(shortName);
                    }

                    @Override
                    public Image getImage(Object element) {
                        String imgLoc = ((CommandHistoryRecord) element).getImageForColumn(shortName);
                        if (CommandHistoryRecordContentProvider.GREEN.equals(imgLoc))
                            return greenBubble;
                        else if (CommandHistoryRecordContentProvider.RED.equals(imgLoc))
                            return redBubble;
                        else
                            return grayBubble;
                    }
                });
                dynamicColumns.add(shortName);
                layoutDataByColumn.put(column.getColumn(), new ColumnPixelData(90));
                TableColumnLayout tcl = new TableColumnLayout();
                parent.setLayout(tcl);
                applyColumnLayoutData(tcl);
                column.getColumn().setWidth(90);
                tableViewer.getTable().layout();
            }
        }

        // Now add content
        tableContentProvider.processCommandHistoryEntry(cmdhistEntry);
    }

    private SelectionAdapter getSelectionAdapter(TableColumn column) {
        SelectionAdapter selectionAdapter = new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                tableViewerComparator.setColumn(column);
                int dir = tableViewerComparator.getDirection();
                tableViewer.getTable().setSortDirection(dir);
                tableViewer.getTable().setSortColumn(column);
                tableViewer.refresh();
            }
        };
        return selectionAdapter;
    }

    @Override
    public void setFocus() {
        tableViewer.getTable().setFocus();
    }
}