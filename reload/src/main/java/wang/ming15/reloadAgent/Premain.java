package wang.ming15.reloadAgent;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;

/**
 * 实现服务器局部代码热加载功能
 *      目前只支持方法体代码热更以及对属性值的改变
 *      但是不能修改类的继承结构, 不能修改方法签名, 不能增加删除方法以及属性成员
 *
 *  使用方法
 *      java -javaagent:D:\premain\target\agent-1.0-SNAPSHOT.jar -cp .;./* MainServerStart
 *      只需要将该项目打包出来然后参照上面的例子进行代理处理就好了, 然后正常启动游戏服就好
 *
 * Created by wangming on 2016/4/6.
 */
public class Premain {
    private static final Logger logger = Logger.getLogger(Premain.class);

    private static Instrumentation instrumentation;
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;

        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(new File("reload.properties")));
            String watchPath = properties.getProperty("watchPath");
            String fileName = properties.getProperty("fileName");
            String prfixName = properties.getProperty("prfixName");
            CodeReloader.newOne().startWatchFileChange(watchPath, fileName, prfixName);
        } catch (IOException e) {
            logger.error("", e);
        }
    }

    // 将已经加载过的类缓存起来, 避免没有修改过的类再次被重新加载
    private static final Map<String, String> classMD5 = new ConcurrentHashMap<>();
	private static int classSize = 0;

    /**
     * 遍历某个目录加载所有的class文件
     * @param directionPath
     */
    public static void loadFromDirection(String directionPath) {
        loadFromDirection(new File(directionPath), "");
    }

    private static void loadFromDirection(File dir, String parantName) {
        try {
            for (File file : dir.listFiles()) {
                if (file.isFile() && !file.getName().endsWith(".class")) {
                    continue;
                }
                if (file.isDirectory()) {
                    String fileName = file.getName();
                    if (parantName != null && !parantName.equals("")) {
                        fileName = parantName + "." + fileName;
                    }
                    loadFromDirection(file, fileName);
                    continue;
                }
                try(InputStream input = new FileInputStream(file);) {
                    String fileName = file.getPath();
                    String className = findClassName(fileName);
                    if (parantName != null && !parantName.equals("")) {
                        className = parantName + "." + className;
                    }
                    redefineClassesFromBytes(input, className, null);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 从jar包或者ZIP里加载所有的class文件
     * @param jarPath
     */
    public static void loadFromZipFile(String jarPath, String prfixName) {
		Class[] allLoadClasses = instrumentation.getAllLoadedClasses();
		Map<String, Class> allLoadClassesMap = new HashMap<>(classSize);
		for (Class loadedClass : allLoadClasses) {
			if (loadedClass.getName().startsWith(prfixName)) {
				allLoadClassesMap.put(loadedClass.getName(), loadedClass);
			}
		}
		// 加载的类我们不会主动去卸载它, 因此, 我们记录下来上次更新时的类的数量, 下次就根据这个数量直接分配, 避免动态扩容
		classSize = allLoadClassesMap.size();

		try(InputStream in = new BufferedInputStream(new FileInputStream(new File(jarPath)));
            ZipInputStream zin = new ZipInputStream(in);) {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    // TODO 检查是否还有其他操作要做
                } else {
                    long size = ze.getSize();
                    if (size > 0) {
                        String fileName = ze.getName();
                        if (!fileName.endsWith(".class")) {
                            continue;
                        }
                        ZipFile zf = new ZipFile(jarPath);
                        InputStream input = zf.getInputStream(ze);
                        if (input == null) {
                            logger.error("Code Reload cant find file : " + fileName);
                            continue;
                        }
                        redefineClassesFromBytes(input, fileName, allLoadClassesMap);
                        input.close();
                        zf.close();
                    }
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static String findClassName(String fileName) {
        int idx = fileName.lastIndexOf("\\");
        fileName = fileName.substring(idx + 1);
        fileName = fileName.split("\\.class")[0];
        return fileName;
    }

    /* 使用instrumentation将读取的class byte数组加载进虚拟机
     */
    private static void redefineClassesFromBytes(InputStream input, String fileName, Map<String, Class> allLoadClassesMap) {
        try {
        	String className = getClassName(fileName);
            logger.info("Start Hot Reload Class : " + fileName + "  (" + className + ")");
	        byte[] bytes = new byte[input.available()];
    	    input.read(bytes);
			Class loadedClass = allLoadClassesMap.get(className);
			if (loadedClass != null) {
				instrumentation.redefineClasses(new ClassDefinition(loadedClass, bytes));
			}
        } catch (final Exception e) {
            logger.error("Code Reload Failed : " + fileName, e);
        } catch (Error error) {
			logger.error("Code Reload Failed : " + fileName, error);
		}
    }

    private static String getClassName(String fileName) {
        fileName = fileName.split("\\.class")[0];
        fileName = fileName.replace("\\\\", ".");
        fileName = fileName.replace("/", ".");
        return fileName;
    }

    private static boolean canLoad(byte[] bytes, String fileName) throws NoSuchAlgorithmException {
        String md5 = md5(bytes);
        String oldMD5 = classMD5.get(fileName);
        if (oldMD5 == null) {
            classMD5.put(fileName, md5);
            return true;
        } else if (oldMD5.equals(md5)) {
            return false;
        } else {
            classMD5.put(fileName, md5);
            return true;
        }
    }

    private static String md5(byte[] byte1) throws NoSuchAlgorithmException {
        StringBuffer stringBuffer = new StringBuffer();
        MessageDigest md5 = MessageDigest.getInstance("md5");
        byte[] a = md5.digest(byte1);
        for (byte b : a) {
            stringBuffer.append(b);
        }
        return stringBuffer.toString();
    }
}
