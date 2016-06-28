package wang.ming15.reloadAgent;

import com.sun.tools.attach.VirtualMachine;
import org.apache.log4j.Logger;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

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
public class Agent {
    private static final Logger logger = Logger.getLogger(Agent.class);

    private static Instrumentation instrumentation;

	// 已经加载进内存的业务类, 只对业务逻辑类进行热加载
    private static final Map<String, Class> allLoadClassesMap = new HashMap<>();
	// 将已经加载过的类缓存起来, 避免没有修改过的类再次被重新加载
	private static final Map<String, String> classMD5 = new ConcurrentHashMap<>();

    public static void agentmain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
        try {
            Properties properties = new Properties();
            properties.load(new FileInputStream(new File("reload.properties")));
            String watchPath = properties.getProperty("watchPath");
            String fileName = properties.getProperty("fileName");
            String prfixName = properties.getProperty("prfixName");

            Class[] allLoadClasses = instrumentation.getAllLoadedClasses();
            for (Class loadedClass : allLoadClasses) {
                if (loadedClass.getName().startsWith(prfixName)) {
                    allLoadClassesMap.put(loadedClass.getName(), loadedClass);
                }
            }

            CodeReloader.newOne().startWatchFileChange(watchPath, fileName, prfixName);
        } catch (IOException e) {
            logger.error("", e);
        }

    }

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
					byte[] bytes = new byte[input.available()];
					input.read(bytes);
                    redefineClassesFromBytes(bytes, className);
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
    public static void loadFromZipFile(String jarPath) {
		try {
			String name = ManagementFactory.getRuntimeMXBean().getName();
			String pid = name.split("@")[0];
			System.out.println(pid);
			VirtualMachine vm = VirtualMachine.attach(pid);
			for (int i = 0; i < 100; i++) {
				TimeUnit.SECONDS.sleep(10);
				vm.loadAgent("D:\\ming\\test\\target\\test-1.0-SNAPSHOT.jar");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		Map<String, byte[]> loadClass = new HashMap<>();
		try(InputStream in = new BufferedInputStream(new FileInputStream(new File(jarPath)));
            ZipInputStream zin = new ZipInputStream(in);) {
            ZipEntry ze;
            while ((ze = zin.getNextEntry()) != null) {
                if (ze.isDirectory()) {
                    // TODO 检查是否还有其他操作要做
                } else {
                    if (ze.getSize() > 0) {
                        String fileName = ze.getName();
                        if (!fileName.endsWith(".class")) {
                            continue;
                        }
						try(ZipFile zf = new ZipFile(jarPath); InputStream input = zf.getInputStream(ze);) {
							if (input == null) {
								logger.error("Code Reload cant find file : " + fileName);
								continue;
							}
							byte[] bytes = new byte[input.available()];
							input.read(bytes);
							if (canLoad(bytes, fileName)) {
								loadClass.put(fileName, bytes);
							}
						}
                    }
                }

            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

		for (Map.Entry<String, byte[]> entry : loadClass.entrySet()) {
            redefineClassesFromBytes(entry.getValue(), entry.getKey());
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
    private static void redefineClassesFromBytes(byte[] bytes, String fileName) {
        try {
        	String className = getClassName(fileName);
            logger.info("Start Hot Reload Class : " + fileName + "  (" + className + ")");
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

    private static String md5(byte[] bytes) throws NoSuchAlgorithmException {
        StringBuffer stringBuffer = new StringBuffer();
        MessageDigest md5 = MessageDigest.getInstance("md5");
        byte[] a = md5.digest(bytes);
        for (byte b : a) {
            stringBuffer.append(b);
        }
        return stringBuffer.toString();
    }
}
