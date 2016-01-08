/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dc.jul.storage.registry.jp;

import org.dc.jps.core.JPService;
import org.dc.jps.exception.JPNotAvailableException;
import org.dc.jps.preset.AbstractJPBoolean;

/**
 *
 * @author mpohling
 */
public class JPInitializeDB extends AbstractJPBoolean {

    public final static String[] COMMAND_IDENTIFIERS = {"--init"};

    public JPInitializeDB() {
        super(COMMAND_IDENTIFIERS);
    }

    /**
     * returns true if JPS is in test mode or JPResetDB is enabled.
     * @return
     */
    @Override
    protected Boolean getPropertyDefaultValue() {
        try {
            return JPService.testMode() || JPService.getProperty(JPResetDB.class).getValue();
        } catch (JPNotAvailableException ex) {
            JPService.printError("Could not load default value!", ex);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Initialize a new instance of the internal database.";
    }
}
