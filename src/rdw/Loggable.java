package rdw;

// TODO: it works only if used log4j-1.2.17.jar (UnBBayes Distributive). Should be corrected for different versions
//  log4j
import org.apache.log4j.PropertyConfigurator;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.*;

import static rdw.Util.criticalError;
import static rdw.Util.getInt;

public abstract class Loggable
{
	static class LogContainer
	{
		String name;
		String template;
		String Level;
		int count;
		int limit; 
		
	}
	public final static String FORMAT_DATA_DAY="yyyy/MM/dd";
	public final static String FORMAT_DATA_MIN="yy-MM-dd HH:mm";
    public final static String FORMAT_DATA_SEC="yy-MM-dd HH:mm:ss";
    public final static String FORMAT_DATA_MS="yy-MM-dd HH:mm:ss.SS";
	public final static String FORMAT_DATA_MIN1="yy-MM-dd-HH-mm";
    public final static SimpleDateFormat simpleDateFormatDAY= new SimpleDateFormat(FORMAT_DATA_DAY);
    public final static SimpleDateFormat simpleDateFormatSEC= new SimpleDateFormat(FORMAT_DATA_SEC);
    public final static SimpleDateFormat simpleDateFormatMS= new SimpleDateFormat(FORMAT_DATA_MS);
    public final static SimpleDateFormat simpleDateFormatMIN= new SimpleDateFormat(FORMAT_DATA_MIN);
    public final static SimpleDateFormat simpleDateFormatMIN1= new SimpleDateFormat(FORMAT_DATA_MIN1);

	static class OneLineSimpleFormatter extends Formatter
    {
        /**
         * Format the given LogRecord.
         *
         * @param record the log record to be formatted.
         * @return a formatted log record
         */
        public synchronized String format(LogRecord record)
        {
            StringBuilder sb = new StringBuilder();
            // Minimize memory allocations here.
            long mills=record.getMillis();
            sb.append(simpleDateFormatSEC.format(mills));
            sb.append("\t");
            sb.append(mills/1000+"\t" +mills%1000);
            sb.append("\t");
            if (record.getSourceClassName() != null)
            {
                sb.append(record.getSourceClassName());
            }
            else
            {
                sb.append(record.getLoggerName());
            }
             sb.append(":");
            if (record.getSourceMethodName() != null)
            {
                sb.append(record.getSourceMethodName());
            }
            else
            {
                sb.append("common");
            }
            sb.append("\t");
            String message = formatMessage(record);
            sb.append(record.getLevel().getLocalizedName());
            sb.append("\t");
            sb.append(message);
            sb.append("\r\n");
            if (record.getThrown() != null)
            {
                try
                {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    sb.append(sw.toString());
                }
                catch (Exception ex)
                {
                }
            }
            return sb.toString();
        }
    }

	static Map<String,LogContainer> name2container = new HashMap<String,LogContainer>();

	static void initLogers(long taskId)
	{
		for(String name:name2container.keySet())
		{
			LogContainer container=name2container.get(name);
			initLogger(name,container.template,container.limit,container.count,container.Level,taskId);
		}

	}
	
	static boolean initLogger(String logname, String pattern, int limit, int count, String levelName,long taskId)
    {
		Logger log_ =null;
		File logfile=new File(pattern);
		File directory = new File(logfile.getParentFile().getAbsolutePath());
		directory.mkdirs();
        try
        {
            log_ = Logger.getLogger(logname);
            //"STEP.txt", Units.MB_BT, 10

            FileHandler handler = new FileHandler(pattern, limit * 1024*1024, count, true);
            handler.setFormatter(new OneLineSimpleFormatter());
            log_.addHandler(handler);
            Level level = Level.parse(levelName);
            if (level == null)
            {
                System.out.println(String.format("!Unknown level %s for logger %s;  please check your cfg file ", levelName,logname));
                return false;
            }
            log_.setLevel(Level.parse(levelName));
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.out.println(String.format("!!!!!!! %s log is not initialized !!!!!", logname));
            return false;
        }
        log_.info(String.format("logger %s starts for task=%X and  pattern: %s", logname,taskId,pattern));
        return true;
    }
    public static Properties configuration;
    public static long taskId;
    public static StringBuffer cfgBuffer = new StringBuffer(1024);
    protected static boolean exitOnCritical = true;
    static public Logger log_task;// = Logger.getLogger("tasks");
    static public void init(String cfg_file)
    {
        taskId = (long) (Long.MAX_VALUE * Math.random());
        cfgBuffer.append(String.format("taskId %X", taskId));
        configuration = new Properties();
        try
        {
            configuration.load(new FileInputStream(cfg_file));
        } catch (IOException ex)
        {
            ex.printStackTrace();
            return;
        }
        String prefix_log = "log.";
        int prefix_log_len = prefix_log.length();
        System.out.println("****************** " + cfg_file
                + "********************");
        for (String key : configuration.stringPropertyNames())
        {
            String ss = key + " => " + configuration.get(key);
            System.out.println(ss);
            cfgBuffer.append("\r\n" + ss);
            String entry = key.trim();
            try
            {
                int lastDotPos = entry.lastIndexOf('.');
                if (entry.startsWith(prefix_log))
                {
                    if (lastDotPos < prefix_log_len + 2)
                    {
                        criticalError("error in cfg file: there is no container name "
                                + entry);
                    }
                    String name = entry.substring(prefix_log_len, lastDotPos);
                    if (!name2container.containsKey(name))
                    {
                        LogContainer container = new LogContainer();
                        container.name = name;
                        name2container.put(name, container);
                    }
                    LogContainer container = name2container.get(name);
                    String attribute = entry.substring(lastDotPos + 1);
                    if (attribute.compareToIgnoreCase("level") == 0)
                    {

                        container.Level = configuration.getProperty(entry);
                    } else if (attribute.compareToIgnoreCase("limit") == 0)
                    {
                        container.limit = getInt(entry, configuration);
                    } else if (attribute.compareToIgnoreCase("template") == 0)
                    {
                        container.template = configuration.getProperty(entry);

                    } else if (attribute.compareToIgnoreCase("count") == 0)
                    {
                        container.count = getInt(entry, configuration);
                    }
                }
            } catch (Exception e1)
            {
                e1.printStackTrace();
                criticalError("error in cfg file: parsing " + entry + " "
                        + e1.toString());
            }
        }
        System.out
                .println("**To change the configuration please edit cfg-file***");

        initLogers(taskId);
        log_task = Logger.getLogger("tasks");
        log_task.setUseParentHandlers(false);
        log_task.info(cfgBuffer.toString());
        if(configuration.containsKey("log4jConfPath"))
        {
            String log4jConfPath = configuration.getProperty("log4jConfPath");
            PropertyConfigurator.configure(log4jConfPath);
        }

    }
    static public Logger log_algo = Logger.getLogger("algo");
    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");
        String cfg_file = args[0];
        init(cfg_file);

        log_algo.setUseParentHandlers(false);
        log_algo.info("Hello world!");
    }
}

