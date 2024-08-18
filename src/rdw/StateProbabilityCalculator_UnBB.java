package rdw;

import unbbayes.io.BaseIO;
import unbbayes.io.NetIO;
import unbbayes.prs.Node;
import unbbayes.prs.bn.JunctionTreeAlgorithm;
import unbbayes.prs.bn.PotentialTable;
import unbbayes.prs.bn.ProbabilisticNetwork;



import java.io.File;
import java.util.*;
import java.util.logging.Logger;

import org.json.simple.*;
import org.json.simple.parser.*;
import unbbayes.prs.bn.ProbabilisticNode;
import unbbayes.util.extension.bn.inference.IInferenceAlgorithm;


import java.util.Map;

public class StateProbabilityCalculator_UnBB implements StateProbabilityCalculator
{
    private ProbabilisticNetwork net = null;
    private IInferenceAlgorithm algorithm = null;
    private List<Node> nodeList = null;
    private int netSize = 0;
    //private SortedMap<String, UnBBWrapper.NodeInfo> name2InfoPrior = new TreeMap<>();
    private SortedMap<String, Integer> name2index = new TreeMap<>();
    private Map<Integer, float[]> nidx2cptValues = new TreeMap<>();
    int SHOW_MODE=3;
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
                        ss+="\t";
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



    @Override
    public void setEvidences(ArrayList<HardEvidence> hardEvidences,ArrayList<SoftEvidence> softEvidences)
    {

    }

    @Override
    public Map<String, NodeInfo> getInfo()
    {
        Map<String, NodeInfo> name2Info=new TreeMap<>();
        nodeList = net.getNodes();
        int nidx = 0;
        for (Node node : nodeList)
        {
            String name=getName(nidx);
            NodeInfo_UnBB nodeInfo = new NodeInfo_UnBB(node,SHOW_MODE);
            name2Info.put(name, nodeInfo);
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
}
