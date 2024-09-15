package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static rdw.StateProbabilityCalculator.NodeInfo.*;

public class BbnProcessor extends Loggable
{
    static double ABS_TH_SENS = 0.40;
    static double REL_TH_SENS = 1.20;
    public static void createSourceTargetFile( String modelFilePath,String work_dir,String dest_prefix) throws IOException
    {
        StateProbabilityCalculator prob_calc = new StateProbabilityCalculator_UnBB(modelFilePath);
        int show_flag = SHOW_NAME;
        Map<String, StateProbabilityCalculator.NodeInfo> priori_info = prob_calc.getInfo(show_flag, true);
        JSONObject result = new JSONObject();
        Map evidences=new TreeMap();
        Map evidences_soft=new TreeMap();
        evidences.put("soft",evidences_soft);
        Map targets=new TreeMap();
        result.put("evidences",evidences);
        result.put("targets",targets);
        for(String name:priori_info.keySet())
        {
            if(name.startsWith(dest_prefix))
            {
                targets.put(name,null);
            }
            else
            {
                Map<String, Double> state2probability = priori_info.get(name).getStatesProbabilities();
                /*TODO:Probably a normalization has no effect. Check additionally
                Double maxValue= Collections.max(state2probability.values());
                for (String state: state2probability.keySet())
                {
                    state2probability.put(state,state2probability.get(state)/maxValue);
                }*/
                evidences_soft.put(name,state2probability);
            }
        }
        String res_file = "ev_all_source_target.json";
        String resFilePath = work_dir + File.separator + res_file;
        FileWriter file = new FileWriter(resFilePath);
        String jsonString = result.toJSONString();
        jsonString = jsonString.replace("{", "{\n\t").replace(",", ",\n\t");//.replace("}","\n\t}");
        file.write(jsonString);
        file.flush();
        file.close();

    }
    public static void main(String[] args) throws Exception
    {
        //task_evidences-rdw.cfg
        String message1 = "BbnProcessor.main()";
        System.out.println(message1);
        String cfg_file = args[0];
        init(cfg_file);
        log_algo.setUseParentHandlers(false);
        log_algo.info(message1);
        //TODO: this definition is used for logging debug only. Maybe delete??
        int show_flag = SHOW_NAME | SHOW_DESCRIPTION | SHOW_EXPLANATION;
        String modelFilePath = Util.getAndTrim("model_file", configuration);
        String work_dir = Util.getAndTrim("work_dir", configuration);
        /*TODO: define separate task*/
        //createSourceTargetFile( modelFilePath,work_dir, "F");
        if (args.length > 1) //calling a ev_ file individually
        {
            //C:\\data\\radware\\bbn_work\\ev_get_priory_selected_without_filtration.json
            //C:\\data\\radware\\bbn_work\\ev_get_priory_all_without_filtration.json
            //C:\\data\\radware\\bbn_work\\ev_test.json

            String ev_file = args[1];
            log_algo.info(String.format("processing one file: ev_file:%s", ev_file));
            processInputFile(ev_file, modelFilePath, show_flag);
        } else //all file in directory are processed
        {

            processAllInputFiles(work_dir, modelFilePath, show_flag);
        }

    }

