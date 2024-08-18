package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import unbbayes.prs.Node;
import unbbayes.prs.bn.ProbabilisticNode;

import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

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

        public String getVariable(){return _variable;};
        public String getState() {return _state;};
    }
    public static class SoftEvidence
    {
        String _variable;
        Map _likelihoods;

        public SoftEvidence(String variable,Map likelihoods)
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

    public void setEvidences(ArrayList<HardEvidence> hardEvidences,ArrayList<SoftEvidence> softEvidences);
    public Map<String,NodeInfo> getInfo();

    public static void main(String[] args) throws Exception
    {
        String ev_file=args[0];
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(ev_file));
        System.out.println(obj);
        JSONObject jsonObject = (JSONObject) obj;
        JSONObject evidences = (JSONObject)jsonObject.get("evidences");
        System.out.println(evidences);
        JSONObject hardEvidences=null;
        JSONObject softEvidences =null;
        if (evidences.containsKey("hard"))
        {
            hardEvidences =(JSONObject) evidences.get("hard");
            for (Object obj1:hardEvidences.keySet())
            {
                String variableName=(String) obj1;
                String state_name=(String) hardEvidences.get(variableName);
                String msg = String.format("hard evidence: variable  '%s' => set state '%s'",  variableName, state_name);
                System.out.println(msg);
            }
        }
        if (evidences.containsKey("soft"))
        {
            softEvidences =(JSONObject) evidences.get("soft");
            for (Object obj1:softEvidences.keySet())
            {
                String variableName = (String) obj1;
                Map<String,Float> likelihoods=(JSONObject)softEvidences.get(variableName);
                String msg = String.format("soft evidence: for variable  name=%s; likelihoods: %s",  variableName, likelihoods);
                System.out.println(msg);

            }
        }
     }
}
