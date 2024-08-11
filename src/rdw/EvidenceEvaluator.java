package rdw;

import unbbayes.prs.Node;
import unbbayes.prs.bn.ProbabilisticNode;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * TODO:
 * thresholds
 * output as delta
 * distances
 * NOT TODO:
 * reset evedence
 */
public class EvidenceEvaluator extends Loggable
{
    static public Logger log_algo = Logger.getLogger("algo");
    //input patterns
    public static final String PREF_EV = "ev_";
    public static final String PREF_SEV = "sev_";
    public static final String SUFFIX = ".cfg";
    public static final String PREF_PROB = "prob_";
    private static final String PREF_DIFF ="diff_" ;
    private static final String PREF_GEN ="gen_" ;


    private static void saveGeneration_of_evidences(ArrayList<String> paths,Map<String,String> path2info,Map<String,Integer[]> path2type, Node node, int output_generation_of_evidences,
                                                    ArrayList<String> roots, int level, String path, Double output_T_min_abs, Double output_T_min_rel)
    {
        if(level>output_generation_of_evidences)
        {
            processGenerationPathEnd(path2type,path,2);
            return;
        }

        UnBBWrapper.NodeInfo nodeInfo=new UnBBWrapper.NodeInfo(node);
        String[] tokens=nodeInfo.toString().split("=");
        String names = tokens[0];
        String info = tokens[1];
        if(roots.contains(names) &&level!=0 )
        {
            processGenerationPathEnd(path2type,path,4);
            return;
        }
        String path1=path+"->"+String.format("(%s)",names);
        paths.add(path1);

        path2info.put(path1, info);
        Integer[] types={level,0};
        path2type.put(path1,types);
        ArrayList<Node> cildren = node.getChildren();
        if(cildren==null || cildren.size()==0)
        {
            processGenerationPathEnd(path2type,path1,1);
            return;
        }
        for(Node node1:cildren)
        {
            saveGeneration_of_evidences(paths, path2info, path2type,   node1,    output_generation_of_evidences,  roots,  level+1,  path1,output_T_min_abs,output_T_min_rel);
        }
    }

    private static void processGenerationPathEnd(Map<String, Integer[]> path2type, String path, int flag)
    {
        String[] tt=path.split("->");
        String prev_pp="";//pp-partial path
        for(int ii=1;ii<tt.length;ii++ )
        {
            String fragment=tt[ii];
            String pp=prev_pp+"->"+fragment;
            path2type.get(pp)[1]|=flag;
            prev_pp=pp;
        }
    }

    private static void saveGeneration_of_evidences(String work_dir, UnBBWrapper netWrapper, String inleName, int output_generation_of_evidences,
                                                    Set<Integer> indexSet, Double output_T_min_abs, Double output_T_min_rel) throws FileNotFoundException
    {

        String outleName = inleName.replace(PREF_EV, PREF_GEN).replace(SUFFIX, ".txt");
        String outFilePath = work_dir + File.separator + outleName;

        log_algo.info("saving generations in : " + outFilePath);
        ArrayList<String> roots=new ArrayList<>();
        for (Integer indexNode:indexSet)
        {
            UnBBWrapper.NodeInfo nodeInfo=new UnBBWrapper.NodeInfo(netWrapper.nodeList.get(indexNode));
            String ss = String.format("%d\t%s", indexNode,nodeInfo);
            log_algo.info("root: "+ss);
            String[] tokens=nodeInfo.toString().split("=");
            String names = tokens[0];
            roots.add(names);
        }
        ArrayList<String> paths=new ArrayList<>();
        Map<String,String> path2info = new HashMap<>();
        Map<String,Integer[]> path2type=new HashMap<>();

        for(Integer indexNode:indexSet)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            int level=0;
            saveGeneration_of_evidences( paths, path2info,path2type, node,    output_generation_of_evidences,  roots,  level,  "",output_T_min_abs,output_T_min_rel);
         }

