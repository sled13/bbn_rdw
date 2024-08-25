package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import unbbayes.prs.Node;
import unbbayes.prs.bn.ProbabilisticNode;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static rdw.Util.map2str;
import static rdw.Util.parseEvJson;

public interface StateProbabilityCalculator
{
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
    public static void printInfo(Map<String,NodeInfo> info)
    {
        for (String node_name:info.keySet())
        {
            System.out.println(String.format("'%s' -->>%s", node_name,info.get(node_name)));
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
