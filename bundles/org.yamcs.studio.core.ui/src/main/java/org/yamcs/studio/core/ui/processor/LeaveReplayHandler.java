package org.yamcs.studio.core.ui.processor;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.yamcs.protobuf.ClientInfo;
import org.yamcs.protobuf.EditClientRequest;
import org.yamcs.studio.core.model.ManagementCatalogue;

public class LeaveReplayHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ManagementCatalogue catalogue = ManagementCatalogue.getInstance();
        ClientInfo clientInfo = catalogue.getCurrentClientInfo();
        EditClientRequest req = EditClientRequest.newBuilder().setProcessor("realtime").build();
        catalogue.editClientRequest(clientInfo.getId(), req);

        return null;
    }
}
