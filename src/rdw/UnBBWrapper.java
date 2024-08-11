package rdw;

import unbbayes.io.BaseIO;
import unbbayes.io.NetIO;
import unbbayes.prs.INode;
import unbbayes.prs.Node;
import unbbayes.prs.bn.JunctionTreeAlgorithm;
import unbbayes.prs.bn.PotentialTable;
import unbbayes.prs.bn.ProbabilisticNetwork;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.util.extension.bn.inference.IInferenceAlgorithm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.logging.Logger;

class UnBBWrapper extends Loggable
{
    public static final String STATE_TRUE = "T";
    public static final double DEFAULT_ABS_MIN_PROB = 0.1;

    public static final int INDEX_ABS_MAX_ERROR = 0;
    public static final int INDEX_RELATIVE_ABS_MAX_ERROR = 1;
    public static final int INDEX_ABS_AVG_ERROR = 2;
    public static final int INDEX_RELATIVE_AVG_ERROR = 3;
    public static final int INDEX_ABS_AVG_WEIGHTED_ERROR = 4;
    public static final int INDEX_RELATIVE_AVG_WEIGHTED_ERROR = 5;
    public static final int INDEX_COUNT = 6;
    public static final int INDEX_WEIGHT = 7;
    public static final String[] EstimationNames = {"ABS_MAX", "RELATIVE_ABS_MAX", "ABS_AVG", "RELATIVE_AVG", "ABS_AVG_WEIGHTED", "_RELATIVE_AVG_WEIGHTED", "COUNT", "WEIGHT"};

    ProbabilisticNetwork net = null;
    IInferenceAlgorithm algorithm = null;
    List<Node> nodeList = null;
    int netSize = 0;
    SortedMap<String, String> name2InfoPrior = new TreeMap<>();
    SortedMap<String, Integer> name2index = new TreeMap<>();
    Map<Integer, float[]> nidx2cptValues = new TreeMap<>();

    //=================
    static class NodeInfo
    {
        public static int PROB_GRANULATION = 10000;
        public static int SHOW_MODE = 3;
        public final static int SHOW_NAME = 1;
        public final static int SHOW_DESCRIPTION = SHOW_NAME<<1;
        public final static int SHOW_EXPLANATION = SHOW_DESCRIPTION<<1;


        @Override
        public String toString()
        {
            String ss ="";
            for (int ii=0; ii<3;ii++)
            {
                int flag=1<<ii;
                if((flag&SHOW_MODE)!=0)
                {
                    if (ss.length()>0)
                    {
                        ss+="\t";
                    }
                    ss+=names[ii];
                }
            }
            ss+="=";
            int staeNum=0;
            for (String state:state2probability.keySet())
            {
                String state_info = String.format("%s:%.4f", state, state2probability.get(state));
                if(staeNum>0)
                    ss+=";";
                ss += state_info;
                staeNum++;
            }
            return ss;
        }


        String[] names=new String[3];
        //String description;
        SortedMap<String,Double> state2probability=new TreeMap<>();
        //String description1;

        NodeInfo(Node node)
        {
            if((SHOW_NAME&SHOW_MODE)!=0)
                names[0]=node.getName();
            if((SHOW_DESCRIPTION&SHOW_MODE)!=0)
                names[1]=node.getDescription();
            if((SHOW_EXPLANATION&SHOW_MODE)!=0)
                names[2] = node.getExplanationDescription();
            for (int i = 0; i < node.getStatesSize(); i++)
            {
                double prob=((ProbabilisticNode) node).getMarginalAt(i);
                prob = Math.round(PROB_GRANULATION *prob)/(0.0+ PROB_GRANULATION);
                state2probability.put(node.getStateAt(i),prob);
            }
        }
        public double getProbability(String state)
        {
            return state2probability.get(state);
        }
    }

    //==================
    UnBBWrapper(String modelFilePath)
    {

        try
        {
            BaseIO io = new NetIO(); // open a .net file
            net = (ProbabilisticNetwork) io.load(new File(modelFilePath));
        } catch (Exception e)
        {
            Util.criticalError("Error loading Bayesian Network:" + e);
        }


        // prepare the algorithm to compile network
        algorithm = new JunctionTreeAlgorithm();
        algorithm.setNetwork(net);
        algorithm.run();

        // print node's prior marginal probabilities
        nodeList = net.getNodes();
        int nidx = 0;
        for (Node node : nodeList)
        {
            NodeInfo nodeInfo = new NodeInfo(node);
            String ss = nodeInfo.toString();
            String[] tokens = ss.split("=");
            //TODO: find a concistent solution : tokens[0]; node.getName() is a fragment of tokens[0]
            name2InfoPrior.put(tokens[0], tokens[1]);
            name2index.put(node.getName(), nidx);
            float[] cptvalues = getCptValues(node);
            nidx2cptValues.put(nidx, cptvalues);
            nidx++;


        }
        netSize = nodeList.size();


    }

