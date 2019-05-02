package com.slug.textPrediction;

import ade.Connection;
import ade.SuperADEComponentImpl;
import com.slug.common.NLPacket;
import java.io.*;


import java.rmi.RemoteException;

import static com.util.Util.checkNextArg;

public class TextPredictionComponentImpl extends SuperADEComponentImpl implements TextPredictionComponent {

    private boolean doneConstructing;
    private Connection nlpConnection;
    private String nlpType;
    private String nlpName;
    private boolean useNLP;
    private TextPredictionModel tpm;

    @Override
    public boolean addUtterance(NLPacket incoming) throws RemoteException {
        NLPacket output = tpm.generatePrediction(incoming);
        nlpConnection.call(0, "addUtterance", Boolean.class, output);
        return true;
    }

    public TextPredictionComponentImpl() throws RemoteException {
        super();
        if (useNLP) {
            log.info("Using NLP");
            if (nlpName != null && !nlpName.isEmpty()) {
                nlpConnection = connectToComponent(nlpType, nlpName);
            } else {
                nlpConnection = connectToComponent(nlpType);
            }
        }
        doneConstructing = true;
        String dialogueDir = "com" + File.separator + "slug" + File.separator + "textPrediction" + File.separator;
        File file = new File(dialogueDir + "bigrams_freq.txt");
        tpm = new TextPredictionModel(file);
    }


    @Override
    protected void init() {
        useNLP = false;
        nlpType = "com.interfaces.NLPComponent";
        nlpName = null;

        doneConstructing = false;
    }

    /**
     * Parses command line arguments specific to this ADEComponent.
     *
     * @param args The custom command line arguments
     * @return <tt>true</tt> if all <tt>args</tt> are recognized,
     * <tt>false</tt> otherwise
     */
    @Override
    protected boolean parseArgs(String[] args) {
        boolean found = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-nlp")) {
                useNLP = true;
                if (!checkNextArg(args, i)) {
                    nlpType = args[++i];
                    if (!checkNextArg(args, i)) {
                        nlpName = args[++i];
                    }
                }
                found = true;
            }
            else {
                return false;  // return false on any unrecognized args
            }
        }
        return found;
    }


    @Override
    protected String additionalUsageInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("     -nlp                  <set nlp component>\n");
        sb.append("     -nlptype [type]       <use specified nlp component>\n");
        return sb.toString();
    }

    @Override
    protected boolean localServicesReady() {
        return doneConstructing && requiredConnectionsPresent();
    }

}
