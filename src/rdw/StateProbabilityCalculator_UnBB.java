package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import unbbayes.io.BaseIO;
import unbbayes.io.NetIO;
import unbbayes.prs.Node;
import unbbayes.prs.bn.JunctionTreeAlgorithm;
import unbbayes.prs.bn.PotentialTable;
import unbbayes.prs.bn.ProbabilisticNetwork;



import java.io.File;
import java.util.*;
import java.util.logging.Logger;


import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.util.extension.bn.inference.IInferenceAlgorithm;


import java.util.Map;

import static rdw.StateProbabilityCalculator.NodeInfo.*;
import static rdw.Util.parseEvJson;

public class StateProbabilityCalculator_UnBB extends Loggable implements StateProbabilityCalculator
{
    private ProbabilisticNetwork net = null;
    private IInferenceAlgorithm algorithm = null;
    private List<Node> nodeList = null;
    private int netSize = 0;
    private SortedMap<String, Integer> name2index = new TreeMap<>();
    private Map<Integer, float[]> nidx2cptValues = new TreeMap<>();
    //int SHOW_MODE=3;
    public static class NodeInfo_UnBB implements StateProbabilityCalculator.NodeInfo
    {
        public static int PROB_GRANULATION = 10000;

        @Override
        public String toString()
        {
            String ss =getName();
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

        int SHOW_MODE=3;
        String[] names=new String[3];
        //String description;
        SortedMap<String,Double> state2probability=new TreeMap<>();
        public NodeInfo_UnBB(Node node,int show_mode)
        {
            SHOW_MODE=show_mode;
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

        @Override
        public String getName()
        {
            String ss ="";
            for (int ii=0; ii<3;ii++)
            {
                int flag=1<<ii;
                if((flag&SHOW_MODE)!=0)
                {
                    if (ss.length()>0)
                    {
                        ss+="//";
                    }
                    ss+=names[ii];
                }
            }
            return ss;
        }

        @Override
        public Map<String, Double> getStatesProbabilities()
        {
            return state2probability;
        }

        @Override
        public Double getProbability(String state)
        {
            if (state2probability.containsKey(state))
            {
                return state2probability.get(state);
            }
            return null;
        }
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

    @Override
    public ArrayList<String> setEvidences(ArrayList<HardEvidence> hardEvidences,ArrayList<SoftEvidence> softEvidences)
    {
        System.out.println(hardEvidences);
        System.out.println(softEvidences);
        ArrayList<String> effectiveNodes= new ArrayList<>();
        for (HardEvidence he:hardEvidences)
        {
             String variable=he._variable;
            if (!name2index.containsKey(variable))
            {
                throw new RuntimeException(String.format("unknown variable in : %s", he));
            }
            if(effectiveNodes.contains(variable))
            {
                   throw new RuntimeException(String.format("repeated variable in : %s", he));
            }
            int indexNode=name2index.get(variable);
            //TODO: check state
            String state= he._state;
            Node node = nodeList.get(indexNode);
            //String nodeName = node.getName();
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            String msg = String.format("hard evidence: variable index=%d; '%s' => set state '%s'", indexNode, variable, state);
            System.out.println(msg);
            log_algo.info(msg);

            int indexState= getStateIndex(node,state);
            if( indexState<0)
            {
                throw new RuntimeException(String.format("wrong state = '%s' for node = '%s'",state,variable));
            }
            probabilisticNode.addFinding(indexState);
            log_algo.fine("->state= " + state);
            effectiveNodes.add(variable);
        }
        for (SoftEvidence se:softEvidences)
        {
            String variable=se._variable;
            if (!name2index.containsKey(variable))
            {
                throw new RuntimeException(String.format("unknown variable in : %s", se));
            }
            if(effectiveNodes.contains(variable))
            {
              throw new RuntimeException(String.format("repeated variable in : %s", se));
            }
            int indexNode=name2index.get(variable);
            //TODO: check state
            Map likelihoods = se._likelihoods;
            Node node = nodeList.get(indexNode);
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            String msg = String.format("soft evidence: for variable index=%d; name=%s; likelihoods: %s", indexNode, variable, likelihoods);
            System.out.println(msg);
            log_algo.fine(msg);

            int statesSize = probabilisticNode.getStatesSize();
            float[] likelihood_arr=new float[statesSize];
            Arrays.fill(likelihood_arr, 0.0F);
            for(int ii=0;ii<statesSize; ii++)
            {
                String state_name = probabilisticNode.getStateAt(ii);
                if (likelihoods.containsKey(state_name))
                {
                    double prob = (double) likelihoods.get(state_name);
                    likelihood_arr[ii] = (float) prob;
                }
            }
            probabilisticNode.addLikeliHood(likelihood_arr);
            log_algo.fine("->likelihood_arr= " + likelihood_arr);

            effectiveNodes.add(variable);
        }
        try
        {
            algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
        }

        return effectiveNodes;
    }

    @Override
    public Map<String, NodeInfo> getInfo(int flag)
    {
        Map<String, NodeInfo> name2Info=new TreeMap<>();
        nodeList = net.getNodes();
        int nidx = 0;
        for (Node node : nodeList)
        {
            String name=getName(nidx);
            NodeInfo_UnBB nodeInfo = new NodeInfo_UnBB(node,flag);
            name2Info.put(name, nodeInfo);
            nidx++;
        }
        return name2Info;
    }


    public StateProbabilityCalculator_UnBB(String modelFilePath)
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
            UnBBWrapper.NodeInfo nodeInfo = new UnBBWrapper.NodeInfo(node);

            ///name2InfoPrior.put(getName(nidx), nodeInfo);
            name2index.put(node.getName(), nidx);
            float[] cptvalues = getCptValues(node);
            nidx2cptValues.put(nidx, cptvalues);
            nidx++;
        }
        netSize = nodeList.size();
    }

