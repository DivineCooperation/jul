/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.jul.schedule;

import de.citec.jul.exception.printer.ExceptionPrinter;
import de.citec.jul.exception.printer.LogLevel;
import org.slf4j.LoggerFactory;

/**
 *
 * @author <a href="mailto:mpohling@cit-ec.uni-bielefeld.de">Divine Threepwood</a>
 * 
 * RecurrenceEventFilter helps to filter high frequency events. 
 * After a new incoming event is processed, all further incoming events are skipped except of the last event which is executed after the defined timeout is reached. 
 */
public abstract class RecurrenceEventFilter {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RecurrenceEventFilter.class);
    
    public static final long DEFAULT_TIMEOUT = 1000;
    
    private Timeout timeout;
    
    private boolean changeDetected;

    public RecurrenceEventFilter() {
        this(DEFAULT_TIMEOUT);
    }
    
    public RecurrenceEventFilter(long timeout) {
        this.changeDetected = false;
        this.timeout = new Timeout(timeout) {
            
            @Override
            public void expired() {
                if(changeDetected) {
                    callRelay();
                }
            }
        };
    }
    
    public synchronized void trigger() {
        if(timeout.isActive()) {
            changeDetected = true;
            return;
        }
        
        changeDetected = false;
        callRelay();
        timeout.start();
    }
    
    public void cancel() {
        timeout.cancel();
    }
    
    private void callRelay() {
        try {
            relay();
        } catch (Exception ex) {
            ExceptionPrinter.printHistory(ex, logger, LogLevel.ERROR);
        }
    }
    
    public abstract void relay() throws Exception;
}
