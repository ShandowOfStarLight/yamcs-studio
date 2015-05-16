package org.yamcs.studio.ui.commanding.stack;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.yamcs.studio.core.YamcsPlugin;
import org.yamcs.studio.ui.YamcsUIPlugin;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.Significance.Levels;

public class AddToStackWizardPage1 extends WizardPage {

    private Image errorIcon = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_ERROR_TSK);
    private Image warnIcon = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK);
    private Image level1Image;
    private Image level2Image;
    private Image level3Image;
    private Image level4Image;
    private Image level5Image;

    private Telecommand command;

    public AddToStackWizardPage1(Telecommand command) {
        super("Choose a command");
        setTitle("Choose a command");
        this.command = command;
        setPageComplete(false);
    }

    @Override
    public void createControl(Composite parent) {
        Composite tableWrapper = new Composite(parent, SWT.NONE);
        setControl(tableWrapper);

        ResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources(), tableWrapper);
        level1Image = resourceManager.createImage(YamcsUIPlugin.getImageDescriptor("icons/level1s.png"));
        level2Image = resourceManager.createImage(YamcsUIPlugin.getImageDescriptor("icons/level2s.png"));
        level3Image = resourceManager.createImage(YamcsUIPlugin.getImageDescriptor("icons/level3s.png"));
        level4Image = resourceManager.createImage(YamcsUIPlugin.getImageDescriptor("icons/level4s.png"));
        level5Image = resourceManager.createImage(YamcsUIPlugin.getImageDescriptor("icons/level5s.png"));

        TableColumnLayout tcl = new TableColumnLayout();
        tableWrapper.setLayoutData(new GridData(GridData.FILL_BOTH));
        tableWrapper.setLayout(tcl);

        TableViewer commandsTable = new TableViewer(tableWrapper, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        commandsTable.getTable().setHeaderVisible(false);
        commandsTable.getTable().setLinesVisible(false);

        TableViewerColumn significanceColumn = new TableViewerColumn(commandsTable, SWT.NONE);
        significanceColumn.getColumn().setAlignment(SWT.CENTER);
        significanceColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return null;
            }

            @Override
            public Image getImage(Object element) {
                MetaCommand cmd = (MetaCommand) element;
                if (cmd.getDefaultSignificance() == null)
                    return null;
                switch (cmd.getDefaultSignificance().getConsequenceLevel()) {
                case watch:
                    return level1Image;
                case warning:
                    return level2Image;
                case distress:
                    return level3Image;
                case critical:
                    return level4Image;
                case severe:
                    return level5Image;
                default:
                    return null;
                }
            }
        });
        tcl.setColumnData(significanceColumn.getColumn(), new ColumnPixelData(20));

        TableViewerColumn nameColumn = new TableViewerColumn(commandsTable, SWT.NONE);
        nameColumn.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                MetaCommand cmd = (MetaCommand) element;
                return cmd.getName();
            }
        });
        tcl.setColumnData(nameColumn.getColumn(), new ColumnWeightData(100));

        commandsTable.addSelectionChangedListener(evt -> {
            IStructuredSelection sel = (IStructuredSelection) evt.getSelection();
            MetaCommand cmd = (MetaCommand) sel.getFirstElement();
            Significance significance = cmd.getDefaultSignificance();
            if (significance != null) {
                StringBuilder buf = new StringBuilder();
                if (significance.getConsequenceLevel() != Levels.none) {
                    buf.append("[");
                    buf.append(significance.getConsequenceLevel().toString().toUpperCase());
                    buf.append("] ");
                }
                if (significance.getReasonForWarning() != null) {
                    buf.append(significance.getReasonForWarning());
                }
                setMessage(buf.toString());

            } else {
                setMessage(null);
            }
            command.setMetaCommand(cmd);
            setPageComplete(true);
        });

        commandsTable.setContentProvider(ArrayContentProvider.getInstance());

        Collection<MetaCommand> nonAbstract = new ArrayList<>();
        YamcsPlugin.getDefault().getCommands().forEach(cmd -> {
            if (!cmd.isAbstract())
                nonAbstract.add(cmd);
        });
        commandsTable.setInput(nonAbstract);

        commandsTable.setComparator(new ViewerComparator() {
            @Override
            public int compare(Viewer viewer, Object o1, Object o2) {
                MetaCommand c1 = (MetaCommand) o1;
                MetaCommand c2 = (MetaCommand) o2;
                return c1.getName().compareTo(c2.getName());
            }
        });
    }
}