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
import java.io.FileReader;
import java.util.*;
import java.util.logging.Logger;

import org.json.simple.*;
import org.json.simple.parser.*;

class UnBBWrapper extends Loggable
{
    ProbabilisticNetwork net = null;
    IInferenceAlgorithm algorithm = null;
    List<Node> nodeList = null;
    int netSize = 0;
    SortedMap<String, NodeInfo> name2InfoPrior = new TreeMap<>();
    SortedMap<String, Integer> name2index = new TreeMap<>();
    Map<Integer, float[]> nidx2cptValues = new TreeMap<>();

    public static final double DEFAULT_ABS_MIN_PROB = 0.1;
    public static Map<Integer, NodeInfo> getDifferences(UnBBWrapper netWrapper, Double detection_Th_min_abs, Double detection_Th_min_rel,
            /*out*/Set<Integer> indexSetExclude, Map<Integer, double[]> probTrueMapPostPrior) {
        if (detection_Th_min_abs == null || detection_Th_min_abs < DEFAULT_ABS_MIN_PROB)
        {
            detection_Th_min_abs = DEFAULT_ABS_MIN_PROB;
        }
        Map<Integer, NodeInfo> diffMapInfoPost = new TreeMap<>();
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++) {
            Node node = netWrapper.nodeList.get(indexNode);
            String name = node.getName();

            NodeInfo nodeInfo = new NodeInfo(node);
            NodeInfo priorNodeInfo = netWrapper.name2InfoPrior.get(name);
            if (nodeInfo.state2probability.containsKey("T")) {
                Double trueProbabilityPost = nodeInfo.state2probability.get("T");
                double trueProbabilityPrior = priorNodeInfo.state2probability.get("T");
                String[] tokens = nodeInfo.toString().split("=");
                if (trueProbabilityPost < detection_Th_min_abs ||
                        detection_Th_min_rel != null && trueProbabilityPost < detection_Th_min_rel * trueProbabilityPrior)
                    continue;

                diffMapInfoPost.put(indexNode, nodeInfo);
                if (indexSetExclude == null || !indexSetExclude.contains(indexNode))
                {
                    if (probTrueMapPostPrior != null) {
                        double[] probPostPrior = {trueProbabilityPost, trueProbabilityPrior};
                        probTrueMapPostPrior.put(indexNode, probPostPrior);
                    }
                }
            }
        }
        return diffMapInfoPost;
    }

    String getName(int nidx)
    {
        return nodeList.get(nidx).getName();
    }

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

            name2InfoPrior.put(getName(nidx), nodeInfo);
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
        return componentId2component;
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

    public class ConnectivityComponent
    {
        public static final int TYPE_FLAG_HAVE_CHILDREN = 1;
        public static final int TYPE_FLAG_HAVE_PARENTS = 2;
        //public static final int START_POINT = 1;
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
     }

    /**
     * for test mostly!
     * @param work_directory
     */
    private static void check_models_in_directory(String work_directory)
    {
        File directory = new File(work_directory);

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
    public static void main(String[] args) throws Exception
    {
//        TODO: test 1
//        String work_directory="C:\\projects\\radware\\UnBBayes\\Nets-avi\\Nets";
//        check_models_in_directory(work_directory);

        System.out.println("Test UnBBWrapper");
        String cfg_file = args[0];
        init(cfg_file);
        log_algo.setUseParentHandlers(false);
        log_algo.info("Test UnBBWrapper");
        String ev_file=args[1];
        log_algo.info(String.format("processing ev_file:%s", ev_file));

        String modelFilePath = Util.getAndTrim("model_file", configuration);
        String work_dir = Util.getAndTrim("work_dir", configuration);
        JSONParser parser = new JSONParser();

        UnBBWrapper netWrapper = new UnBBWrapper(modelFilePath);
        Object obj = parser.parse(new FileReader(ev_file));
        System.out.println(obj);
        JSONObject jsonObject = (JSONObject) obj;
        JSONObject evidences = (JSONObject)jsonObject.get("evidences");
        System.out.println(evidences);
        netWrapper.net.resetEvidences();
        JSONObject hardEvidences=null;
        JSONObject softEvidences =null;
        if (evidences.containsKey("hard"))
        {
            hardEvidences =(JSONObject) evidences.get("hard");
        }
        if (evidences.containsKey("soft"))
        {
            softEvidences =(JSONObject) evidences.get("soft");
        }
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            String nodeName = node.getName();
            ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
            if (hardEvidences.containsKey(nodeName))
            {
                //String node_name=(String) he;
                String state_name= (String) hardEvidences.get(nodeName);
                String msg = String.format("hard evidence: variable index=%d; '%s' => set state '%s'", indexNode, nodeName, state_name);
                System.out.println(msg);
                log_algo.info(msg);

                //index2state.put(indexNode,state);//for generation
                int indexState= UnBBWrapper.getStateIndex(node,state_name);
                if( indexState<0)
                {
                    throw new RuntimeException(String.format("wrong state = '%s' for node = '%s'",state_name,nodeName));
                }

                probabilisticNode.addFinding(indexState);
                log_algo.fine("->state= " + state_name);
            } else if (softEvidences.containsKey(nodeName))
            {
                JSONObject likelihoods=(JSONObject)softEvidences.get(nodeName);

                String msg = String.format("soft evidence: for variable index=%d; name=%s; likelihoods: %s", indexNode, nodeName, likelihoods);
                System.out.println(msg);
                log_algo.fine(msg);

                int statesSize = probabilisticNode.getStatesSize();
                float[] likelihood_arr=new float[statesSize];
                Arrays.fill(likelihood_arr, 0.0F);
                for(int ii=0;ii<statesSize; ii++)
                {
                    String state_name=probabilisticNode.getStateAt(ii);
                    if (likelihoods.containsKey(state_name))
                    {
                        double prob = (double) likelihoods.get(state_name);
                        likelihood_arr[ii] = (float) prob;
                    }
                }

                probabilisticNode.addLikeliHood(likelihood_arr);
                log_algo.fine("->likelihood_arr= " + likelihood_arr);
            }
        }

        try
        {
            netWrapper.algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
        }

        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            String name = node.getName();

            NodeInfo nodeInfo = new NodeInfo(node);
            NodeInfo priorNodeInfo = netWrapper.name2InfoPrior.get(name);
            System.out.println("---current---"+nodeInfo);
            System.out.println("---prior-----"+priorNodeInfo);
            double max_abs_difference=0;
            double min_abs_difference=0;
            String state_max=null;
            String state_min=null;
            for (String state:nodeInfo.state2probability.keySet())
            {
                double p1=nodeInfo.state2probability.get(state);
                double p2=priorNodeInfo.state2probability.get(state);
                double delta=p2-p1;
                if(  delta<min_abs_difference)
                {
                    min_abs_difference=delta;
                    state_min=state;
                }
                if (delta>max_abs_difference)
                {
                    max_abs_difference=delta;
                    state_max=state;
                }


            }
            System.out.println(String.format("===diff=== max: %f (%s); min: %f (%s)", max_abs_difference,state_max,min_abs_difference,state_min));
        }
//        Map<Integer, UnBBWrapper.NodeInfo> diffMapInfoPost = UnBBWrapper.getDifferences(netWrapper, output_T_min_abs, output_T_min_rel, null, null);
//        System.out.println(diffMapInfoPost);



    }


}
