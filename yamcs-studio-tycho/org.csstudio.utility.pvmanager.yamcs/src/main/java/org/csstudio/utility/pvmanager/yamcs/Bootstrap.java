package org.csstudio.utility.pvmanager.yamcs;

import java.util.logging.Logger;

import org.csstudio.autocomplete.AutoCompleteHelper;
import org.eclipse.ui.IStartup;

public class Bootstrap implements IStartup {
    private static final Logger log = Logger.getLogger(Bootstrap.class.getName());
    
    /**
     * TODO This is a bit of a hack to get yamcs:// datasource registered early
     * on. Surely there's a better way? This method will be triggered thanks to
     * the org.eclipse.ui.startup extension point.
     */
    @Override
    public void earlyStartup() {
        log.info("Registering datasources early on:");
        for (String prefix : AutoCompleteHelper.retrievePVManagerSupported()) {
            log.info(" - Preloaded support for " + prefix);
        }
    }
}