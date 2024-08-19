package rdw;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

public class Util
{
    public static final double Epsilon7 = 1.0e-7d;
    public static final double Epsilon10 = 1.0e-10d;
    public static final double Epsilon8 = 1.0e-8d;
    public static final double Epsilon6 = 1.0e-6d;
    public static final double Epsilon5 = 1.0e-5d;
    public static final double Epsilon4 = 1.0e-4d;
    public static final double Epsilon3 = 1.0e-3d;
    public static final double Epsilon1 = 1.0e-1d;
    protected static boolean exitOnCritical = true;

    public static void criticalError(String str, Logger log)
    {
        System.err.println(str);
        if (log != null) log.severe(str);
        if (exitOnCritical)
        {
            System.exit(2);
        }
    }

    public static void criticalError(String str)
    {
        criticalError(str, null);
    }

    // cfg utilities:
    static public String getAndTrim(String key, Properties configuration)
    {
        String re = configuration.getProperty(key);
        if (re == null)
        {
            criticalError("key `" + key + "' must be defined");
        }
        int len = re.length();
        String ret = new String();
        boolean space = true;

        for (int i = 0; i < len; i++)
        {
            char ch = re.charAt(i);

            if (Character.isWhitespace(ch))
            {
                if (!space)
                {
                    space = true;
                    ret += ' ';
                }
            } else
            {
                ret += ch;
                space = false;
            }
        }
        int pos0 = ret.indexOf('"');
        int pos1 = ret.lastIndexOf('"');
        if (pos0 >= 0 && pos1 > pos0)
        {
            ret = ret.substring(pos0 + 1, pos1);
        }

        return ret;
    }

    static public char getChar(String key, Properties configuration)
    {
        String str = configuration.getProperty(key);

        if (str == null)
        {
            criticalError("key `" + key + "' not found");
        }

        if (str.length() != 1)
        {
            criticalError("key `" + key + "' must have one char value");
        }
        return str.charAt(0);
    }

    static public boolean isTrue(String key, Properties configuration)
    {
        String val = configuration.getProperty(key).trim();
        if (val == null)
        {
            criticalError("key `" + key + "' must be defined");
        }

        if (val.equalsIgnoreCase("yes") || val.equalsIgnoreCase("on")
                || val.equalsIgnoreCase("true")
                || val.equalsIgnoreCase("rulez")
                || val.equalsIgnoreCase("enable"))
        {
            return true;
        }

        if (val.equalsIgnoreCase("no") || val.equalsIgnoreCase("off")
                || val.equalsIgnoreCase("false")
                || val.equalsIgnoreCase("suxx")
                || val.equalsIgnoreCase("disable"))
        {
            return false;
        }

        criticalError("key `"
                + key
                + "' must be a logical value: yes/on/true/enable/rulez or no/off/false/disable/suxx ");
        return false;
    }

