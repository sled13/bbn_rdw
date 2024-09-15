package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import static rdw.Util.map2str;
import static rdw.Util.parseEvJson;

public interface StateProbabilityCalculator
{
    //-start-------main functionality--(1-st stage)--------------------
    public static class HardEvidence
    {
        String _variable;
        String _state;
        public HardEvidence(String variable, String state)
        {
            _variable=variable;
            _state=state;
        }

        @Override
        public String toString()
        {
            return String.format("'%s'=='%s'", _variable,_state);
        }

        public String getVariable(){return _variable;};
        public String getState() {return _state;};
    }
    public static class SoftEvidence
    {
        String _variable;
        Map _likelihoods;

        @Override
        public String toString()
        {
            return String.format("'%s==(%s)", _variable,map2str(_likelihoods));
        }

        public SoftEvidence(String variable, Map likelihoods)
        {
            _variable=variable;
            _likelihoods=likelihoods;
        }

        public String getVariable()
        {
            return _variable;
        }

        public Map<String, Double> getLikelihood()
        {
            return _likelihoods;
        }
    }

    public interface NodeInfo
    {
        public final static int SHOW_NAME = 1;
        public final static int SHOW_DESCRIPTION = SHOW_NAME<<1;
        public final static int SHOW_EXPLANATION = SHOW_DESCRIPTION<<1;

        public String getName();
        public Map <String,Double> getStatesProbabilities();
        public Double getProbability(String state);
    }

    public ArrayList<String> setEvidences(ArrayList<HardEvidence> hardEvidences,ArrayList<SoftEvidence> softEvidences,
                                          boolean reset_before);
    public Map<String,NodeInfo> getInfo(int flag,boolean reset_before);
    public static StringBuffer printInfo(Map<String,NodeInfo> info)
    {
        StringBuffer stringBuffer = new StringBuffer();
        for (String node_name:info.keySet())
        {
            stringBuffer.append(String.format("'%s' -->>%s", node_name,info.get(node_name)));
        }
        return stringBuffer;
    }
    //-end-------main functionality--(1-st stage)--------------------
    /**
     *
     * @param variableName
     * @return results.keys - is a set (of variable names) that is usually called Markov Blanket. Each name is mapped
     * to flag 1 -'parent', 2-'child', 3- parent of child (it is not standard for Mb)
     */
    public Map<String,Integer> getMarkovBlanket(String variableName);

    public static class Influence extends TreeMap
    {
        //int n_paths =1;
        //ArrayList<Integer> shortestPath =null;

        public Influence(ArrayList<Integer> path)
        {
            this.put("n_paths",1);
            this.put("shortestPath", new ArrayList<>(path));
        }
    }

    public static  Map<String,Influence> getInfluencers(StateProbabilityCalculator spc,String variableName,
                                                        int maxDistance,Set<String> sources)
    {
        Map<String,Influence> influencers= new TreeMap<>();
        Map<String,ArrayList> checkList=new TreeMap();
        ArrayList<Integer> path0= new ArrayList<>();
        Influence root=new Influence(path0);
        checkList.put(variableName,(ArrayList) root.get("shortestPath"));
        for(int dist=0; dist<maxDistance;dist++ )
        {
            Map<String,ArrayList> newCheckList=new TreeMap();
            for( String name:checkList.keySet())
            {
                //if(name==variableName) continue;
                ArrayList path=checkList.get(name);
                Map<String, Integer> mb = spc.getMarkovBlanket(name);
                for(String name1:mb.keySet())
                {
                    if(sources!=null &&!sources.contains(name1)) continue;
                    if(name1==name ||name1==variableName) continue;
                    if (influencers.containsKey(name1))
                    {
                        Influence influence = influencers.get(name1);
                        influence.put("n_paths",(int)influence.get("n_paths")+1);
                        continue;
                    }
                    ArrayList path1=new ArrayList(path);
                    Integer type = mb.get(name1);
                    path1.add(type);
                    Influence influence1=new Influence(path1);
                    influencers.put(name1,influence1);
                    newCheckList.put(name1,path1);

                }

            }
            if (checkList.size()<1)
            {
                System.out.println(String.format("Influence analysis is stopped after distance %s: nothing to do more", dist));
                return influencers;
            }
            checkList=newCheckList;
        }
        System.out.println(String.format("Influence analysis is finished for max distance %s; not processed checkListSize:%s", maxDistance,checkList.size()));
        return influencers;
    }
    static void analyzeInfluences(Map<String, NodeInfo> info, String res_file, String work_dir, int max_dist, StateProbabilityCalculator prob_calc) throws IOException, ParseException
    {
        Set<String> source_set= info.keySet();
        Set<String> target_set= info.keySet();
        if (res_file !=null)
        {
            String resFilePath = work_dir + File.separator + res_file;
            ArrayList<Map> res = parseEvJson(resFilePath);
            // Map hardEvidences = res.get(0);
            Map softEvidences = res.get(1);
            Map targets = res.get(2);
            //Map defaults = res.get(3);
            source_set=softEvidences.keySet();
            target_set=targets.keySet();
        }
        System.out.println(String.format("==== TEST: influencers for max distance:%s====", max_dist));
        JSONObject result = new JSONObject();
        for(String varName:target_set)
        {
            Map<String, Influence> influencers = StateProbabilityCalculator.getInfluencers(prob_calc, varName, max_dist,
                    source_set);
            System.out.println(String.format("%s ==>>%s", varName,influencers));
            result.put(varName,influencers);

            String influenceFilePath=  work_dir + File.separator + "influence.json";

            FileWriter file = new FileWriter(influenceFilePath);
            String jsonString = result.toJSONString();
            jsonString = jsonString.replace("{", "{\n\t").replace(",", ",\n\t");//.replace("}","\n\t}");
            file.write(jsonString);
            file.flush();
            file.close();
        }
    }
    public static void main(String[] args) throws Exception
    {
        String ev_file=args[0];
        ArrayList<Map> res=parseEvJson(ev_file);
        Map hardEvidences = res.get(0);
        Map softEvidences = res.get(1);
        System.out.println(String.format(">>>hardEvidences:%s", hardEvidences));
        System.out.println(String.format(">>>softEvidences:%s", softEvidences));
     }
}