        PrintWriter printWriter = new PrintWriter(new FileOutputStream(new File(outFilePath)/*, false*/));
        for(String path:paths)
        {
            String info =path2info.get(path);
            Integer[] types=path2type.get(path);
            String ss = String.format("%d\t%d\t%s=%s", types[0],types[1],path,info);
            printWriter.println(ss);

        }
        printWriter.flush();
        printWriter.close();
    }

    private static void saveDifferences(String work_dir, UnBBWrapper netWrapper, String inName, int output_differences, Double output_T_min_abs, Double output_T_min) throws FileNotFoundException
    {
        //TODO: output_differences may be used for configuration of output
        String outleName = inName.replace(PREF_EV, PREF_DIFF).replace(SUFFIX, ".txt");
        String outFilePath = work_dir + File.separator + outleName;
        PrintWriter printWriter = new PrintWriter(new FileOutputStream(new File(outFilePath)/*, false*/));

        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            UnBBWrapper.NodeInfo nodeInfo=new UnBBWrapper.NodeInfo(netWrapper.nodeList.get(indexNode));
            String newString=nodeInfo.toString();
            String name=netWrapper.getName(indexNode);

            String prioryStr=netWrapper.name2InfoPrior.get(name).toString();
            if(prioryStr.equals(newString)) continue;
            String ss = String.format("%d\t%s=%s<->%s", indexNode,name,prioryStr,newString);
            printWriter.println(ss);
        }
        printWriter.flush();
        printWriter.close();
        log_algo.info(String.format("saved differences %s for file saving differences in : %s", inName,outleName));
    }

    private static void saveProbabilities(String work_dir, UnBBWrapper netWrapper, String inleName, int output_probabilities, Double output_T_min_abs, Double output_T_min_rel) throws FileNotFoundException
    {

        Map<Integer, UnBBWrapper.NodeInfo> diffMapInfoPost = UnBBWrapper.getDifferences(netWrapper, output_T_min_abs, output_T_min_rel, null, null);
        //TODO: output_probabilities may be used for configuration of output
        String outleName = inleName.replace(PREF_EV, PREF_PROB).replace(SUFFIX, ".txt");
        String outFilePath = work_dir + File.separator + outleName;
        PrintWriter printWriter = new PrintWriter(new FileOutputStream(new File(outFilePath)/*, false*/));
        for(int indexNode:diffMapInfoPost.keySet())
        {
            UnBBWrapper.NodeInfo nodeInfo=diffMapInfoPost.get(indexNode);
            String ss = String.format("%d\t%s", indexNode,nodeInfo);
            printWriter.println(ss);
        }
        printWriter.flush();
        printWriter.close();
        log_algo.info(String.format("saved Probabilities %s file for in %s", inleName,outFilePath));
    }



    private static void processEvidenceFile(UnBBWrapper netWrapper, String work_dir, int output_probabilities, int output_generation_of_evedence,
                                            int output_differences, String inleName, Double output_T_min_abs, Double output_T_min_rel) throws IOException
    {
        log_algo.info(String.format("Processing %s file", inleName));
       //TODO: it does not work???? net.resetLikelihoods();        //???net.resetEvidences();
        //TODO: this is TMP solution       // netWrapper = new NetWrapper(modelFilePath);
        netWrapper.net.resetEvidences();
        String inFilePath = work_dir + File.separator + inleName;

        Properties inCfg = new Properties();
        inCfg.load(new FileInputStream(inFilePath));
        Map<Integer,String> index2state=new TreeMap<>();
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            String nodeName = node.getName();
            if (inCfg.containsKey(nodeName))
            {
                log_algo.info(String.format("setting evidence for variable index=%d; name=%s", indexNode, nodeName));
                String state = Util.getAndTrim(nodeName, inCfg);
                index2state.put(indexNode,state);//for generation
                int indexState= UnBBWrapper.getStateIndex(node,state);
                if( indexState<0)
                {
                    throw new RuntimeException(String.format("unknown staete = %s for node = %s",state,nodeName));
                }
                ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
                probabilisticNode.addFinding(indexState);
                log_algo.fine("->state= " + state);
            }
        }
        // propagate evidence
        try
        {
            netWrapper.algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());
        }
        if(output_probabilities>0)
            saveProbabilities(work_dir, netWrapper, inleName,output_probabilities,output_T_min_abs,output_T_min_rel);
        if(output_differences>0)
            saveDifferences(work_dir, netWrapper, inleName,output_differences,output_T_min_abs,output_T_min_rel);
        if(output_generation_of_evedence>0)
            saveGeneration_of_evidences(work_dir, netWrapper, inleName,output_generation_of_evedence,index2state.keySet(),output_T_min_abs,output_T_min_rel);
    }
    private static void processSoftEvidenceFile(UnBBWrapper netWrapper, String work_dir, int output_probabilities, int output_generation_of_evedence,
                                            int output_differences, String inFileName, Double output_T_min_abs, Double output_T_min_rel) throws IOException
    {
        log_algo.info(String.format("Processing %s file", inFileName));
        netWrapper.net.resetEvidences();
        netWrapper.net.resetLikelihoods();
        String inFilePath = work_dir + File.separator + inFileName;

        Properties inCfg = new Properties();
        inCfg.load(new FileInputStream(inFilePath));
        Map<Integer,ArrayList<Double>> index2state=new TreeMap<>();
        log_algo.info("processing soft evidence task: " + inFileName + " : " + inCfg);
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            Node node = netWrapper.nodeList.get(indexNode);
            String nodeName = node.getName();
            if (inCfg.containsKey(nodeName))
            {
                log_algo.fine(String.format("setting evidence for variable index=%d; name=%s", indexNode, nodeName));
                ArrayList<Double> likelihood_=Util.getDoubleArray(nodeName, inCfg);
                index2state.put(indexNode,likelihood_);//for generation
                float[] likelihood=new float[likelihood_.size()];
                for(int ii=0;ii<likelihood_.size(); ii++)
                {
                    double dd=likelihood_.get(ii);
                    likelihood[ii]=(float)(dd);
                }
                ProbabilisticNode probabilisticNode = (ProbabilisticNode) node;
                probabilisticNode.addLikeliHood(likelihood);
                log_algo.fine("->likelihood= " + likelihood_);
            }
        }
        // !!!!!!propagate evidence!!!!!
        try
        {
            netWrapper.algorithm.propagate();
        } catch (Exception exc)
        {
            Util.criticalError(exc.getMessage());

        }
        if(output_probabilities>0)
            saveProbabilities(work_dir, netWrapper, inFileName,output_probabilities,output_T_min_abs,output_T_min_rel);
        if(output_differences>0)
            saveDifferences(work_dir, netWrapper, inFileName,output_differences,output_T_min_abs,output_T_min_rel);
        if(output_generation_of_evedence>0)
            saveGeneration_of_evidences(work_dir, netWrapper, inFileName,output_generation_of_evedence,index2state.keySet(),output_T_min_abs,output_T_min_rel);
    }

    public static void main(String[] args) throws Exception
    {

        String cfg_file = args[0];
        init(cfg_file);

        log_algo.setUseParentHandlers(false);
        System.out.println("processing configuration: " + configuration);
        String modelFilePath = Util.getAndTrim("model_file", configuration);
        String work_dir = Util.getAndTrim("work_dir", configuration);
        int output_probabilities = 0;
        if (configuration.containsKey("output.probabilities"))
        {
            output_probabilities = Util.getInt("output.probabilities", configuration);
        }
        int output_generation_of_evedence = 0;
        if (configuration.containsKey("output.probabilities"))
        {
            output_generation_of_evedence = Util.getInt("output.generation_of_evedence", configuration);
        }
        Double output_T_min_abs=null;
        if (configuration.containsKey("output.T_min_abs"))
        {
            output_T_min_abs = Util.getDouble("output.T_min_abs", configuration);
        }
        Double output_T_min_rel=null;
        if (configuration.containsKey("output.T_min_rel"))
        {
            output_T_min_rel = Util.getDouble("output.T_min_rel", configuration);
        }
        int output_differences = 0;
        if (configuration.containsKey("output.probabilities"))
        {
            output_differences = Util.getInt("output.differences", configuration);
        }

        log_task.info(String.format("loading %s",modelFilePath));
        UnBBWrapper netWrapper = new UnBBWrapper(modelFilePath);
        String outFilePath0 = work_dir + File.separator + "prob_prior.txt";
        PrintWriter printWriter0 = new PrintWriter(new FileOutputStream(new File(outFilePath0)/*, false*/));
        log_algo.info(String.format("prob_prior for %s",modelFilePath));
        System.out.println(String.format("prob_prior for %s",modelFilePath));
        for (int indexNode = 0; indexNode < netWrapper.netSize; indexNode++)
        {
            UnBBWrapper.NodeInfo nodeInfo=new UnBBWrapper.NodeInfo(netWrapper.nodeList.get(indexNode));
            String ss = String.format("%d\t%s", indexNode,nodeInfo);
            log_algo.fine(ss);
            printWriter0.println(ss);
        }
        printWriter0.flush();
        printWriter0.close();

        File directory = new File(work_dir);
        Util.FilenameFilterPrefSuf filenameFilterPrefSuf = new Util.FilenameFilterPrefSuf(PREF_EV, SUFFIX);
        String[] fileNames = directory.list(filenameFilterPrefSuf);
        for (String evidenceFileName : fileNames)
        {
            processEvidenceFile(netWrapper, work_dir, output_probabilities, output_generation_of_evedence, output_differences, evidenceFileName,output_T_min_abs,output_T_min_rel);
            log_task.info(String.format("processed  %s",evidenceFileName));
            System.out.println(String.format("processed  %s",evidenceFileName));
        }

        Util.FilenameFilterPrefSuf filenameFilterPrefSuf_sev = new Util.FilenameFilterPrefSuf(PREF_SEV, SUFFIX);
        String[] fileNames_sev = directory.list(filenameFilterPrefSuf_sev);
        for (String evidenceFileName : fileNames_sev)
        {
            processSoftEvidenceFile(netWrapper, work_dir, output_probabilities, output_generation_of_evedence, output_differences, evidenceFileName,output_T_min_abs,output_T_min_rel);
            log_task.info(String.format("processed  %s",evidenceFileName));
            System.out.println(String.format("processed  %s",evidenceFileName));
        }


    }



}
