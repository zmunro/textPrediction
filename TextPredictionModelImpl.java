package com.slug.textPrediction;

import com.slug.common.*;

import java.util.ArrayList;
import java.util.List;


import java.util.*;
import java.lang.*;
import java.io.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


// Represents a probability value. Built from two probabilities, one derived from context
// and one derived from corpus data
class ProbTuple{
  double staticProb;
  double dynamicProb;

  // This can be modified to weight context higher or lower
  Double weight() {
    return this.staticProb ;
  }

  ProbTuple(double staticProb) {
    this.staticProb = staticProb;
    this.dynamicProb = 0.5;
  }
}

class SortByProb implements Comparator<SecondWord> {
  public int compare(SecondWord a, SecondWord b) {
    return (int)((b.prob.weight() - a.prob.weight()) * 100);
  }
}

class SecondWord {
  String word;
  ProbTuple prob;

  SecondWord(String word, Double staticProb) {
    this.word = word;
    this.prob = new ProbTuple(staticProb);
  }
}

class FirstWord {
  String word;
  PriorityQueue<SecondWord> secondWords;

  PriorityQueue<SecondWord> getSecondWords() {
    return new PriorityQueue<SecondWord>(secondWords);
  }

  FirstWord(String word) {
    this.word = word;
    this.secondWords = new PriorityQueue<SecondWord>(new SortByProb());
  }
}




class TextPredictionModel {
    private static SyllableCounterImpl syllableCounter = new SyllableCounterImpl();
    private static Log log = LogFactory.getLog(TextPredictionModel.class);
    private HashMap<String, FirstWord> firstWords;

  TextPredictionModel(File file) {

    if (file == null) {
      log.error("null file pointer");
      return;
    }
    Scanner sc = null;
    try {
      sc = new Scanner(file);
    } catch (FileNotFoundException ex) {
      log.error(ex);
    }

    HashMap<String, FirstWord> words = new HashMap<>();

    FirstWord fst = new FirstWord("");
    SecondWord snd;
    String line;


    while (sc.hasNextLine()) {
      line = sc.nextLine().replaceAll("\\s+","");
      if (line.indexOf(':') > 0) {
        // new firstword
        line = line.replace(":","");
        fst = new FirstWord(line);
      } else if (line.indexOf(',') > 0) {
        // new secondword
        snd = new SecondWord(line.split(",")[0], Double.parseDouble(line.split(",")[1]));
        fst.secondWords.add(snd);
      } else if (line.indexOf('-') >= 0) {
        //end of bigram
        words.put(fst.word, fst);
      }
    }
    this.firstWords = words;
  }

  // recursively builds sentences from bigram data
  // branchFactor: how many bigrams you look at for each initial word
  // threshold:    how many bigrams you look at until you have reached a cumulative confidence probability
  // acc:          the accumulator, containing the sentence so far
  private ArrayList<Prediction> buildSentence(int branchFactor, double threshold, Prediction acc) {

    String fst = acc.sentence.split(" ")[acc.sentence.split(" ").length - 1];
    // snds is a copy so it can be deconstructed while iterated over
    if (!firstWords.containsKey(fst)) {
      return new ArrayList<>();
    }

    // words associated with the last word in the sentence so far
    PriorityQueue<SecondWord> snds = firstWords.get(fst).getSecondWords();
    double cumulativeProb = 0.0;
    int count = 0;
    SecondWord sndWord;
    ArrayList<Prediction> sentences = new ArrayList<>();

    // break if we've gone through all words, cumulative prob has been reached, branch factor has been reached,
    // or we have gotten caught in a loop (more than 10 words in sentence)
    while(  snds.size() > 0 &&
            cumulativeProb < threshold &&
            count < branchFactor &&
            acc.sentence.split(" ").length < 10) {
      sndWord = snds.poll();
      String newPrediction = acc.sentence + " " + sndWord.word;
      double prob = sndWord.prob.weight() * acc.probability;
      Prediction acc2 = new Prediction(newPrediction, prob);
      if (sndWord.word.equals("!end!")) {
        sentences.add(acc2);
      }
      count++;
      cumulativeProb += sndWord.prob.weight();
      sentences.addAll(buildSentence(branchFactor, threshold, acc2));
    }

    return sentences;
  }

  Comparator<Prediction> compareSentence = new Comparator<Prediction>() {
    @Override
    public int compare(Prediction p1, Prediction p2) {
      return p1.compareTo(p2);
    }
  };

  private boolean predictable(String word) {
    return firstWords.containsKey(word);
  }

  public NLPacket generatePrediction(NLPacket input) {
    log.info("generate Prediction");
    List<String> words = input.getUtterance().words;
    String lastWord = words.get(words.size() - 1);
    String phrase = "";
    for (String w : words) {
      phrase += (w + ' ');
    }
    Prediction acc = new Prediction(phrase, 1.0);

    // return input if last word has no prediction, or if it is most likely the end of the utterance
    if (!firstWords.containsKey(lastWord)) {
      return input;
    } else if (firstWords.get(lastWord).secondWords.peek().word == "!end!")  {
      return input;
    }

    ArrayList<Prediction> predictions = buildSentence(3, 0.75, acc);
    for (Prediction p : predictions) {
      p.sentence = p.sentence.replace("!end!", "");
      log.info("prediction: " + p.sentence);
    }
    Collections.sort(predictions, compareSentence);
    long length = syllableCounter.sentenceSyllableCounter(words);
    log.info("best: " + predictions.get(0).sentence);
    PredictionUtterance predUtt = new PredictionUtterance("none", length, predictions, input);

    // Utterance utt = new Utterance(input.getUtterance().getSpeaker(),
    //         input.getUtterance().getListeners().get(0),
    //         Arrays.asList(predictions.get(0).sentence.split(" ")),
    //         UtteranceType.UNKNOWN);
    NLPacket output = new NLPacket(predUtt);
    return output;
  }

}