    public static void processInputFile(String ev_file, String modelFilePath, int show_flag) throws IOException, ParseException
    {
        StateProbabilityCalculator prob_calc = new StateProbabilityCalculator_UnBB(modelFilePath);
        ArrayList<Map> evJson = Util.parseEvJson(ev_file);
        Map hardEvidences = evJson.get(0);
        Map softEvidences = evJson.get(1);
        Map targets = evJson.get(2);
        Map defaults = evJson.get(3);


        Map<String, StateProbabilityCalculator.NodeInfo> priori_info = prob_calc.getInfo(show_flag, true);
        log_algo.info("----priori_info--------------------");
        StringBuffer stringBuffer = StateProbabilityCalculator.printInfo(priori_info);
        log_algo.info(stringBuffer.toString());
        ArrayList<StateProbabilityCalculator.HardEvidence> hardEvidences_arr = getHardEvidencesFromMap(hardEvidences);
        ArrayList<StateProbabilityCalculator.SoftEvidence> softEvidences_arr = getSoftEvidencesFromMap(softEvidences);
        ArrayList<String> effectiveNodes = prob_calc.setEvidences(hardEvidences_arr, softEvidences_arr, false);
        log_algo.info(String.format("effectiveNodes: %s", effectiveNodes));

        Map<String, StateProbabilityCalculator.NodeInfo> posterior_info = prob_calc.getInfo(show_flag, false);
        log_algo.info("----posterior_info--------------------");
        StringBuffer stringBuffer1 = StateProbabilityCalculator.printInfo(posterior_info);
        log_algo.info(stringBuffer1.toString());

        Set target_nodes_names = null;
        if (targets != null)
        {
            target_nodes_names = targets.keySet();
        } else
        {
            target_nodes_names = priori_info.keySet();
        }
        JSONObject result = new JSONObject();
        Double default_abs_th = null;
        Double default_rel_th = null;
        if (defaults != null)
        {
            if (defaults.containsKey("abs_th") && defaults.get("abs_th") != null)
            {
                default_abs_th = (Double) defaults.get("abs_th");
            }
            if (defaults.containsKey("rel_th") && defaults.get("abs_th") != null)
            {
                default_rel_th = (Double) defaults.get("rel_th");
            }
        }
        for (Object obj : target_nodes_names)
        {
            JSONObject result_for_target = new JSONObject();
            String names = (String) obj;
            StateProbabilityCalculator.NodeInfo nodeInfo = posterior_info.get(names);
            StateProbabilityCalculator.NodeInfo priorNodeInfo = priori_info.get(names);
            log_algo.info("---current---" + nodeInfo);
            log_algo.info("---prior-----" + priorNodeInfo);
            Map<String, Double> state2probability = priorNodeInfo.getStatesProbabilities();
            if (targets == null || targets.get(names) == null)
            {
                for (String state : state2probability.keySet())
                {
                    updateTargetResult(state, nodeInfo, default_rel_th, default_abs_th, result_for_target, state2probability);
                }
            } else
            {
                Map target = (Map) targets.get(names);
                for (Object obj1 : target.keySet())
                {
                    String state = (String) obj1;
                    Double abs_th = default_abs_th;
                    Double rel_th = default_rel_th;
                    Map state_data = (Map) target.get(state);
                    if (state_data != null && state_data.containsKey("abs_th") && state_data.get("abs_th") != null)
                    {
                        abs_th = (Double) state_data.get("abs_th");
                    }
                    if (state_data != null && state_data.containsKey("rel_th") && state_data.get("rel_th") != null)
                    {
                        rel_th = (Double) state_data.get("rel_th");
                    }
                    updateTargetResult(state, nodeInfo, rel_th, abs_th, result_for_target, state2probability);
                }

            }
            result.put(names, result_for_target);

            /* TODO:test!!!! to see how Ubb implements virtual evidence */
            /*StateProbabilityCalculator_UnBB prob_calc_unbb = (StateProbabilityCalculator_UnBB) prob_calc;
            String outFile = modelFilePath.replace(".net", "_new.net");
            prob_calc_unbb.save(outFile);*/
        }
        addPerturbationSensitivity(prob_calc,evJson,result);
        System.out.println("------------results-------------------");
        System.out.println(result);
        String res_file = ev_file.replace("\\ev_", "\\res_");
        FileWriter file = new FileWriter(res_file);
        String jsonString = result.toJSONString();
        jsonString = jsonString.replace("{", "{\n\t").replace(",", ",\n\t");//.replace("}","\n\t}");
        file.write(jsonString);
        file.flush();
        file.close();
    }

    private static void addPerturbationSensitivity(StateProbabilityCalculator prob_calc,ArrayList<Map> evJson, JSONObject result)
    {
        Map hardEvidences = evJson.get(0);
        Map softEvidences = evJson.get(1);
        Map targets = evJson.get(2);
        Map defaults = evJson.get(3);
        for(Object obj:result.keySet())
        {
            String varName=(String) obj;
            Map context = (Map)result.get(varName);
            if(context ==null ||context.size()<1)
            {
                long report_flag=0;
                String state=null;

                if(targets!=null &&targets.get(varName)!=null )
                {
                    Map target_for_name=(Map) targets.get(varName);
                    for(Object obj1:target_for_name.keySet())
                    {
                        state=(String)obj1;
                        if(target_for_name.get(state)!= null)
                        {
                            Map state_context=(Map) target_for_name.get(state);
                           if(state_context.containsKey("report_flag"))
                            report_flag=(long) state_context.get("report_flag");

                        }
                    }
                }
                if (state!=null&&report_flag==0&&defaults.containsKey("report_flag"))
                {
                    report_flag=(long) defaults.get("report_flag");
                }
                if (state!=null&&report_flag!=0)
                {
                    ArrayList<StateProbabilityCalculator.HardEvidence> hardEvidences_arr = getHardEvidencesFromMap(hardEvidences);
                    ArrayList<StateProbabilityCalculator.SoftEvidence> softEvidences_arr = getSoftEvidencesFromMap(softEvidences);
                    Map<String, StateProbabilityCalculator.NodeInfo> priori_info = prob_calc.getInfo(SHOW_NAME, true);
                    updateResultForFlag(prob_calc,varName,state,hardEvidences_arr, softEvidences_arr,context,(int)report_flag,
                            priori_info);
                }
            }

        }
    }

