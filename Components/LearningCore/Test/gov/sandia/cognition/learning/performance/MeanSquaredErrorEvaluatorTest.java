/*
 * File:                MeanSquaredErrorTest.java
 * Authors:             Justin Basilico
 * Company:             Sandia National Laboratories
 * Project:             Cognitive Foundry
 *
 * Copyright September 26, 2007, Sandia Corporation.  Under the terms of Contract
 * DE-AC04-94AL85000, there is a non-exclusive license for use of this work by
 * or on behalf of the U.S. Government. Export of this program may require a
 * license from the United States Government. See CopyrightHistory.txt for
 * complete details.
 *
 *
 */

package gov.sandia.cognition.learning.performance;

import gov.sandia.cognition.learning.data.DefaultTargetEstimatePair;
import gov.sandia.cognition.learning.data.TargetEstimatePair;
import java.util.ArrayList;
import java.util.Collection;
import junit.framework.TestCase;

/**
 * This class implements JUnit tests for the following classes:
 *
 *     MeanSquaredError
 *
 * @author Justin Basilico
 * @since  2.0
 */
public class MeanSquaredErrorEvaluatorTest
    extends TestCase
{
    public MeanSquaredErrorEvaluatorTest(
        String testName)
    {
        super(testName);
    }
    
    public void testConstructors()
    {
        MeanSquaredErrorEvaluator<Object> instance = 
            new MeanSquaredErrorEvaluator<Object>();
    }

    /**
     * Test of summarize method, of class gov.sandia.cognition.learning.measure.MeanSquaredError.
     */
    public void testSummarize()
    {
        MeanSquaredErrorEvaluator<Object> instance = new MeanSquaredErrorEvaluator<Object>();
        Collection<TargetEstimatePair<Double, Double>> data = 
            new ArrayList<TargetEstimatePair<Double, Double>>();
        
        double error = 0.0;
        assertEquals(error, instance.summarize(data));
        
        data.add(new DefaultTargetEstimatePair<Double, Double>(4.7, 6.7));
        error += 2.0 * 2.0;
        assertEquals(error / 1.0, instance.summarize(data));
        
        data.add(new DefaultTargetEstimatePair<Double, Double>(4.7, 1.7));
        error += 3.0 * 3.0;
        assertEquals(error / 2.0, instance.summarize(data));
        
        data.add(new DefaultTargetEstimatePair<Double, Double>(7.0, 7.0));
        assertEquals(error / 3.0, instance.summarize(data));
        
        data.add(new DefaultTargetEstimatePair<Double, Double>(4.0, 4.0));
        assertEquals(error / 4.0, instance.summarize(data));
        
        data.add(new DefaultTargetEstimatePair<Double, Double>(10.0, 9.5));
        error += 0.5 * 0.5;
        assertEquals(error / 5.0, instance.summarize(data));
        
        boolean exceptionThrown = false;
        try
        {
            data.add(new DefaultTargetEstimatePair<Double, Double>(null, 4.0));
            instance.summarize(data);
        }
        catch ( NullPointerException e )
        {
            exceptionThrown = true;
        }
        finally
        {
            assertTrue(exceptionThrown);
        }
    }

    /**
     * Test of compute method, of class gov.sandia.cognition.learning.measure.MeanSquaredError.
     */
    public void testCompute()
    {
        // Tested by testSummarize.
    }
}
