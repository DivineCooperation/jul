package org.openbase.jul.extension.protobuf;

/*-
 * #%L
 * JUL Extension Protobuf
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
import java8.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.exception.NotAvailableException;
import org.openbase.jul.pattern.Observable;
import org.openbase.jul.pattern.Observer;
import org.openbase.jul.pattern.provider.DataProvider;
import org.openbase.jul.schedule.Stopwatch;
import rst.domotic.state.PowerStateType.PowerState;
import rst.domotic.unit.dal.ColorableLightDataType.ColorableLightData;
import rst.timing.TimestampType.Timestamp;

/**
 *
 * @author <a href="mailto:pleminoq@openbase.org">Tamino Huxohl</a>
 */
public class MessageObservableTest {

    public MessageObservableTest() {
    }

    @BeforeClass
    public static void setUpClass() {
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
     * Test of computeHash method, of class MessageObservable.
     */
    @Test
    public void testComputeHash() throws Exception {
        System.out.println("computeHash");

        final Timestamp timestamp1 = Timestamp.newBuilder().setTime(20).build();
        final Timestamp timestamp2 = Timestamp.newBuilder().setTime(100).build();

        final PowerState powerState1 = PowerState.newBuilder().setTimestamp(timestamp1).setValue(PowerState.State.ON).build();
        final PowerState powerState2 = PowerState.newBuilder().setTimestamp(timestamp2).setValue(PowerState.State.ON).build();

        final ColorableLightData colorableLightData1 = ColorableLightData.newBuilder().setPowerState(powerState1).build();
        final ColorableLightData colorableLightData2 = ColorableLightData.newBuilder().setPowerState(powerState2).build();

        final MessageObservable<ColorableLightData> instance = new MessageObservable(new DataProvider<ColorableLightData>() {

            @Override
            public boolean isDataAvailable() {
                return true;
            }

            @Override
            public Class<ColorableLightData> getDataClass() {
                return ColorableLightData.class;
            }

            @Override
            public ColorableLightData getData() throws NotAvailableException {
                return null;
            }

            @Override
            public void addDataObserver(Observer<ColorableLightData> observer) {
            }

            @Override
            public void removeDataObserver(Observer<ColorableLightData> observer) {
            }

            @Override
            public void waitForData() throws CouldNotPerformException, InterruptedException {
            }

            @Override
            public void waitForData(long timeout, TimeUnit timeUnit) throws CouldNotPerformException, InterruptedException {
            }

            @Override
            public CompletableFuture<ColorableLightData> getDataFuture() {
                return CompletableFuture.completedFuture(null);
            }
        });

        assertEquals("Hashes are not equal even though only the timestamp has changed", instance.computeHash(colorableLightData1), instance.computeHash(colorableLightData2));

        instance.addObserver(new Observer<ColorableLightData>() {

            @Override
            public void update(Observable<ColorableLightData> source, ColorableLightData data) throws Exception {
                assertEquals("Received unexpected update", colorableLightData1, data);
            }
        });

        instance.notifyObservers(colorableLightData1);
        instance.notifyObservers(colorableLightData2);

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.start();
        for (int i = 0; i < 1000; ++i) {
            instance.computeHash(colorableLightData1);
        }
        stopwatch.stop();
        System.out.println("Computing 1000 hashes took " + stopwatch.getTime() + "ms");
    }

}
