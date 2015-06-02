/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.jul.processing;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author mpohling
 */
public class QuaternionTransformTest {

    public QuaternionTransformTest() {
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
     * Test of transformTaitBryanToQuaternion method, of class
     * QuaternionTransform.
     */
    @Test
    public void testNeutralTransformTaitBryanToQuaternion() {
        System.out.println("testNeutralTransformTaitBryanToQuaternion");
        double roll = 0.0;
        double pitch = 0.0;
        double yaw = 0.0;
        double[] expResult = new double[4];
        expResult[0] = 1;
        expResult[1] = 0;
        expResult[2] = 0;
        expResult[3] = 0;
        double[] result = QuaternionTransform.transformTaitBryanToQuaternion(roll, pitch, yaw);
        assertArrayEquals(expResult, result, 0.1d);
    }

    @Test
    public void testTransformTaitBryanToQuaternion() {
        System.out.println("transformTaitBryanToQuaternion");
        double roll = 45.0;
        double pitch = 0.0;
        double yaw = 180.0;
        double[] expResult = new double[4];
        expResult[0] = 1;
        expResult[1] = 0;
        expResult[2] = 0;
        expResult[3] = 0;

        System.out.println("roll:" + Math.toRadians(roll));
        System.out.println("pitch:" + Math.toRadians(pitch));
        System.out.println("yaw:" + Math.toRadians(yaw));

        double[] result = QuaternionTransform.transformTaitBryanToQuaternion(roll, pitch, yaw);
        System.out.println("w:" + result[0]);
        System.out.println("x:" + result[1]);
        System.out.println("y:" + result[2]);
        System.out.println("z:" + result[3]);
        assertArrayEquals(expResult, result, 0.1d);
    }
}