    //------private
    private String getName(int nidx)
    {
        return nodeList.get(nidx).getName();
    }
    private float[] getCptValues(Node node)
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

    public static void main(String[] args) throws Exception
    {
//        TODO: test 1
//        String work_directory="C:\\projects\\radware\\UnBBayes\\Nets-avi\\Nets";
//        check_models_in_directory(work_directory);

        System.out.println("Test StateProbabilityCalculator_UnBB");
        String cfg_file = args[0];
        init(cfg_file);
        log_algo.setUseParentHandlers(false);
        log_algo.info("Test StateProbabilityCalculator_UnBB");
        String ev_file = args[1];
        log_algo.info(String.format("processing ev_file:%s", ev_file));

        String modelFilePath = Util.getAndTrim("model_file", configuration);
        String work_dir = Util.getAndTrim("work_dir", configuration);
        StateProbabilityCalculator prob_calc=new StateProbabilityCalculator_UnBB(modelFilePath);

        ArrayList<Map> res=parseEvJson(ev_file);
        Map hardEvidences = res.get(0);
        Map softEvidences = res.get(1);
        System.out.println(String.format(">>>hardEvidences:%s", hardEvidences));
        System.out.println(String.format(">>>softEvidences:%s", softEvidences));

        int flag = SHOW_NAME | SHOW_DESCRIPTION | SHOW_EXPLANATION;
        Map<String, NodeInfo> priori_info = prob_calc.getInfo(flag);
        System.out.println("----priori_info--------------------");
        StateProbabilityCalculator.printInfo(priori_info);
        ArrayList<HardEvidence> hardEvidences_arr = getHardEvidencesFromMap(hardEvidences);
        ArrayList<SoftEvidence> softEvidences_arr=getSoftEvidencesFromMap(softEvidences);
        ArrayList<String> effectiveNodes = prob_calc.setEvidences(hardEvidences_arr, softEvidences_arr);
        System.out.println(String.format("effectiveNodes: %s", effectiveNodes));

        Map<String, NodeInfo> posterior_info = prob_calc.getInfo(flag);
        System.out.println("----posterior_info--------------------");
        StateProbabilityCalculator.printInfo(posterior_info);

        for (String names:priori_info.keySet())
        {
            NodeInfo nodeInfo = posterior_info.get(names);
            NodeInfo priorNodeInfo = priori_info.get(names);
            System.out.println("---current---" + nodeInfo);
            System.out.println("---prior-----" + priorNodeInfo);
            double max_abs_difference = 0;
            double min_abs_difference = 0;
            String state_max = null;
            String state_min = null;
            Map<String, Double> state2probability = priorNodeInfo.getStatesProbabilities();
            for (String state : state2probability.keySet())
            {
                double p1 = state2probability.get(state);
                double p0 = nodeInfo.getStatesProbabilities().get(state);
                double delta = p1 - p0;
                if (delta < min_abs_difference)
                {
                    min_abs_difference = delta;
                    state_min = state;
                }
                if (delta > max_abs_difference)
                {
                    max_abs_difference = delta;
                    state_max = state;
                }


            }
            System.out.println(String.format("===diff=== max: %f (%s); min: %f (%s)", max_abs_difference, state_max, min_abs_difference, state_min));
        }

    }



    private static ArrayList<HardEvidence> getHardEvidencesFromMap(Map hardEvidences)
    {
        ArrayList<HardEvidence> hardEvidences_arr=null;
        if (hardEvidences !=null)
        {
            hardEvidences_arr=new ArrayList<>();
            for (Object obj: hardEvidences.keySet())
            {
                String variableName=(String) obj;
                String stataName=(String) hardEvidences.get(variableName);
                HardEvidence he=new HardEvidence(variableName, stataName);
                hardEvidences_arr.add(he);
            }
        }
        return hardEvidences_arr;
    }

    private static ArrayList<SoftEvidence> getSoftEvidencesFromMap(Map softEvidences)
    {
        ArrayList<SoftEvidence> softEvidences_arr = null;
        if (softEvidences != null)
        {
            softEvidences_arr = new ArrayList<>();
            for (Object obj : softEvidences.keySet())
            {
                String variableName = (String) obj;
                Map likelihoods = (Map) softEvidences.get(variableName);
                SoftEvidence se = new SoftEvidence(variableName, likelihoods);
                softEvidences_arr.add(se);
            }

        }
        return softEvidences_arr;
    }
}
