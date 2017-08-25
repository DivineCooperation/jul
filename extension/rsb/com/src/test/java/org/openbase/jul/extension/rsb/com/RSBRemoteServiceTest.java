package org.openbase.jul.extension.rsb.com;

/*-
 * #%L
 * JUL Extension RSB Communication
 * %%
 * Copyright (C) 2015 - 2017 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */
import java.util.concurrent.Callable;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openbase.jps.exception.JPServiceException;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.InvalidStateException;
import org.openbase.jul.exception.TimeoutException;
import org.openbase.jul.extension.rsb.com.RSBCommunicationServiceTest.RSBCommunicationServiceImpl;
import org.openbase.jul.pattern.Controller.ControllerAvailabilityState;
import org.openbase.jul.pattern.Remote;
import org.openbase.jul.pattern.Remote.ConnectionState;
import org.openbase.jul.schedule.GlobalCachedExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rst.domotic.registry.UnitRegistryDataType.UnitRegistryData;

/**
 *
 * @author <a href="mailto:divine@openbase.org">Divine Threepwood</a>
 */
public class RSBRemoteServiceTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public RSBRemoteServiceTest() {
    }

    @BeforeClass
    public static void setUpClass() throws JPServiceException {
//        JPService.setupJUnitTestMode();
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of waitForConnectionState method, of class RSBRemoteService.
     *
     * @throws java.lang.InterruptedException
     * @throws org.openbase.jul.exception.CouldNotPerformException
     */
    @Test(timeout = 10000)
    public void testWaitForConnectionState() throws InterruptedException, CouldNotPerformException {
        System.out.println("waitForConnectionState");
        RSBRemoteService instance = new RSBCommunicationServiceTest.RSBRemoteServiceImpl();
        instance.init("/test/waitForConnectionState");

        // Test not active handling
        try {
            instance.waitForConnectionState(Remote.ConnectionState.CONNECTING, 10);
            Assert.fail("No exception thrown.");
        } catch (InvalidStateException ex) {
            // should be thrown...
            Assert.assertTrue(true);
        }

        // Test Timeout
        instance.activate();

        try {
            instance.waitForConnectionState(Remote.ConnectionState.CONNECTED, 10);
            Assert.fail("No exception thrown.");
        } catch (TimeoutException ex) {
            // should be thrown...
            Assert.assertTrue(true);
        }

        // Test if shutdown is blocked by waitForConnection without timeout
        System.out.println("Test if waitForConnection is interrupted through shutdown!");
        GlobalCachedExecutorService.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    System.out.println("Thread is running");
                    assertTrue("Instance is not active while waiting", instance.isActive());
                    System.out.println("Wait for ConnectionState");
                    instance.waitForConnectionState(Remote.ConnectionState.CONNECTED);
                } catch (CouldNotPerformException | InterruptedException ex) {
//                    ExceptionPrinter.printHistoryAndReturnThrowable(ex, logger);
                }
                return null;
            }
        });

        Thread.sleep(100);

        instance.shutdown();
    }

//    @Test(timeout = 5000)
    @Test
    public void testDeactivation() throws InterruptedException, CouldNotPerformException {
        System.out.println("testDeactivation");

        RSBRemoteService instance = new RSBCommunicationServiceTest.RSBRemoteServiceImpl();
        instance.init("/test/testDeactivation");
        instance.activate();

        RSBCommunicationServiceTest.RSBCommunicationServiceImpl communicationService = new RSBCommunicationServiceImpl(UnitRegistryData.newBuilder());
        communicationService.init("/test/testDeactivation");
        communicationService.activate();
        communicationService.waitForAvailabilityState(ControllerAvailabilityState.ONLINE);
        instance.waitForConnectionState(ConnectionState.CONNECTED);
        instance.waitForData();
        System.out.println("shutdown...");
        System.out.println("main thread name: " + Thread.currentThread().getName());
        communicationService.deactivate();
        instance.deactivate();
        communicationService.shutdown();
        instance.shutdown();
    }
}