    public static int getInt(String key, Properties config)
    {
        try
        {
            String v = config.getProperty(key);
            if (v != null)
            {
                return Integer.parseInt(v);
            } else
            {
                criticalError("key `" + key + "' must be defined");
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a integer value");
        }

        return 0;
    }

    public static long getLong(String key, Properties config)
    {
        try
        {
            String v = config.getProperty(key);
            if (v != null)
            {
                return Long.parseLong(v);
            } else
            {
                criticalError("key `" + key + "' must be defined");
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a long value");
        }

        return 0;
    }

    public static double getDouble(String key, Properties config)
    {
        try
        {
            String v = config.getProperty(key);
            if (v != null)
            {
                return Double.parseDouble(v);
            } else
            {
                criticalError("key `" + key + "' must be defined");
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a integer value");
        }

        return 0;
    }

    public static ArrayList<Double> getDoubleArray(String key, Properties config)
    {
        try
        {
            String v = getAndTrim(key, config);
            if (v != null)
            {
                ArrayList<Double> list = new ArrayList<Double>();
                String[] tt = v.split(" ");
                for (int i = 0; i < tt.length; i++)
                {
                    double d = Double.parseDouble(tt[i]);
                    list.add(d);
                }
                return list;
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a integer value");
        }

        return null;
    }

    // returns value in seconds
    static public int getTimeValue(String key, Properties configuration)
    {
        int mul = 0;
        String val = configuration.getProperty(key);
        if (val == null)
        {
            criticalError("key `" + key + "' must be defined");
        }
        int len = val.length() - 1;

        if (len < 0)
        {
            criticalError("key `" + key + "' cannot be empty");
        }

        char ch = val.charAt(len);

        if (ch == 'h' || ch == 'H')
        {
            mul = 3600;
        } else if (ch == 'm' || ch == 'M')
        {
            mul = 60;
        } else if (ch == 's' || ch == 'S')
        {
            mul = 1;
        }

        if (mul != 0 && len > 0)
        {
            val = val.substring(0, len);
        } else
        {
            mul = 1;
        }

        try
        {
            return Integer.parseInt(val) * mul;
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a interval value");
        }

        return 0;
    }

    public static ArrayList<Integer> getIntArray(String key, Properties config)
    {
        try
        {
            String v = getAndTrim(key, config);
            if (v != null)
            {
                ArrayList<Integer> list = new ArrayList<Integer>();
                String[] tt = v.split(" ");
                for (int i = 0; i < tt.length; i++)
                {
                    int d = Integer.parseInt(tt[i]);
                    list.add(d);
                }
                return list;
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a integer value");
        }

        return null;
    }
    public static String getFileExtension(String name) {

        int lastIndexOf = name.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // empty extension
        }
        return name.substring(lastIndexOf);
    }
    public static ArrayList<String> getStrArray(String key, Properties config)
    {
        try
        {
            String v = getAndTrim(key, config);
            if (v != null)
            {
                ArrayList<String> list = new ArrayList<String>();
                String[] tt = v.split(" ");
                for (int i = 0; i < tt.length; i++)
                {

                    list.add(tt[i]);
                }
                return list;
            }
        } catch (NumberFormatException exc)
        {
            criticalError("key `" + key + "' must be a integer value");
        }

        return null;
    }

    public static String set2str(Set<Integer> indexSet)
    {
        String key_ = indexSet.toString();
        key_ = key_.replace("[", "").replace("]", "");
        return key_.replace(",", "_").replace(" ", "");
    }

    /**
     *
     * @param indexStr delimited string of integers (without preffix '[' and suffix']')
     * @param delimiter if null used a default one=','
     * @return
     */
    public static TreeSet<Integer> str2set(String indexStr,String delimiter)
    {
        if (delimiter==null) delimiter=",";
        String[] tt = indexStr.split(delimiter);
        TreeSet<Integer> res = new TreeSet<>();
        for (int ii = 0; ii < tt.length; ii++)
        {
            res.add(Integer.parseInt(tt[ii].trim()));
        }
        return res;
    }

    /**
     *
     * @param value_str - formated as Map.toString without preffix '[' and suffix']'
     * @return
     */
    public static Map<Integer, Integer> str2map(String value_str)
    {
        String[] tt = value_str.split(",");
        Map<Integer, Integer> res = new TreeMap<>();
        for (int ii = 0; ii < tt.length; ii++)
        {
            String[] ttt_i = tt[ii].split("=");
            int nodeIndex = Integer.parseInt(ttt_i[0].trim());
            int nodeDistance = Integer.parseInt(ttt_i[1].trim());
            res.put(nodeIndex, nodeDistance);
        }
        return res;
    }

    /**
     * estimates difference between 2 maps with non-negative integer values
     * @param dMap0
     * @param dMap1
     * @param Epsilon defines criteria
     * @return
     */
    static int difference(Map<Integer, Integer> dMap0, Map<Integer, Integer> dMap1, double Epsilon)
    {
        Set<Integer> support = new TreeSet<>();
        support.addAll(dMap0.keySet());
        support.addAll(dMap1.keySet());
        double difference = 0;
        for (int index : support)
        {
            int x = 1;
            int y = 1;
            if (dMap0.containsKey(index))
            {
                x = dMap0.get(index);
            }
            if (dMap1.containsKey(index))
            {
                y = dMap1.get(index);
            }
            difference += Math.abs(x - y) / (1.0 + Math.min(x, y));
         }
        int size = support.size();
        difference /= size;
        int res = (int) Math.round(100 * difference);
        //res only if equals!
        if (res == 0 && difference > Epsilon)
        {
            res = 1;
        }

        return res;
    }

    public static class FilenameFilterPrefSuf implements FilenameFilter
    {
        String prefix;
        String suffix;

        public FilenameFilterPrefSuf(String prefix, String suffix)
        {
            this.prefix = prefix.toLowerCase();
            this.suffix = suffix.toLowerCase();
        }

        public static String getId(String fileName, String prefix, String suffix)
        {
            return fileName.substring(prefix.length() + 1, fileName.length() - suffix.length());
        }

        @Override
        public boolean accept(File dir, String name)
        {
            String correctedname = name.toLowerCase();
            return correctedname.endsWith(suffix) && correctedname.startsWith(prefix);
        }
    }

    public static class WAvgStd
    {
        public double M1;
        public double M2;
        public int Count;
        public double W;
        boolean calculated=false;

        public static double getV(String type, WAvgStd wAvgStd)
        {
            double v= wAvgStd.Avg();
            if(type.equals("std"))
            {
                v= wAvgStd.Std();
            }
            else if (type.equals("count"))
            {
                v= wAvgStd.Count;
            }
            else if (type.equals("weight"))
            {
                v= wAvgStd.Count* wAvgStd.WAvg();
            }
            else if (type.equals("weightAvg"))
            {
                v= wAvgStd.WAvg();
            }
            return v;
        }

        public void Update(WAvgStd avgStd, double shift)
        {
            if(calculated)
            {
                throw new RuntimeException("the method is not applicable for 'calculated");
            }
        /*
        TODO: check and correct
        */
            Count += avgStd.Count;
            W += avgStd.W;
            M1 += avgStd.M1+shift*avgStd.W;
            M2 += shift*shift*W+avgStd.M2;
        }
        public static String HEADERS = "Count\tweight\tavg\tstd";
        public WAvgStd(String[] tokens, int shift)
        {
            Count = Integer.parseInt(tokens[shift]);
            double w = Double.parseDouble(tokens[shift + 1]);
            W = Count*w;
            double avg = Double.parseDouble(tokens[shift + 2]);
            M1 = W * avg;
            double std = Double.parseDouble(tokens[shift + 3]);
            M2 = W * (std * std + avg * avg);
        }


        public WAvgStd()
        {

        }

        public WAvgStd(WAvgStd avgStd)
        {
            calculated=avgStd.calculated;
            Count = avgStd.Count;
            W = avgStd.W;
            M1 = avgStd.M1;
            M2 = avgStd.M2;
        }

        public boolean allowNegativeMultiplicity=true;
        public void Update(int m, double w, double value)
        {
            if(calculated)
            {
                throw new RuntimeException("the method is not applicable for 'calculated");
            }
            if(!allowNegativeMultiplicity&&m<0)
            {
                throw new RuntimeException("m<0");
            }

            Count+=m;
            if (!allowNegativeMultiplicity&&m * w < Epsilon8) return;
            W += m*w;
            M1 += m*w*value;
            M2 +=m*w* value * value;
        }
        public void Update(WAvgStd avgStd)
        {
            boolean this_calc_state=calculated;
            boolean new_calc_state=calculated;
            calcState(false);
            avgStd.calcState(false);
            Count += avgStd.Count;
            W += avgStd.W;
            M1 += avgStd.M1;
            M2 += avgStd.M2;

            calcState(this_calc_state);
            avgStd.calcState(new_calc_state);
        }
        public double Avg()
        {
            if(calculated)
            {
                return M1;
            }
            if (W < Epsilon6) return 0;
            return M1 / W;
        }

        public double L2Norm()
        {
            if(calculated)
            {
                double res= M2*M2+M1*M1;
                res= Math.sqrt(res);
                return res;
            }
            if (W < Epsilon6) return 0;
            double res = M2 / W;
            res= Math.sqrt(res);
            return res;
        }
        public double Std()
        {
            if(calculated)
            {
                return M2;
            }
            if (Count < 2) return 0;
            if (W < Epsilon6) return 0;
            double std = M2 / W;
            double avg = Avg();
            std -= avg * avg;

            if(std<-Epsilon5)
            {
                //throw new RuntimeException("std<-Epsilon5");
                //System.out.println("std<-Constants.Epsilon5- instead throw Exception std="+std);
                return 0;

            }
            std = Math.sqrt(Math.max(0, std));
            return std;
        }
        public double WAvg()
        {
            if(calculated)
            {
                return W;
            }
            if (Count < 1) return 0;
            return W/Count;
        }
        public double R()
        {
            return WAvg()*Count;
        }
        @Override
        public String toString()
        {
            double avg = Avg();
            double std = Std();
            double wAvg = WAvg();
            return String.format("%d\t%.3f\t%.5f\t%.5f", Count, wAvg, avg, std);

        }
        public void calcState(boolean state)
        {
            if(calculated==state) return;
            if(state==true)
            {
                double avg = Avg();
                double std = Std();
                double wAvg = WAvg();
                M1 = avg;
                M2 = std;
                W = wAvg;
                calculated = true;
            }
            else
            {
                W*=Count;
                M2=M2*M2+M1*M1;
                M2*=W;
                M1*=W;
                calculated= false;
            }
        }

        public static void main(String[] args) throws IOException
        {
            WAvgStd wAvgStd= new WAvgStd();
            for(int i=0; i<10; i++)
            {
                wAvgStd.Update(1,1,i);
            }
            System.out.println(HEADERS);
            System.out.println(wAvgStd+"\t"+wAvgStd.W);
            wAvgStd.calcState(true);
            System.out.println(wAvgStd+"\t"+wAvgStd.W);
            wAvgStd.calcState(false);
            System.out.println(wAvgStd+"\t"+wAvgStd.W);
            wAvgStd.calcState(true);
            System.out.println(wAvgStd+"\t"+wAvgStd.W);
        }
    }

    public static ArrayList<Map> parseEvJson(String ev_file) throws IOException, ParseException
    {
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(ev_file));
        System.out.println(obj);
        JSONObject jsonObject = (JSONObject) obj;
        JSONObject evidences = (JSONObject)jsonObject.get("evidences");
        System.out.println(evidences);
        Map hardEvidences=null;
        Map softEvidences =null;
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
        ArrayList<Map> res=new ArrayList<>();
        res.add(hardEvidences);
        res.add(softEvidences);
        return res;
    }

    public static String map2str(Map map)
    {
        String res="";
        for( Object obj:map.keySet())
        {
            if (res.length()>0)
            {
                res+=";";
            }
            res+= String.format("%s=%s", obj,map.get(obj));
        }
        return res;
    }
}
