/*
 * File:                GaussianConfidence.java
 * Authors:             Kevin R. Dixon
 * Company:             Sandia National Laboratories
 * Project:             Cognitive Foundry
 *
 * Copyright August 16, 2007, Sandia Corporation.  Under the terms of Contract
 * DE-AC04-94AL85000, there is a non-exclusive license for use of this work by
 * or on behalf of the U.S. Government. Export of this program may require a
 * license from the United States Government. See CopyrightHistory.txt for
 * complete details.
 *
 */

package gov.sandia.cognition.statistics.method;

import gov.sandia.cognition.annotation.PublicationReference;
import gov.sandia.cognition.annotation.PublicationType;
import gov.sandia.cognition.math.UnivariateStatisticsUtil;
import gov.sandia.cognition.statistics.ScalarDistribution;
import gov.sandia.cognition.statistics.distribution.UnivariateGaussian;
import gov.sandia.cognition.util.AbstractCloneableSerializable;
import gov.sandia.cognition.util.Pair;
import java.util.Collection;

/**
 * This test is sometimes called the "Z test"
 * Defines a range of values that the statistic can take, as well as the
 * confidence that the statistic is between the lower and upper bounds.  This
 * test is useful in those situations where the tested data were generated by
 * a (univariate) Gaussian distribution.
 * @author Kevin R. Dixon
 * @since  2.0
 *
 */
