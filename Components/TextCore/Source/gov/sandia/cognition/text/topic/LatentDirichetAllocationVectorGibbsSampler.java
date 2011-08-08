/*
 * File:                LatentDirichetAllocationVectorGibbsSampler.java
 * Authors:             Justin Basilico
 * Company:             Sandia National Laboratories
 * Project:             Cognitive Foundry
 * 
 * Copyright October 22, 2010, Sandia Corporation.
 * Under the terms of Contract DE-AC04-94AL85000, there is a non-exclusive 
 * license for use of this work by or on behalf of the U.S. Government. Export 
 * of this program may require a license from the United States Government. 
 * See CopyrightHistory.txt for complete details.
 * 
 */

package gov.sandia.cognition.text.topic;

import gov.sandia.cognition.annotation.PublicationReference;
import gov.sandia.cognition.annotation.PublicationReferences;
import gov.sandia.cognition.annotation.PublicationType;
import gov.sandia.cognition.collection.CollectionUtil;
import gov.sandia.cognition.learning.algorithm.AbstractAnytimeBatchLearner;
import gov.sandia.cognition.learning.data.DatasetUtil;
import gov.sandia.cognition.statistics.DiscreteSamplingUtil;
import gov.sandia.cognition.math.matrix.VectorEntry;
import gov.sandia.cognition.math.matrix.Vectorizable;
import gov.sandia.cognition.util.AbstractCloneableSerializable;
import gov.sandia.cognition.util.ArgumentChecker;
import gov.sandia.cognition.util.Randomized;
import java.util.Collection;
import java.util.Random;

/**
 * A Gibbs sampler for performing Latent Dirichlet Allocation (LDA). It operates
 * on input vectors that are expected to have positive integer counts.
 * The LDA model uses a fixed set of latent topics as a generative model
 * for term occurrences in documents. Thus, each document is a mixture of
 * different topics. This implementation uses a Gibbs sampling version of
 * Markov Chain Monte Carlo algorithm to estimate the parameters of the model.
 * 
 * @author  Justin Basilico
 * @since   3.1
 */
@PublicationReferences(
    references={
        @PublicationReference(
            author={"David M. Blei", "Andrew Y. Ng", "Michael I. Jordan"},
            title="Latent Dirichlet Allocation",
            year=2003,
            type=PublicationType.Journal,
            publication="Journal of Machine Learning Research",
            pages={993, 1022},
            url="http://www.cs.princeton.edu/~blei/papers/BleiNgJordan2003.pdf"),
        @PublicationReference(
            author="Gregor Heinrich",
            title="Parameter estimation for text analysis",
            year=2009,
            type=PublicationType.TechnicalReport,
            url="http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.149.1327&rep=rep1&type=pdf")
    }
)
public class LatentDirichetAllocationVectorGibbsSampler
    extends AbstractAnytimeBatchLearner<Collection<? extends Vectorizable>, LatentDirichetAllocationVectorGibbsSampler.Result>
    implements Randomized
