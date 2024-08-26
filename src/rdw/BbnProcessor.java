package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static rdw.StateProbabilityCalculator.NodeInfo.*;

public class BbnProcessor extends Loggable
{
    public static void main(String[] args) throws Exception
    {
        String message1 = "BbnProcessor.main()";
        System.out.println(message1);
        String cfg_file = args[0];
        init(cfg_file);
        log_algo.setUseParentHandlers(false);
        log_algo.info(message1);
        //TODO: this definition is used for logging debug only. Maybe delete??
        int show_flag = SHOW_NAME | SHOW_DESCRIPTION | SHOW_EXPLANATION;
        String modelFilePath = Util.getAndTrim("model_file", configuration);
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
            String work_dir = Util.getAndTrim("work_dir", configuration);
            processAllInputFiles(work_dir, modelFilePath, show_flag);
        }
    }

    public static void processInputFile(String ev_file, String modelFilePath, int show_flag) throws IOException, ParseException
    {
        StateProbabilityCalculator prob_calc = new StateProbabilityCalculator_UnBB(modelFilePath);
        ArrayList<Map> res = parseEvJson(ev_file);
        Map hardEvidences = res.get(0);
        Map softEvidences = res.get(1);
        Map targets = res.get(2);
        Map defaults = res.get(3);


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
            /* TODO:test!!!!*/
            /*StateProbabilityCalculator_UnBB prob_calc_unbb = (StateProbabilityCalculator_UnBB) prob_calc;
            String outFile = modelFilePath.replace(".net", "_new.net");
            prob_calc_unbb.save(outFile);*/
        }
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

    public static ArrayList<Map> parseEvJson(String ev_file) throws IOException, ParseException
    {
        System.out.println(String.format("----parsing file:%s", ev_file));
        ArrayList<Map> res = new ArrayList<>();

        JSONParser parser = new JSONParser();
        JSONObject jsonObject = (JSONObject)parser.parse(new FileReader(ev_file));
        Map hardEvidences=null;
        Map softEvidences =null;
        Map targets=null;
        Map defaults =null;
        System.out.println(jsonObject);
        if (jsonObject.size()>0)
        {
            if(jsonObject.containsKey("evidences"))
            {
            JSONObject evidences = (JSONObject) jsonObject.get("evidences");
            System.out.println(evidences);

            if (evidences.containsKey("hard"))
            {
                hardEvidences = (JSONObject) evidences.get("hard");
                for (Object obj1 : hardEvidences.keySet())
                {
                    String variableName = (String) obj1;
                    String state_name = (String) hardEvidences.get(variableName);
                    String msg = String.format("hard evidence: variable  '%s' => set state '%s'", variableName, state_name);
                    System.out.println(msg);
                }
            }
            if (evidences.containsKey("soft"))
            {
                softEvidences = (JSONObject) evidences.get("soft");
                for (Object obj1 : softEvidences.keySet())
                {
                    String variableName = (String) obj1;
                    Map<String, Float> likelihoods = (JSONObject) softEvidences.get(variableName);
                    String msg = String.format("soft evidence: for variable  name=%s; likelihoods: %s", variableName, likelihoods);
                    System.out.println(msg);

                }
            }

            }
            if (jsonObject.containsKey("targets"))
            {
                targets = (JSONObject) jsonObject.get("targets");
            }
            if (jsonObject.containsKey("defaults"))
            {
                defaults = (JSONObject) jsonObject.get("defaults");
            }


        }
        res.add(hardEvidences);
        res.add(softEvidences);
        res.add(targets);
        res.add(defaults);
        System.out.println(String.format(">>>hardEvidences:%s", hardEvidences));
        System.out.println(String.format(">>>softEvidences:%s", softEvidences));
        System.out.println(String.format(">>>targets:%s", targets));
        System.out.println(String.format(">>>defaults:%s", defaults));
        return res;
    }
}
