package com.smbsoft;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class CRF {

    private int stateCount;
    private int obsCount;

    private HashMap<String, Integer> wordIndex;
    private HashMap<String, Integer> stateIndex;


    public void execute() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("src/us50.test.tagged"));
        ArrayList<String[]> sentences = new ArrayList<>();
        ArrayList<String> sentence = new ArrayList<>();

        wordIndex = new HashMap<>();
        stateIndex = new HashMap<>();

        //br.readLine(); br.readLine();

        String line;
        obsCount = 0;
        stateCount = 1; // starting state is 0

        String word, tag;

        while ( (line = br.readLine()) != null){
            if (line.isEmpty()){
                sentences.add(sentence.toArray(new String[sentence.size()]));
                sentence.clear();
            }else {
                word = line.split("\\|")[0];
                tag = line.split("\\|")[1];

                if (!wordIndex.containsKey(word))
                    wordIndex.put(word, obsCount++);

                if (!stateIndex.containsKey(tag))
                    stateIndex.put(tag, stateCount++);

                sentence.add(word);
                sentence.add(tag);
            }
        }

        br.close();

        System.out.println("stateCount = " + stateCount);
        System.out.println("obsCount = " + obsCount);

        float[] w = new float[stateCount * stateCount + stateCount * obsCount];

        Random rand = new Random();

        for(int i = 0; i < w.length; i ++){
            w[i] = rand.nextFloat();
        }

        // Need to fine tune below parameters for better training
        final float trainingRate = 0.00000001f;
        int iterations = 10;
        final float lambda = 1f;

        while(iterations-- != 0){
            // get forward values
            // get backward values

            float[] gradient = new float[stateCount * stateCount + stateCount * obsCount];

            for(String[] sent : sentences){
                float[][] forwardValues = new float[sent.length/2][stateCount];
                float[][] backwardValues = new float[sent.length/2][stateCount];

                // base case - forward and backward values
                for(int state = 1; state < stateCount; state ++){
                    forwardValues[0][state] = (float) Math.exp(getDotProduct(sent, 0, state, 0, w));
                    backwardValues[sent.length/2 - 1][state] = 1;
                }

                // forward values
                for(int obs = 1; obs < sent.length/2 ; obs ++)
                    for(int state = 1; state < stateCount; state ++)
                        for(int prev = 1; prev < stateCount; prev ++)
                            forwardValues[obs][state] += forwardValues[obs - 1][prev]
                                    + (float) Math.exp(getDotProduct(sent, prev, state, obs, w));

                // backward values
                for(int obs = sent.length/2 - 2; obs >= 0 ; obs --)
                    for(int state = 1; state < stateCount; state ++)
                        for(int prev = 1; prev < stateCount; prev ++)
                            backwardValues[obs][state] += backwardValues[obs + 1][prev]
                                    + (float) Math.exp(getDotProduct(sent, prev, state, obs, w));


                // getting the gradient
                // first part
                for(int k = 0; k < gradient.length; k ++){

                    gradient[k] += getKFeature(sent, 0 , stateIndex.get(sent[1]), 0, w, k);

                    for(int obs = 1; obs < sent.length / 2; obs ++){
                        gradient[k] += getKFeature(sent, stateIndex.get(sent[2 * obs - 1]), stateIndex.get(sent[2 * obs + 1]), obs, w, k);
                    }
                }

                // second part
                for(int k = 0; k < gradient.length; k ++){
                    // start state
                    for(int state = 1; state < stateCount; state ++){
                        gradient[k] -= getKFeature(sent, 0, state, 0, w, k)
                                * getDotProduct(sent, 0, state, 0, w)
                                * backwardValues[0][state];
                    }

                    for(int obs = 1; obs < sent.length / 2; obs ++){
                        for(int s1 = 1; s1 < stateCount; s1 ++){
                            for(int s2 = 1; s2 < stateCount; s2 ++){
                                gradient[k] -= getKFeature(sent, s1, s2, obs, w, k)
                                        * forwardValues[obs-1][s1] * getDotProduct(sent, s1, s2, obs, w)
                                        * backwardValues[obs][s2];
                            }
                        }
                    }
                }

                //System.out.println("Sentence done!");

            }


            // - lambda * wk
            for(int k = 0; k < gradient.length; k ++){
                w[k] += trainingRate * (gradient[k] - lambda * w[k]);
            }

            System.out.println("iteration = " + iterations);

            // Decoding, just for this dataset. Not the most efficient. No issue because only 7 states
            float max = -1;
            float temp = 0;
            int maxState = 1;


            int correct = 0;
            int total = 0;

            for(String[] sent : sentences) {
                // should be testing data
                // 3rd dimension is to store value, prev state ( < -- for backtracking)

                float[][][] table = new float[sent.length/2][stateCount][2];

                // base case, first observation
                for(int state = 0; state < stateCount; state ++) {
                    table[0][state][0] = getDotProduct(sent, 0, state, 0, w);
                    table[0][state][1] = 0;
                }

                for(int obs = 1; obs < sent.length / 2; obs ++){
                    for(int s1 = 1; s1 < stateCount; s1 ++){
                        for(int s2 = 1; s2 < stateCount; s2 ++){
                            temp = table[obs-1][s2][0] + getDotProduct(sent, s2, s1, obs, w);
                            if (temp > max){
                                max = temp;
                                maxState = s2;
                            }
                        }

                        table[obs][s1][0] = max;
                        table[obs][s1][1] = maxState;
                    }
                }

                max = table[sent.length / 2 - 1][1][0];
                maxState = (int) table[sent.length / 2 - 1][1][1];

                // select the max
                for(int state = 2; state < stateCount; state ++){
                    if (table[sent.length / 2 - 1][state][0] > max){
                        max = table[sent.length / 2 - 1][state][0];
                        maxState = state;
                    }
                }

                if (stateIndex.get(sent[sent.length - 1]) == temp)
                    correct ++;
                total ++;

                for(int obs = sent.length/2 - 1; obs > 0; obs --){
                    maxState = (int) table[obs][maxState][1];

                    //System.out.println(stateIndex.get(sent[obs * 2 + 1])+ " "+maxState);

                    if (stateIndex.get(sent[obs * 2 + 1]) == maxState)
                        correct ++;
                    total ++;
                }

            }


            System.out.println(String.format("Accuracy = %.2f%%\n", 100 * correct / (float) total));

        }

        float max = -1;
        float temp = 0;
        int maxState = 1;

        int correct = 0;
        int total = 0;

        for(String[] sent : sentences) {
            // should be testing data
            // 3rd dimension is to store value, prev state ( < -- for backtracking)

            float[][][] table = new float[sent.length/2][stateCount][2];

            // base case, first observation
            for(int state = 0; state < stateCount; state ++) {
                table[0][state][0] = getDotProduct(sent, 0, state, 0, w);
                table[0][state][1] = 0;
            }

            for(int obs = 1; obs < sent.length / 2; obs ++){
                for(int s1 = 1; s1 < stateCount; s1 ++){
                    for(int s2 = 1; s2 < stateCount; s2 ++){
                        temp = table[obs-1][s2][0] + getDotProduct(sent, s2, s1, obs, w);
                        if (temp > max){
                            max = temp;
                            maxState = s2;
                        }
                    }

                    table[obs][s1][0] = max;
                    table[obs][s1][1] = maxState;
                }
            }

            max = table[sent.length / 2 - 1][1][0];
            maxState = (int) table[sent.length / 2 - 1][1][1];

            // select the max
            for(int state = 2; state < stateCount; state ++){
                if (table[sent.length / 2 - 1][state][0] > max){
                    max = table[sent.length / 2 - 1][state][0];
                    maxState = state;
                }
            }


            if (stateIndex.get(sent[sent.length - 1]) == temp)
                correct ++;
            total ++;

            for(int obs = sent.length/2 - 1; obs > 0; obs --){
                maxState = (int) table[obs][maxState][1];

                //System.out.println(stateIndex.get(sent[obs * 2 + 1])+ " "+maxState);

                if (stateIndex.get(sent[obs * 2 + 1]) == maxState)
                    correct ++;
                total ++;
            }

        }

        System.out.println(String.format("Accuracy = %.2f%%\n", 100 * correct / (float) total));
    }


    private float getDotProduct(String[] sentence, int prevState, int currentState, int i, float[] w){
        // I'll model using the same parameters used in HMMs, transition and emission
        // first state * state number of elements will be taken for trans. Next obs * state elements is for emission

        // only the relevant elements will be 0, others will be 1

        // then return the sum of the relevant indexes of w

        float sum = 0;

        sum += w[prevState * stateCount + currentState];

        sum += w[stateCount * stateCount + currentState * obsCount + wordIndex.get(sentence[i * 2])]; // i * 2 because sentence format is with word, tag , word, tag

        return sum;
    }

    private float getKFeature(String[] sentence, int prevState, int currentState, int i, float[] w, int k){
        // returns the k th element of the feature vector, 0 indexed

        if (k==(prevState * stateCount + currentState) ||
                k == (stateCount * stateCount + currentState * obsCount + wordIndex.get(sentence[i * 2]))){
            return 1.0f;
        }
        return 0f;

    }


    // preprocessing data
    // generating feature functions --> feature vector
    // forward backward algorithm
}