    public static int getStateIndex(Node node, String state)
    {
        for (int indexState = 0; indexState < node.getStatesSize(); indexState++)
        {
            if (state.equals(node.getStateAt(indexState)))
            {

                return indexState;
            }
        }
        return -1;
    }

    /**
     * Also used:  SoTaP- Set of Target ProbabilitiesSet of Target Probabilities=DifferenceMap
     * Typically the method is applied after setting evidence and propagate probabilities. So, we have 2 sets of probabilities "prior" amd "post". The method detects and reports differences
     *
     * @param netWrapper
     * @param detection_Th_min_abs  - detection criteria absolute
     * @param detection_Th_min_rel  - detection criteria relative
     * @param indexSetExclude       - sets excluding from analysis ( we do not interesting in report on differences that we specially have set, for instance)
     * @param probTrueMapPostPrior- 2 probabilities for each entry
     *                              Promille = int difRatioPromille = (int) Math.round(1000 * (trueProbabilityPost / Math.max(trueProbabilityPrior, DEFAULT_ABS_MIN_PROB) - 1));
     * @return diffMapInfoPost
     */
    public static Map<Integer, NodeInfo> getDifferences(UnBBWrapper netWrapper, Double detection_Th_min_abs, Double detection_Th_min_rel,
                                                                          /*out*/Set<Integer> indexSetExclude, Map<Integer, double[]> probTrueMapPostPrior)
    {
        if (detection_Th_min_abs == null || detection_Th_min_abs < DEFAULT_ABS_MIN_PROB)
        {
            detection_Th_min_abs = DEFAULT_ABS_MIN_PROB;
        }
        Map<Integer, NodeInfo> diffMapInfoPost = new TreeMap<>();
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            NodeInfo nodeInfo = new NodeInfo(node);
            Double trueProbabilityPost = nodeInfo.state2probability.get("T");
            String[] tokens = nodeInfo.toString().split("=");
            String names = tokens[0];
            double trueProbabilityPrior = getTrueProbabilityPrior(netWrapper, names);
            if (trueProbabilityPost < detection_Th_min_abs ||
                    detection_Th_min_rel != null && trueProbabilityPost < detection_Th_min_rel * trueProbabilityPrior)
                continue;

            diffMapInfoPost.put(indexNode, nodeInfo);
            if (indexSetExclude == null || !indexSetExclude.contains(indexNode))
            {
                if (probTrueMapPostPrior != null)
                {
                    double[] probPostPrior = {trueProbabilityPost, trueProbabilityPrior};
                    probTrueMapPostPrior.put(indexNode, probPostPrior);
                }
            }
        }
        return diffMapInfoPost;
    }

    static double getTrueProbabilityPrior(UnBBWrapper netWrapper, String names)
    {
        String prioryStr = netWrapper.name2InfoPrior.get(names);
        String[] tt = prioryStr.split(":");
        return Double.parseDouble(tt[tt.length - 1]);
    }

    public static void setTrueEvidences(UnBBWrapper netWrapper, Set<Integer> nodeIndexes)
    {
        log_algo.fine("setTrueEvidences: " + nodeIndexes);
        netWrapper.net.resetEvidences();
        netWrapper.setTrueEvidences(nodeIndexes, log_algo);
        // propagate evidence
        try
        {
            netWrapper.algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
        }

    }

    static void replaceNames(String modelFilePath, Map<String, String> original2suggested, String outFile) throws FileNotFoundException
    {
        UnBBWrapper unBBWrapper = new UnBBWrapper(modelFilePath);
        for (String nameOriginal : original2suggested.keySet())
        {
            String nameSugested = original2suggested.get(nameOriginal);
            ProbabilisticNode thisNode = (ProbabilisticNode) unBBWrapper.net.getNode(nameOriginal);
            thisNode.setName(nameSugested);
        }
        new NetIO().save(new File(outFile), unBBWrapper.net);

    }

    private int findFirstUnchecked( Set<Integer> checked)
    {
        for (int i = 0; i < nodeList.size(); i++)
        {
            if (!checked.contains(i)) return i;

        }
        throw new RuntimeException("All checked");
    }

    public  TreeMap<Integer, ConnectivityComponent> defineConnectivityComponents(Logger log_algo)
    {
        Set<Integer> checked = new HashSet<>();
        TreeMap<Integer, ConnectivityComponent> componentId2component = new TreeMap<>();
        while (checked.size() < nodeList.size())
        {
            int nidx = findFirstUnchecked(checked);
            ConnectivityComponent connectivityComponent = new ConnectivityComponent(nidx, checked, log_algo);
            int componentId = componentId2component.size();
            componentId2component.put(componentId, connectivityComponent);
        }

        String msg;
        int size = componentId2component.size();
        if (size == 1)
        {

            ConnectivityComponent connectivityComponent = componentId2component.firstEntry().getValue();
            TreeMap<Integer, TreeSet<String>> type2names = connectivityComponent.createType2Names();
            System.out.println(type2names);
            msg = String.format("the ID graph is connected; all points: %d, start points: %d, end points: %d, intermidiate points: %d", nodeList.size(), type2names.get(1).size(), type2names.get(2).size(), type2names.get(3).size());
            if (log_algo != null)
            {
                log_algo.info(msg);
            }

        } else
        {
            msg = String.format("!!!!!the ID graph is not connected!!!!; %d components", size);
            if (log_algo != null)
            {
                log_algo.warning(msg);
            }
            for (Integer id : componentId2component.keySet())
            {
                String msg_i = String.format("connectivity component #%d (%d): %s", id, componentId2component.get(id).nodeName2type.size(), componentId2component.get(id));
                if (log_algo != null)
                {
                    log_algo.warning(msg_i);
                }

                msg += "\n" + msg_i;

            }

        }
        System.out.println(msg);
       /* for(Integer id: componentId2component.keySet())
        {
            String msg_i = String.format("connectivity component #%d (%d): %s", id,componentId2component.get(id).size(), componentId2component.get(id));
            System.out.println(msg_i);

        }*/
       return componentId2component;
    }



    /**
     * cId connectivity id
     * spId start point id
     * bId branch id

     * @param componentId2component - connectivity components
     * @param outFilePathBranches
     * @throws FileNotFoundException
     */
    public static void printBranches(TreeMap<Integer, ConnectivityComponent> componentId2component, String outFilePathBranches) throws FileNotFoundException
    {
        PrintWriter printWriterEffect = new PrintWriter(new FileOutputStream(new File(outFilePathBranches), false));
        String hh=String.format("%s\t%s\t%s\t%s","cId","spId", "bId","branch");
        printWriterEffect.println(hh);
        for(int cId:componentId2component.keySet())
        {

            ConnectivityComponent connectivityComponent=componentId2component.get(cId);
            connectivityComponent.printBranches(printWriterEffect,cId);

        }
        printWriterEffect.flush();
        printWriterEffect.close();

    }


    //TODO: define public class CorrectorCP
    public StringBuffer showCorrection(Map<String, Util.WAvgStd> cptId2stat, Map<String, Integer> keyCoord2errcount, double significantWeight)
    {
        StringBuffer stringBuffer = new StringBuffer();
        String header = String.format("%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s", "node", "condition", "current", "new", "confidence", "cptId", Util.WAvgStd.HEADERS, "err");
        stringBuffer.append(header + "\n");
        System.out.println(header);
        for (String cptId : cptId2stat.keySet())
        {
            int errCount = 0;
            if (keyCoord2errcount.containsKey(cptId))
            {
                errCount = keyCoord2errcount.get(cptId);
            }
            String[] tt = cptId.split("_");
            int nidx = Integer.parseInt(tt[0]);
            int linCoord = Integer.parseInt(tt[1]);
            Node node = nodeList.get(nidx);
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            PotentialTable cpt = probabilisticNode.getProbabilityFunction();
            int[] multCoord = cpt.getMultidimensionalCoord(linCoord);
            INode var_i = cpt.getVariableAt(0);
            String condition = var_i.getName() + "=" + var_i.getStateAt(multCoord[0]);
            for (int ii = 1; ii < cpt.getVariablesSize(); ii++)
            {
                var_i = cpt.getVariableAt(ii);
                condition += "," + var_i.getName() + "=" + var_i.getStateAt(multCoord[ii]);
            }
            float current = nidx2cptValues.get(nidx)[linCoord];
            Util.WAvgStd wAvgStd = cptId2stat.get(cptId);
            double dd = Math.sqrt(0.5 * Math.max(0.01, current));
            double r = 1 - 2 * wAvgStd.Std();
            if (r < Util.Epsilon7) continue;
            double weight = wAvgStd.W / significantWeight;
            if (weight < Util.Epsilon7) continue;
            if (weight > 1)
            {
                weight = 1;
            }

            double confidence = weight * r;
            double new_ = confidence * wAvgStd.Avg() + (1 - confidence) * current;
            String ss = String.format("%s\t%s\t%.5f\t%.5f\t%.5f\t%s\t%s\t%d", node.getName(), condition, current, new_, confidence, cptId, wAvgStd, errCount);
            stringBuffer.append(ss + "\n");
            System.out.println(ss);
        }
        return stringBuffer;
    }

    public Map<Integer, Map<Integer, float[]>> defineCPTCorrection(Set<HistoryObject> objects, Map<String, Float> targets, float threshold, Set<String> keyCoorderr)
    {

        Set<Integer> nodeIndexes = ho2trueIndexes(objects);

        Map<Integer, float[]> nidx2likelihood = new TreeMap<>();
        for (String targetNode : targets.keySet())
        {
            float[] likelihood = new float[2];
            float targetValue = targets.get(targetNode);
            if (targetValue > 0)
            {
                likelihood[0] = 1;
                likelihood[1] = 1 - targetValue;

            } else
            {
                likelihood[0] = 1 + targetValue;
                likelihood[1] = 1;
            }

            int nidx = name2index.get(targetNode);
            nidx2likelihood.put(nidx, likelihood);
        }
        Map<Integer, Map<Integer, float[]>> nidx2cptChanges = new TreeMap<>();
        for (int nidx = 0; nidx < netSize; nidx++)
        {


            Node node = nodeList.get(nidx);
            //node.
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            PotentialTable cpt = probabilisticNode.getProbabilityFunction();
            float[] values = nidx2cptValues.get(nidx);
            int table_length = values.length;
            for (int linCoord = 0; linCoord < table_length; linCoord++)
            {
                net.resetEvidences();
                net.resetLikelihoods();
                setTrueEvidences(nodeIndexes, null);
                setSoftEvidences(nidx2likelihood);
                int[] multidimensionalCoord = cpt.getMultidimensionalCoord(linCoord);
                float v0 = values[linCoord];//cpt.getValue(linCoord);
                try
                {
                    //TODO: check if double propgate works!?
                    algorithm.propagate();

                    float columnProbabbility = defineCPTcolumn(multidimensionalCoord, 1, cpt);
                    if (columnProbabbility < Util.Epsilon3)
                        continue;
                    algorithm.propagate();
                    //INode node_i = cpt.getVariableAt(0);
                    float prob = ((ProbabilisticNode) node).getMarginalAt(multidimensionalCoord[0]);
                    float[] _probV_probColumn = {prob, columnProbabbility};
                    float d2 = v0 - _probV_probColumn[0];
                    if (Math.abs(d2) > threshold)
                    {
                        if (!nidx2cptChanges.containsKey(nidx))
                        {
                            nidx2cptChanges.put(nidx, new TreeMap<>());
                        }
                        nidx2cptChanges.get(nidx).put(linCoord, _probV_probColumn);
                    }
                } catch (Exception exc)
                {
                    //Util.criticalError
                    // System.out.println("algorithm.propagate:"+exc.getMessage());
                    if (keyCoorderr != null)
                    {
                        String keyCoord = String.format("%d_%d", nidx, linCoord);
                        keyCoorderr.add(keyCoord);
                    }
                    //return null;
                }

            }
        }
        return nidx2cptChanges;
    }

    private Set<Integer> ho2trueIndexes(Set<HistoryObject> objects)
    {
        Set<Integer> nodeIndexes = new TreeSet<>();
        for (HistoryObject historyObject : objects)
        {
            if ((historyObject.state == STATE_TRUE) && (historyObject.relative_time == RelativeTime.last))
            {
                int nidx = name2index.get(historyObject.evidence);
                nodeIndexes.add(nidx);
            } else
            {
                throw new RuntimeException("not implemented HO: " + historyObject);
            }
        }
        return nodeIndexes;
    }

    private void setSoftEvidences(Map<Integer, float[]> nidx2likelihood)
    {
        for (int nidx : nidx2likelihood.keySet())
        {
            float[] likelihood = nidx2likelihood.get(nidx);
            /*
            TODO: finde right solution
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) nodeList.get(nidx);
            probabilisticNode.addLikeliHood(likelihood);

             */
            //TODO: this is tmp
            int indexState = 0;
            if (likelihood[0] < likelihood[1])
            {
                indexState = 1;
            }

            ProbabilisticNode probabilisticNode = (ProbabilisticNode) nodeList.get(nidx);
            ;
            probabilisticNode.addFinding(indexState);
        }
    }

    public void setTrueEvidences(Set<Integer> nodeIndexes, Logger log_algo)
    {
        for (int indexNode : nodeIndexes)
        {
            NodeInfo nodeInfo = new NodeInfo(nodeList.get(indexNode));
            Node node = nodeList.get(indexNode);
            String nodeName = node.getName();
            String state = STATE_TRUE;
            int indexState = getStateIndex(node, state);
            if (indexState < 0)
            {
                throw new RuntimeException(String.format("unknown staete = %s for node = %s", state, nodeName));
            }

            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            probabilisticNode.addFinding(indexState);
            if (log_algo != null) log_algo.fine(String.format("%d\t%s", indexNode, nodeInfo));
        }

    }

    TreeSet<Integer> indexSet(Set<HistoryObject> historyObjects)
    {
        TreeSet<Integer> indexSet = new TreeSet<>();
        for (HistoryObject ho : historyObjects)
        {
            int nidx = name2index.get(ho.evidence);
            indexSet.add(nidx);
        }

        return indexSet;
    }

    public double[] estimate(Map<String, Set<HistoryObject>> id2objects, Map<String, Map<String, float[]>> id2bounds, SortedMap<Float, Map<String, String>> defect2tasks, int count,
                             PrintWriter printWriter, String mark)
    {
        double[] errors_counts__ = new double[8];
        int cc = 0;
        for (Float defect : defect2tasks.keySet())
        {
            Map<String, String> tasks = defect2tasks.get(defect);
            {
                for (String case_ : tasks.keySet())
                {
                    String target = tasks.get(case_);
                    if (cc >= count)
                    {
                        //break;
                    }
                    double confidence = 1 - defect;
                    Set<HistoryObject> historyObjects = id2objects.get(case_);
                    Map<String, float[]> boundsMap = id2bounds.get(case_);
                    double[] errors_count_ = estimate(case_, historyObjects, boundsMap, confidence, printWriter, mark);
                    for (int ii = 0; ii < 2; ii++)
                    {
                        if (errors_counts__[ii] < errors_count_[ii])
                        {
                            errors_counts__[ii] = errors_count_[ii];
                        }
                    }
                    for (int ii = 2; ii < 8; ii++)
                    {
                        errors_counts__[ii] += errors_count_[ii];
                    }


                }
            }
        }
        double[] errors_counts = normalize(errors_counts__);
        if (printWriter != null)
        {
            String ss = String.format("%s\t%s", "tasks", mark);
            for (int ii = 0; ii < errors_counts.length; ii++)
            {
                ss += String.format("\t%.5f", errors_counts[ii]);

            }
            printWriter.println(ss);
        }
        return errors_counts;
    }

    private static double[] normalize(double[] errors_counts__)
    {
        double[] errors_counts = new double[8];
        System.arraycopy(errors_counts__, 0, errors_counts, 0, errors_counts__.length);
        for (int ii = 2; ii < 4; ii++)
        {
            errors_counts[ii] /= errors_counts[INDEX_COUNT];
        }
        for (int ii = 4; ii < 6; ii++)
        {
            errors_counts[ii] /= errors_counts[INDEX_WEIGHT];
        }
        return errors_counts;
    }

    private double[] estimate(String idSoTaP, Set<HistoryObject> historyObjects, Map<String, float[]> boundsMap, double confidence, PrintWriter printWriter, String mark)
    {
        double[] errors_count = new double[8];
        Set<Integer> nodeIndexes = ho2trueIndexes(historyObjects);
        net.resetEvidences();
        net.resetLikelihoods();
        setTrueEvidences(nodeIndexes, null);
        // propagate evidence
        try
        {
            algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
        }
        double weight = 0;
        for (String nodeName : boundsMap.keySet())
        {
            float[] bounds = boundsMap.get(nodeName);
            int nidx = name2index.get(nodeName);
            Node node = nodeList.get(nidx);
            NodeInfo nodeInfo = new NodeInfo(node);
            Double trueProbabilityPost = nodeInfo.state2probability.get("T");
            double err = bounds[1] - trueProbabilityPost;
            if (bounds[1] > bounds[0] && err < 0)
            {
                err = 0;
            }
            if (bounds[1] < bounds[0] && err > 0)
            {
                err = 0;
            }
            double absErr = Math.abs(err);
            double relativeAbsError = absErr / Math.max(Math.abs(bounds[1] - bounds[0]), 0.025);
            if (errors_count[INDEX_ABS_MAX_ERROR] < absErr)
            {
                errors_count[INDEX_ABS_MAX_ERROR] = absErr;
            }
            if (errors_count[INDEX_RELATIVE_ABS_MAX_ERROR] < relativeAbsError)
            {
                errors_count[INDEX_RELATIVE_ABS_MAX_ERROR] = relativeAbsError;
            }
            errors_count[INDEX_ABS_AVG_ERROR] += absErr;
            errors_count[INDEX_RELATIVE_AVG_ERROR] += relativeAbsError;
            errors_count[INDEX_ABS_AVG_WEIGHTED_ERROR] += absErr * confidence;
            errors_count[INDEX_RELATIVE_AVG_WEIGHTED_ERROR] += relativeAbsError * confidence;
            weight += confidence;
        }
       /* errors[INDEX_ABS_AVG_ERROR]/=boundsMap.size();
        errors[INDEX_RELATIVE_AVG_ERROR]/=boundsMap.size();*/
        errors_count[INDEX_COUNT] = boundsMap.size();
        errors_count[INDEX_WEIGHT] = weight;
        if (printWriter != null)
        {
            double[] errors_counts_for_print = normalize(errors_count);
            String ss = String.format("%s\t%s", idSoTaP, mark);
            for (int ii = 0; ii < errors_count.length; ii++)
            {
                ss += String.format("\t%.5f", errors_counts_for_print[ii]);

            }
            printWriter.println(ss);
        }
        return errors_count;
    }

    float[] getCptValues(Node node)
    {
        ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
        PotentialTable cpt = probabilisticNode.getProbabilityFunction();
        int variablesSize = cpt.getVariablesSize();
        int table_length = cpt.getVariableAt(0).getStatesSize();
        for (int vidx = 1; vidx < variablesSize; vidx++)
        {
            //   String vName = cpt.getVariableAt(vidx).getName();
            table_length *= cpt.getVariableAt(vidx).getStatesSize();
        }
        float[] res = new float[table_length];

        for (int linearCoord = 0; linearCoord < table_length; linearCoord++)
        {
//            int[] multidimensionalCoord = cpt.getMultidimensionalCoord(linearCoord);
            float v0 = cpt.getValue(linearCoord);
            res[linearCoord] = v0;
        }

        return res;
    }

    //TODO:  Define Unit test?
    public void testCRT(int nidx)
    {
        Node node = nodeList.get(nidx);
        ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
        PotentialTable cpt = probabilisticNode.getProbabilityFunction();
        System.out.println("node: " + node.getName());

        float[] values = nidx2cptValues.get(nidx);
        StringBuffer stringBuffer = new StringBuffer();
        String header = cpt.getVariableAt(0).getName();
        int variablesSize = cpt.getVariablesSize();
        int table_length = cpt.getVariableAt(0).getStatesSize();
        for (int vidx = 1; vidx < variablesSize; vidx++)
        {
            String vName = cpt.getVariableAt(vidx).getName();
            header += "," + vName;
            table_length *= cpt.getVariableAt(vidx).getStatesSize();
        }
        if (values.length != table_length)
        {
            throw new RuntimeException("values.length!=table_length");
        }
        stringBuffer.append(String.format("%s,%s,%s\n", "lidx", header, "value"));
        for (int linearCoord = 0; linearCoord < table_length; linearCoord++)
        {
            int[] multidimensionalCoord = cpt.getMultidimensionalCoord(linearCoord);
            float v0 = values[linearCoord];//cpt.getValue(linearCoord);
            float v1 = cpt.getValue(multidimensionalCoord);
            float d = v0 - v1;
            if (Math.abs(d) > 0.00001)
            {
                throw new RuntimeException("Math.abs(v0-v1)>0.00001: " + d);
            }
            float v2 = calculateCase(multidimensionalCoord, cpt);
            float d2 = v0 - v2;
            if (Math.abs(d2) > 0.00001)
            {
                System.out.println(linearCoord);
                System.out.println(v0);
                System.out.println(v2);
                System.out.println(d2);
                throw new RuntimeException("Math.abs(v0-v2)>0.00001: " + d2);
            }
            String inputStr = cpt.getVariableAt(0).getStateAt(multidimensionalCoord[0]);
            for (int vidx = 1; vidx < variablesSize; vidx++)
            {
                String vName = cpt.getVariableAt(vidx).getStateAt(multidimensionalCoord[vidx]);
                inputStr += "," + vName;
            }
            stringBuffer.append(String.format("%d,%s,%.5f\n", linearCoord, inputStr, v0));
            System.out.println(stringBuffer);
        }
    }

    float calculateCase(int[] multidimensionalCoord, PotentialTable cpt)
    {
        float[] res = calculateCRTcolumn(multidimensionalCoord, 1, cpt);
        return res[multidimensionalCoord[0]];
    }

    float[] calculateCRTcolumn(int[] columnDef, int columnDefOffset, PotentialTable cpt)
    {
        net.resetEvidences();
        defineCPTcolumn(columnDef, columnDefOffset, cpt);
        try
        {
            algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
            return null;
        }
        //Node node = nodeList.get(columnDef[0]);
        INode node = cpt.getVariableAt(0);
        int statesSize = node.getStatesSize();
        float[] res = new float[statesSize];
        for (int sidx = 0; sidx < statesSize; sidx++)
        {
            float prob = ((ProbabilisticNode) node).getMarginalAt(sidx);
            res[sidx] = prob;
        }
        return res;
    }

    private float defineCPTcolumn(int[] columnDef, int columnDefOffset, PotentialTable cpt)
    {
        float columnProbability = jointProbability(columnDef, columnDefOffset, cpt);

        for (int vidx = columnDefOffset; vidx < cpt.getVariablesSize(); vidx++)
        {
            INode node = cpt.getVariableAt(vidx);
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            probabilisticNode.addFinding(columnDef[vidx]);


        }
        return columnProbability;
    }

    private float jointProbability(int[] columnDef, int columnDefOffset, PotentialTable cpt)
    {
        //TODO: this is TMP. should be implemented by BBN rules! (joiny statistics as sequence of conditional)
        float columnProbability = 1;
        for (int ii = columnDefOffset; ii < columnDef.length; ii++)
        {
            INode node = cpt.getVariableAt(ii);
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            float probability = probabilisticNode.getMarginalAt(columnDef[ii]);
            if (columnProbability > probability)
            {

                columnProbability = probability;
            }
        }
        return columnProbability;
    }

    public enum RelativeTime
    {
        last,
        recently,
        longago
    }

    public static class HistoryObject
    {
        RelativeTime relative_time = RelativeTime.last;
        String evidence = null;
        String state = "T";

        public HistoryObject(String[] tt, int offset)
        {
            if (tt.length < offset + 3)
            {
                throw new RuntimeException("not sufficient fragments");
            }

            /* TODO: for next version
            String relativeTime=tt[offset];
            int nidx = Integer.parseInt(tt[offset+1]);

             */
            evidence = tt[offset + 2];

        }
        //TODO: optional, just if known????
        //Integer idx=null;
    }

    public class ConnectivityComponent
    {
        public static final int TYPE_FLAG_HAVE_CHILDREN = 1;
        public static final int TYPE_FLAG_HAVE_PARENTS = 2;
        public static final int START_POINT = 1;
        public TreeMap<String, Integer> nodeName2type = new TreeMap<>();

        /**
         * @param nidx     - one of the nodes
         * @param checked  - nodes that alredy classified to different components
         * @param log_algo
         */
        public ConnectivityComponent(int nidx, Set<Integer> checked, Logger log_algo)
        {
            ArrayList<Integer> idList = new ArrayList<>();
            idList.add(nidx);
            nodeName2type.put(nodeList.get(nidx).getName(), 0);
            //to enumerate all id set and 'extend them"
            int extendendCount = 0;
            while (extendendCount < idList.size())
            {
                int currentId = idList.get(extendendCount);
                extendConnectivityData(currentId, idList);
                extendendCount++;
                checked.add(currentId);
            }
            String msg = String.format("idSet for nidx=#%d: %s", nidx, nodeName2type);
            if (log_algo != null)
                log_algo.info(msg);
            else
                System.out.println(msg);
        }

        private void extendConnectivityData(int currentId, ArrayList<Integer> idList)
        {
            Node node = nodeList.get(currentId);
            List<INode> parents = node.getParentNodes();
            for (INode parent : parents)
            {
                String name = parent.getName();
                int nidx = name2index.get(name);
                if (!nodeName2type.containsKey(name))
                {
                    nodeName2type.put(name, 0);
                    idList.add(nidx);
                }

            }
            ArrayList<Node> children = node.getChildren();
            for (INode chaild : children)
            {
                String name = chaild.getName();
                int nidx = name2index.get(name);
                if (!nodeName2type.containsKey(name))
                {
                    nodeName2type.put(name, 0);
                    idList.add(nidx);
                }
            }
            String nodeName = node.getName();
            int type = nodeName2type.get(nodeName);
            type |= children.size() == 0 ? 0 : TYPE_FLAG_HAVE_CHILDREN;
            type |= parents.size() == 0 ? 0 : TYPE_FLAG_HAVE_PARENTS;
            nodeName2type.put(nodeName, type);

        }

        @Override
        public String toString()
        {
            String res = "";
            for (String nName : nodeName2type.keySet())
            {
                if (res.length() > 0)
                {
                    res += ",";
                }
                res += String.format("%s-%d", nName, nodeName2type.get(nName));

            }
            return res;
        }
        private  TreeMap<Integer, TreeSet<String>> createType2Names()
        {
            TreeMap<Integer, TreeSet<String>> type2names= new TreeMap<>();
            for (String nName : nodeName2type.keySet())
            {
                int type = nodeName2type.get(nName);
                if (!type2names.containsKey(type))
                {
                    type2names.put(type, new TreeSet());
                }
                type2names.get(type).add(nName);

            }
            return type2names;
        }
        public void printBranches(PrintWriter printWriterEffect,int cId)
        {
            //print branches for connectivity component TODO:move to class ConnectivityComponent
            //chech if ConnectivityComponent has only one point
            int spId=-1;
            TreeMap<Integer, TreeSet<String>> type2names = createType2Names();
            TreeSet<String> startPoints=type2names.get(ConnectivityComponent.START_POINT);
            for(String sPointName:startPoints)
            {
                spId++;
                int nidx=name2index.get(sPointName);
                BranchSet branchSet=new BranchSet(nidx);

                for(int bid: branchSet.id2breanch.keySet())
                {
                    String branchString="";
                    ArrayList<String> breanch=branchSet.id2breanch.get(bid);
                    for(String nName:breanch)
                    {
                        if(branchString.length()>0)
                            branchString+="-";
                        branchString+=nName;
                    }
                    String ss=String.format("%d\t%d\t%d\t%s",cId,spId, bid,branchString);
                    printWriterEffect.println(ss);

                }
            }
        }
    }
    public class BranchSet
    {
        TreeMap<Integer,ArrayList<String>> id2breanch= new TreeMap<>();
        BranchSet(int nidx)
        {
            int currentBreanch=0;
            ArrayList<String> branch=new ArrayList<>();
            branch.add(nodeList.get(nidx).getName());
            id2breanch.put(0,branch);
            while(currentBreanch<id2breanch.size())
            {
                extendBreanch(currentBreanch);
                currentBreanch++;
            }
        }

        private void extendBreanch(int currentBreanch)
        {
            ArrayList<String> branch=id2breanch.get(currentBreanch);
            while(true)
            {
                String lastNodeName = branch.get(branch.size() - 1);
                int nidxLast = name2index.get(lastNodeName);
                Node node = nodeList.get(nidxLast);
                ArrayList<Node> children = node.getChildren();
                if(children.size()==0)
                    break;
                int childCC = -1;
                for (INode chaild : children)
                {
                    childCC++;
                    String name = chaild.getName();
                    //int nidx = name2index.get(name);
                    if (childCC == children.size() - 1)
                    {
                        branch.add(name);
                    } else
                    {
                        ArrayList<String> branchNew = new ArrayList<>(branch);
                        branchNew.add(name);
                        id2breanch.put(id2breanch.size(), branchNew);
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception
    {
        //String _file = args[0];
        String work_directory="C:\\projects\\radware\\UnBBayes\\Nets-avi\\Nets";
        File directory = new File(work_directory);

        // Using listFiles method we get all the files of a directory
        // return type of listFiles is array
        File[] files = directory.listFiles();
        if (files != null)
        {
            for (File file : files)
            {
                if (!file.isFile())
                    continue;
                String _file=file.getAbsolutePath();
                System.out.println("-----"+_file);
                UnBBWrapper unBBWrapper = new UnBBWrapper(_file);
                unBBWrapper.defineConnectivityComponents( null);
            }
        }

    }
}