    private static void updateResultForFlag(StateProbabilityCalculator prob_calc, String varName, String state,
                                            ArrayList<StateProbabilityCalculator.HardEvidence> hardEvidences,
                                            ArrayList<StateProbabilityCalculator.SoftEvidence> softEvidences,
                                            Map context, int reportFlag, Map<String, StateProbabilityCalculator.NodeInfo> priori_info)
    {
        if (reportFlag==0) return; //the call was done by error!?
        //TODO: only reportFlag==2 is implemented. IMPLEMENT !!!!!!!!!!!
        if (reportFlag!=2)
        {
            String msg = "updateResultForFlag flag=%d; varName='%s'; " +
                    "state='%s' --Not implemented yet!!! flag==2 processed instead";
            System.out.println(String.format(msg, reportFlag,varName,state));
            log_algo.info(msg);
        }
        ArrayList<StateProbabilityCalculator.HardEvidence> hardEvidences1=
                new ArrayList<>(hardEvidences);
        StateProbabilityCalculator.HardEvidence hv=new StateProbabilityCalculator.HardEvidence(varName,state);
        hardEvidences1.add(hv);
        ArrayList<String> effectiveNodes=prob_calc.setEvidences(hardEvidences1,softEvidences,true);
        Map<String, StateProbabilityCalculator.NodeInfo> posterior_info = prob_calc.getInfo(SHOW_NAME, false);
        for (String name : priori_info.keySet())
        {
            if(effectiveNodes.contains(name)) continue;

            StateProbabilityCalculator.NodeInfo posterNodeInfo = posterior_info.get(name);
            StateProbabilityCalculator.NodeInfo priorNodeInfo = priori_info.get(name);
            log_algo.info("---checked---" + posterNodeInfo);
            log_algo.info("---prior-----" + priorNodeInfo);
            Map<String, Double> state2probability = priorNodeInfo.getStatesProbabilities();
            Double maxRation=null;
            String importantState=null;
            Double abs_=null;
            for (String state1:state2probability.keySet())
            {
                double p1=posterNodeInfo.getStatesProbabilities().get(state1);
                if(p1 < ABS_TH_SENS)  continue;
                double p0 = state2probability.get(state1);
                double ratio = p1 / (p0 + 0.0000001);
                if (ratio < REL_TH_SENS) continue;
                if (maxRation==null ||maxRation<ratio)
                {
                    maxRation=ratio;
                    importantState=state1;
                    abs_=p1;
                }
                if (importantState !=null)
                {
                    Map dict=new TreeMap();
                    Map details=new TreeMap();
                    details.put("absolute",abs_);
                    details.put("ratio",maxRation);
                    dict.put(importantState,details);

                    context.put(name,dict);
                }

            }
        }

    }


    public static void processAllInputFiles(String work_dir, String modelFilePath, int show_flag) throws IOException, ParseException
    {
        String PREF_EV = "ev_";
        String SUFFIX = ".json";

        log_algo.info(String.format("processing all input files in directory::%s", work_dir));
        File directory = new File(work_dir);
        Util.FilenameFilterPrefSuf filenameFilterPrefSuf = new Util.FilenameFilterPrefSuf(PREF_EV, SUFFIX);
        String[] fileNames = directory.list(filenameFilterPrefSuf);
        for (String evidenceFileName : fileNames)
        {
            System.out.println("+++++++++++++++++++++++++++");
            String inFilePath = work_dir + File.separator + evidenceFileName;
            processInputFile(inFilePath, modelFilePath, show_flag);
            String msg = String.format("processed input file:%s", inFilePath);
            log_algo.info(msg);
            System.out.println("=====================" + msg);
        }
    }

    private static void updateTargetResult(String state, StateProbabilityCalculator.NodeInfo nodeInfo, Double default_rel_th, Double default_abs_th, Map<String, Double> result_for_target, Map<String, Double> state2probability)
    {
        double p1 = nodeInfo.getStatesProbabilities().get(state);
        if (default_rel_th == null && default_abs_th == null)
        {
            result_for_target.put(state, p1);
            return;
        }

        if (default_abs_th != null)
        {
            if (p1 < default_abs_th) return;
        }
        if (default_rel_th != null)
        {
            double p0 = state2probability.get(state);
            double ratio = p1 / (p0 + 0.0000001);
            if (ratio < default_rel_th) return;
        }
        result_for_target.put(state, p1);
    }

    private static ArrayList<StateProbabilityCalculator.HardEvidence> getHardEvidencesFromMap(Map hardEvidences)
    {
        ArrayList<StateProbabilityCalculator.HardEvidence> hardEvidences_arr = null;
        if (hardEvidences != null)
        {
            hardEvidences_arr = new ArrayList<>();
            for (Object obj : hardEvidences.keySet())
            {
                String variableName = (String) obj;
                String stataName = (String) hardEvidences.get(variableName);
                StateProbabilityCalculator.HardEvidence he = new StateProbabilityCalculator.HardEvidence(variableName, stataName);
                hardEvidences_arr.add(he);
            }
        }
        return hardEvidences_arr;
    }

    private static ArrayList<StateProbabilityCalculator.SoftEvidence> getSoftEvidencesFromMap(Map softEvidences)
    {
        ArrayList<StateProbabilityCalculator.SoftEvidence> softEvidences_arr = null;
        if (softEvidences != null)
        {
            softEvidences_arr = new ArrayList<>();
            for (Object obj : softEvidences.keySet())
            {
                String variableName = (String) obj;
                Map likelihoods = (Map) softEvidences.get(variableName);
                StateProbabilityCalculator.SoftEvidence se = new StateProbabilityCalculator.SoftEvidence(variableName, likelihoods);
                softEvidences_arr.add(se);
            }

        }
        return softEvidences_arr;
    }

}