@ConfidenceTestAssumptions(
    name="Gaussian Z-test",
    alsoKnownAs="Z-test",
    description="Determines if two populations have the same mean, if the populations are Gaussian and relatively large, at least 30 or so.",
    assumptions={
        "The two groups are sampled independently of each other.",
        "The two groups are sampled from a Gaussian distribution, or the underlying distributions are non-Gaussian but obey the weak law of large numbers.",
        "The variances of the two groups are equal."
    },
    nullHypothesis="The means of the groups are equal.",
    dataPaired=false,
    dataSameSize=false,
    distribution=UnivariateGaussian.CDF.class,
    reference=@PublicationReference(
        author="Wikipedia",
        title="Z-test",
        type=PublicationType.WebPage,
        year=2009,
        url="http://en.wikipedia.org/wiki/Z-test"
    )
)
public class GaussianConfidence
    extends AbstractCloneableSerializable
    implements NullHypothesisEvaluator<Collection<? extends Double>>,
    ConfidenceIntervalEvaluator<Collection<? extends Double>>
{

    /** Creates a new instance of GaussianConfidence */
    public GaussianConfidence()
    {
    }

    public GaussianConfidence.Statistic evaluateNullHypothesis(
        Collection<? extends Double> data1,
        Collection<? extends Double> data2 )
    {
        int N1 = data1.size();
        UnivariateGaussian g1 = UnivariateGaussian.MaximumLikelihoodEstimator.learn( data1, 0.0 );
        double std1 = Math.sqrt( g1.getVariance() );

        int N2 = data2.size();
        UnivariateGaussian g2 = UnivariateGaussian.MaximumLikelihoodEstimator.learn( data2, 0.0 );
        double std2 = Math.sqrt( g2.getVariance() );

        double numerator = Math.abs( g1.getMean() - g2.getMean() );
        double denominator = Math.sqrt( ((std1 * std1) / N1) + ((std2 * std2) / N2) );

        double z = numerator / denominator;

        return new GaussianConfidence.Statistic( z );
    }

    /**
     * Computes the probability that the input was drawn from the estimated
     * UnivariateGaussian distribution.  That is, what is the probability
     * that the UnivariateGaussian could produce a MORE UNLIKELY sample than
     * the given "input".  For example, the probability of drawing a more
     * unlikely sample that the mean is 1.0 and infinity is 0.0
     *
     * @param data1 Dataset to consider
     * @param data2 Sample to compute the probability that a
     * UnivariateGaussian would produce a more unlikely sample than "data2"
     * @return probability that the input was drawn from this estimated
     * UnivariateGaussian distribution.  That is, what is the probability
     * that the UnivariateGaussian could produce a MORE UNLIKELY sample than
     * the given input
     */
    public static GaussianConfidence.Statistic evaluateNullHypothesis(
        Collection<? extends Double> data1,
        double data2 )
    {

        Pair<Double,Double> result =
            UnivariateStatisticsUtil.computeMeanAndVariance(data1);
        double mean = result.getFirst();
        double variance = result.getSecond();

        // This will tell us the probability of the left tail of the Gaussian
        double delta = Math.abs( mean - data2 );
        double z;
        if( variance != 0.0 )
        {
            z = delta / Math.sqrt( variance );
        }
        else if( delta != 0.0 )
        {
            z = Double.POSITIVE_INFINITY;
        }
        else
        {
            z = 0.0;
        }

        // This should actually be the student-t distribution with
        // this.getNumSamples() number of degrees of freedom.  However, since
        // we expect the number of samples to be >30 or so, then the
        // Gaussian and Student-t distribution (N>=30) are approximately
        // equal.
        System.out.println( "Z = " + z );
        return new GaussianConfidence.Statistic( z );

    }

    public ConfidenceInterval computeConfidenceInterval(
        Collection<? extends Double> data,
        double confidence )
    {
        UnivariateGaussian g = UnivariateGaussian.MaximumLikelihoodEstimator.learn(
            data, UnivariateGaussian.MaximumLikelihoodEstimator.DEFAULT_VARIANCE );

        return GaussianConfidence.computeConfidenceInterval(
            g, data.size(), confidence );
    }

    /**
     * Computes the Gaussian confidence interval given a distribution of
     * data, number of samples, and corresponding confidence interval
     * @param dataDistribution
     * UnivariateGaussian describing the distribution of the underlying data
     * @param numSamples
     * Number of samples in the underlying data
     * @param confidence
     * Confidence value to assume for the ConfidenceInterval
     * @return
     * ConfidenceInterval capturing the range of the mean of the data
     * at the desired level of confidence
     */
    public static ConfidenceInterval computeConfidenceInterval(
        ScalarDistribution<?> dataDistribution,
        int numSamples,
        double confidence )
    {
        return computeConfidenceInterval(
            dataDistribution.getMean().doubleValue(),
            dataDistribution.getVariance(),
            numSamples, confidence );
    }
    
    /**
     * Computes the Gaussian confidence interval given a distribution of
     * data, number of samples, and corresponding confidence interval
     * @param mean
     * Mean of the distribution.
     * @param variance
     * Variance of the distribution.
     * @param numSamples
     * Number of samples in the underlying data
     * @param confidence
     * Confidence value to assume for the ConfidenceInterval
     * @return
     * ConfidenceInterval capturing the range of the mean of the data
     * at the desired level of confidence
     */
    @PublicationReference(
        author="Wikipedia",
        title="Standard error (statistics)",
        type=PublicationType.WebPage,
        year=2009,
        url="http://en.wikipedia.org/wiki/Standard_error_(statistics)"
    )
    public static ConfidenceInterval computeConfidenceInterval(
        double mean,
        double variance,
        int numSamples,
        double confidence )
    {
        double alpha = 1.0 - confidence;
        double z = -UnivariateGaussian.CDF.Inverse.evaluate(
            0.5 * alpha, 0.0, 1.0 );
        double delta = z * Math.sqrt( variance / numSamples );

        return new ConfidenceInterval(
            mean, mean - delta, mean + delta, confidence, numSamples );        
    }

    /**
     * Confidence statistics for a Gaussian distribution
     */
    public static class Statistic
        extends AbstractConfidenceStatistic
    {

        /**
         * Value that is used in the Gaussian CDF to compute
         * probability.  Usually just called "z-statistic"
         */
        private double z;

        /**
         * Creates a new instance of Statistic
         * @param z
         * Value that is used in the Inverse Gaussian CDF to compute
         * probability.  Usually just called "z-statistic"
         */
        public Statistic(
            double z )
        {
            super( 2.0 * UnivariateGaussian.CDF.evaluate( -z, 0.0, 1.0 ) );
            this.setZ( z );
        }

        /**
         * Setter for z
         * @return
         * Value that is used in the Inverse Gaussian CDF to compute
         * probability.  Usually just called "z-statistic"
         */
        public double getZ()
        {
            return this.z;
        }

        /**
         * Getter for z
         * @param z
         * Value that is used in the Inverse Gaussian CDF to compute
         * probability.  Usually just called "z-statistic"
         */
        protected void setZ(
            double z )
        {
            this.z = z;
        }

    }

}