//    implements MarkovChainMonteCarlo<Object, Object>
// TODO: Implement the MCMC interface.
{

    /** The default topic count is {@value}. */
    public static final int DEFAULT_TOPIC_COUNT = 10;

    /** The default value of alpha is {@value}. */
    public static final double DEFAULT_ALPHA = 5.0;

    /** The default value of beta is {@value}. */
    public static final double DEFAULT_BETA = 0.5;

    /** The default maximum number is iterations is {@value}. */
    public static final int DEFAULT_MAX_ITERATIONS = 10000;

    /** The default number of burn-in iterations is {@value}. */
    public static final int DEFAULT_BURN_IN_ITERATIONS = 2000;

    /** The default number of iterations per sample is {@value}. */
    public static final int DEFAULT_ITERATIONS_PER_SAMPLE = 100;

    /** The number of topics for the algorithm to create. */
    protected int topicCount;

    /** The alpha parameter controlling the Dirichlet distribution for the
     * document-topic probabilities. It acts as a prior weight assigned to
     * the document-topic counts. */
    protected double alpha;

    /** The beta parameter controlling the Dirichlet distribution for the
     * topic-term probabilities. It acts as a prior weight assigned to
     * the topic-term counts. */
    protected double beta;

    /** The number of burn-in iterations for the Markov Chain Monte Carlo
     *  algorithm to run before sampling begins. */
    protected int burnInIterations;

    /** The number of iterations to the Markov Chain Monte Carlo algorithm
     *  between samples (after the burn-in iterations). */
    protected int iterationsPerSample;

    /** The random number generator to use. */
    protected Random random;

    /** The number of documents in the dataset. */
    protected transient int documentCount;

    /** The number of terms in the dataset. */
    protected transient int termCount;

    /** For each document, the number of terms assigned to each topic. Thus,
     *  the first index is a document index and the second is a term index. */
    protected transient int[][] documentTopicCount;

    /** The number of term occurrences in each document. */
    protected transient int[] documentTopicSum;

    /** For each topic, the number of occurrences assigned to each term. Thus,
     *  the first index is a topic index and the second is a term index. */
    protected transient int[][] topicTermCount;

    /** The number of term occurrences assigned to each term. */
    protected transient int[] topicTermSum;

    /** The assignments of term occurrences to topics. */
    protected transient int[] occurrenceTopicAssignments;

    /** The number of model parameter samples that have been made. */
    protected transient int sampleCount;

    /** The result probabilities. Note that if multiple samples are taken, this
     *  will be a sum of the probabilities for the different samples until the
     *  algorithm is done and they are turned into an average. */
    protected transient Result result;

    /**
     * Creates a new {@code LatentDirichetAllocationVectorGibbsSampler} with
     * default parameters.
     */
    public LatentDirichetAllocationVectorGibbsSampler()
    {
        this(DEFAULT_TOPIC_COUNT, DEFAULT_ALPHA, DEFAULT_BETA,
            DEFAULT_MAX_ITERATIONS, DEFAULT_BURN_IN_ITERATIONS,
            DEFAULT_ITERATIONS_PER_SAMPLE, new Random());
    }

    /**
     * Creates a new {@code LatentDirichetAllocationVectorGibbsSampler} with
     * the given parameters.
     *
     * @param   topicCount
     *      The number of topics for the algorithm to create. Must be positive.
     * @param   alpha
     *      The alpha parameter controlling the Dirichlet distribution for the
     *      document-topic probabilities. It acts as a prior weight assigned to
     *      the document-topic counts. Must be positive.
     * @param   beta
     *      The beta parameter controlling the Dirichlet distribution for the
     *      topic-term probabilities. It acts as a prior weight assigned to
     *      the topic-term counts.
     * @param   maxIterations
     *      The maximum number of iterations to run for. Must be positive.
     * @param   burnInIterations
     *      The number of burn-in iterations for the Markov Chain Monte Carlo
     *      algorithm to run before sampling begins.
     * @param   iterationsPerSample
     *      The number of iterations to the Markov Chain Monte Carlo algorithm
     *      between samples (after the burn-in iterations).
     * @param   random
     *      The random number generator to use.
     */
    public LatentDirichetAllocationVectorGibbsSampler(
        final int topicCount,
        final double alpha,
        final double beta,
        final int maxIterations,
        final int burnInIterations,
        final int iterationsPerSample,
        final Random random)
    {
        super(maxIterations);

        this.setTopicCount(topicCount);
        this.setAlpha(alpha);
        this.setBeta(beta);
        this.setBurnInIterations(burnInIterations);
        this.setIterationsPerSample(iterationsPerSample);
        this.setRandom(random);
    }

    @Override
    protected boolean initializeAlgorithm()
    {
        if (CollectionUtil.isEmpty(this.data))
        {
            // Can't run the algorithm on empty data.
            return false;
        }

        // Count the number of documents and number of terms.
        this.documentCount = this.data.size();
        this.termCount = DatasetUtil.getDimensionality(this.data);

        // Initialize all of the data structures.
        this.documentTopicCount = new int[this.documentCount][this.topicCount];
        this.documentTopicSum = new int[this.documentCount];
        this.topicTermCount = new int[this.topicCount][this.termCount];
        this.topicTermSum = new int[this.termCount];

        // Initialize the model parameter arrays.
        this.sampleCount = 0;
        this.result = new Result(
            this.topicCount, this.documentCount, this.termCount);

        // Count up the total number of word occurrences in the data.
        int totalOccurrences = 0;
        for (Vectorizable m : this.data)
        {
            totalOccurrences += (int) m.convertToVector().norm1();
        }
        this.occurrenceTopicAssignments = new int[totalOccurrences];

        // Initialize all the counts by randomly assigning words to topics.
        int document = 0;
        int occurrence = 0;
        for (Vectorizable m : this.data)
        {
            for (VectorEntry v : m.convertToVector())
            {
                final int term = v.getIndex();
                final int count = (int) v.getValue();

                for (int i = 1; i <= count; i++)
                {
                    // Pick a random topic for each word (occurrence).
                    final int topic = this.random.nextInt(this.topicCount);

                    // Increment the counters for the document, term, and topic.
                    this.documentTopicCount[document][topic] += 1;
                    this.documentTopicSum[document] += 1;
                    this.topicTermCount[topic][term] += 1;
                    this.topicTermSum[topic] += 1;
                    this.occurrenceTopicAssignments[occurrence] = topic;
                    occurrence++;
                }
            }
            document++;
        }

        // TODO: Compute the likelihood of the parameter set to track
        // convergence.
        // -- jdbasil (2010-10-30)
        return true;
    }

    @Override
    protected boolean step()
    {
        // We create this array to be used as a workspace to avoid having to
        // recreate it inside the sampling function.
        final double[] topicCumulativeProportions = new double[this.topicCount];
        int document = 0;
        int occurrence = 0;
        for (Vectorizable m : this.data)
        {
            for (VectorEntry v : m.convertToVector())
            {
                final int term = v.getIndex();
                final int count = (int) v.getValue();

                for (int i = 1; i <= count; i++)
                {
                    // Get the old topic assignment.
                    final int oldTopic =
                        this.occurrenceTopicAssignments[occurrence];

                    // Remove the topic assignment .
                    this.documentTopicCount[document][oldTopic] -= 1;
                    this.documentTopicSum[document] -= 1;
                    this.topicTermCount[oldTopic][term] -= 1;
                    this.topicTermSum[oldTopic] -= 1;

                    // Sample a new topic.
                    final int newTopic = this.sampleTopic(document, term,
                        topicCumulativeProportions);

                    // Add the new topic assignment.
                    this.occurrenceTopicAssignments[occurrence] = newTopic;
                    this.documentTopicCount[document][newTopic] += 1;
                    this.documentTopicSum[document] += 1;
                    this.topicTermCount[newTopic][term] += 1;
                    this.topicTermSum[newTopic] += 1;
                    occurrence++;
                }
            }
            document++;
        }

        if (this.iteration >= this.burnInIterations
            && (this.iteration - this.burnInIterations)
                % this.iterationsPerSample == 0)
        {
            this.readParameters();
        }

        return true;
    }

    /**
     * Samples a topic for a given document and term.
     * 
     * @param   document
     *      The document index.
     * @param   term
     *      The term index.
     * @param   topicCumulativeProportions
     *      The array to use to store the proportions in.
     * @return
     *      A topic index sampled from the topic probabilities of the given
     *      document and term.
     */
    protected int sampleTopic(
        final int document,
        final int term,
        final double[] topicCumulativeProportions)
    {
        // Loop over all the topics to compute their cumulative proportions.
        double cumulativeProportionSum = 0.0;
        for (int topic = 0; topic < this.topicCount; topic++)
        {
            // Compute the proportion for this topic.
            final double numerator =
                (this.topicTermCount[topic][term] + this.beta) *
                (this.documentTopicCount[document][topic] + this.alpha);
            final double denominator =
                (this.topicTermSum[topic] + this.termCount * this.beta);
            final double p = numerator / denominator;

            // Add the proportion to the sum to make it cumulative and store it
            // in the array.
            cumulativeProportionSum += p;
            topicCumulativeProportions[topic] = cumulativeProportionSum;
        }

        // Randomly sample from the distribution.
        return DiscreteSamplingUtil.sampleIndexFromCumulativeProportions(this.random,
            topicCumulativeProportions);
    }

    @Override
    protected void cleanupAlgorithm()
    {
        if (this.sampleCount <= 0)
        {
            // We haven't made a sample yet, so do one.
            this.readParameters();
        }
        else if (this.sampleCount > 1)
        {
            // We had more than one sample, so turn the sum into an average.

            // Make the topic-term into probabilities by taking an average.
            for (int topic = 0; topic < this.topicCount; topic++)
            {
                for (int term = 0; term < this.termCount; term++)
                {
                    this.result.topicTermProbabilities[topic][term]
                        /= this.sampleCount;
                }
            }

            // Make the document-topic into probabilities by taking an average.
            for (int document = 0; document < this.documentCount; document++)
            {
                for (int topic = 0; topic < this.topicCount; topic++)
                {
                    this.result.documentTopicProbabilities[document][topic]
                        /= this.sampleCount;
                }
            }
        }
    }

    /**
     * Reads the current set of parameters.
     */
    protected void readParameters()
    {
        // We're doing a sample of the parameters.
        this.sampleCount++;

        // Update the topic-term probabilities.
        final double termCountTimesBeta = this.termCount * this.beta;
        for (int topic = 0; topic < this.topicCount; topic++)
        {
            for (int term = 0; term < this.termCount; term++)
            {
                this.result.topicTermProbabilities[topic][term] +=
                      (this.topicTermCount[topic][term] + this.beta)
                    / (this.topicTermSum[topic] + termCountTimesBeta);
            }
        }

        // Update the document-topic probabilities.
        final double topicCountTimesAlpha = this.topicCount * this.alpha;
        for (int document = 0; document < this.documentCount; document++)
        {
            for (int topic = 0; topic < this.topicCount; topic++)
            {
                this.result.documentTopicProbabilities[document][topic] +=
                      (this.documentTopicCount[document][topic] + this.alpha)
                    / (this.documentTopicSum[document] + topicCountTimesAlpha);
            }
        }

    }

    public Result getResult()
    {
        return this.result;
    }

    /**
     * Gets the number of topics (k) created by the topic model.
     *
     * @return
     *      The number of topics created by the topic model. Must be greater
     *      than zero.
     */
    public int getTopicCount()
    {
        return this.topicCount;
    }

    /**
     * Sets the number of topics (k) created by the topic model.
     *
     * @param   topicCount
     *      The number of topics created by the topic model. Must be greater
     *      than zero.
     */
    public void setTopicCount(
        final int topicCount)
    {
        ArgumentChecker.assertIsPositive("topicCount", topicCount);
        this.topicCount = topicCount;
    }

    /**
     * Gets the alpha parameter controlling the Dirichlet distribution for the
     * document-topic probabilities. It acts as a prior weight assigned to
     * the document-topic counts.
     *
     * @return
     *      The alpha parameter.
     */
    public double getAlpha()
    {
        return this.alpha;
    }

    /**
     * Sets the alpha parameter controlling the Dirichlet distribution for the
     * document-topic probabilities. It acts as a prior weight assigned to
     * the document-topic counts.
     *
     * @param   alpha
     *      The alpha parameter. Must be positive.
     */
    public void setAlpha(
        final double alpha)
    {
        ArgumentChecker.assertIsPositive("alpha", alpha);
        this.alpha = alpha;
    }

    /**
     * Gets the beta parameter controlling the Dirichlet distribution for the
     * topic-term probabilities. It acts as a prior weight assigned to
     * the topic-term counts.
     *
     * @return
     *      The beta parameter.
     */
    public double getBeta()
    {
        return this.beta;
    }

    /**
     * Sets the beta parameter controlling the Dirichlet distribution for the
     * topic-term probabilities. It acts as a prior weight assigned to
     * the topic-term counts.
     *
     * @param   beta
     *      The beta parameter. Must be positive.
     */
    public void setBeta(
        final double beta)
    {
        ArgumentChecker.assertIsPositive("beta", beta);
        this.beta = beta;
    }

    /**
     * Gets he number of burn-in iterations for the Markov Chain Monte Carlo
     * algorithm to run before sampling begins. Note that if this number is
     * greater than the maximum number of iterations, it will only run up to
     * the maximum number of iterations and will only generate one parameter
     * sample.
     *
     * @return
     *      The number of burn-in iterations. Must be non-negative.
     */
    public int getBurnInIterations()
    {
        return this.burnInIterations;
    }

    /**
     * Sets he number of burn-in iterations for the Markov Chain Monte Carlo
     * algorithm to run before sampling begins. Note that if this number is
     * greater than the maximum number of iterations, it will only run up to
     * the maximum number of iterations and will only generate one parameter
     * sample.
     *
     * @param   burnInIterations
     *      The number of burn-in iterations. Must be non-negative.
     */
    public void setBurnInIterations(
        final int burnInIterations)
    {
        ArgumentChecker.assertIsNonNegative("burnInIterations",
            burnInIterations);
        this.burnInIterations = burnInIterations;
    }

    /**
     * Gets the number of iterations to the Markov Chain Monte Carlo algorithm
     * between samples (after the burn-in iterations).
     *
     * @return
     *      The number of iterations between samples.
     */
    public int getIterationsPerSample()
    {
        return iterationsPerSample;
    }

    /**
     * Sets the number of iterations to the Markov Chain Monte Carlo algorithm
     * between samples (after the burn-in iterations).
     *
     * @param   iterationsPerSample
     *      The number of iterations between samples. Must be positive.
     */
    public void setIterationsPerSample(
        final int iterationsPerSample)
    {
        ArgumentChecker.assertIsPositive("iterationsPerSample",
            iterationsPerSample);
        this.iterationsPerSample = iterationsPerSample;
    }
    
    public Random getRandom()
    {
        return this.random;
    }

    public void setRandom(
        final Random random)
    {
        this.random = random;
    }

    /**
     * Gets the number of documents in the dataset.
     *
     * @return
     *      The number of documents.
     */
    public int getDocumentCount()
    {
        return this.documentCount;
    }

    /**
     * Gets the number of terms in the dataset.
     *
     * @return
     *      The number of terms.
     */
    public int getTermCount()
    {
        return this.termCount;
    }

    /**
     * Represents the result of performing Latent Dirichlet Allocation.
     */
    public static class Result
        extends AbstractCloneableSerializable
    {

        /** The topic-term probabilities, which are the often called the phi model
         *  parameters. Note that if multiple samples are taken, this will be a
         *  sum of the probabilities for the different samples until the algorithm
         *  is done and they are turned into an average. */
        protected double[][] topicTermProbabilities;

        /** The document-topic probabilities, which are often called the theta
         *  model parameters. Note that if multiple samples are taken, this will be
         *  a sum of the probabilities for the different samples until the
         *  algorithm is done and they are turned into an average. */
        protected double[][] documentTopicProbabilities;

        /**
         * Creates a new {@code Result}.
         *
         * @param   topicCount
         *      The number of topics.
         * @param   documentCount
         *      The number of documents.
         * @param   termCount
         *      The number of terms.
         */
        public Result(
            final int topicCount,
            final int documentCount,
            final int termCount)
        {
            super();

            this.topicTermProbabilities = new double[topicCount][termCount];
            this.documentTopicProbabilities =
                new double[documentCount][topicCount];
        }

        /**
         * Gets the number of topics (k) created by the topic model.
         *
         * @return
         *      The number of topics created by the topic model.
         */
        public int getTopicCount()
        {
            return this.topicTermProbabilities.length;
        }

        /**
         * Gets the number of documents in the dataset.
         *
         * @return
         *      The number of documents.
         */
        public int getDocumentCount()
        {
            return this.documentTopicProbabilities.length;
        }

        /**
         * Gets the number of terms in the dataset.
         *
         * @return
         *      The number of terms.
         */
        public int getTermCount()
        {
            return this.topicTermProbabilities[0].length;
        }

        /**
         * Gets the topic-term probabilities, which are the often called the phi
         * model parameters.
         *
         * @return
         *      The topic-term probabilities.
         */
        public double[][] getDocumentTopicProbabilities()
        {
            return this.documentTopicProbabilities;
        }

        /**
         * Sets the topic-term probabilities, which are the often called the phi
         * model parameters.
         *
         * @param   documentTopicProbabilities
         *      The topic-term probabilities.
         */
        public void setDocumentTopicProbabilities(
            final double[][] documentTopicProbabilities)
        {
            this.documentTopicProbabilities = documentTopicProbabilities;
        }

        /**
         * Gets the document-topic probabilities, which are often called the
         * theta model parameters.
         *
         * @return
         *      The document-topic probabilities.
         */
        public double[][] getTopicTermProbabilities()
        {
            return this.topicTermProbabilities;
        }

        /**
         * Sets the document-topic probabilities, which are often called the
         * theta model parameters.
         *
         * @param   topicTermProbabilities
         *      The document-topic probabilities.
         */
        public void setTopicTermProbabilities(
            final double[][] topicTermProbabilities)
        {
            this.topicTermProbabilities = topicTermProbabilities;
        }

    }

}
