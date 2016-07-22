package wang.ming15.reloadAgent;

import org.apache.log4j.Logger;

import java.io.*;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.GenericSignatureFormatError;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
public class Premain {
    private static final Logger logger = Logger.getLogger(Premain.class);

    private static Instrumentation instrumentation;

	// 已经加载进内存的业务类, 只对业务逻辑类进行热加载
    private static final Map<String, Class> allLoadClassesMap = new HashMap<>();
	// 将已经加载过的类缓存起来, 避免没有修改过的类再次被重新加载
	private static final Map<String, String> classMD5 = new ConcurrentHashMap<>();

    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;
    }

    public static void cacheAllLoadedClasses(String prfixName) {
        try {
            Class[] allLoadClasses = instrumentation.getAllLoadedClasses();
            for (Class loadedClass : allLoadClasses) {
                if (loadedClass.getName().startsWith(prfixName)) {
                    allLoadClassesMap.put(loadedClass.getName(), loadedClass);
                }
            }
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    /**
     * 从jar包或者ZIP里加载所有的class文件
	 * @param reloadJarPath
	 */
    public static String loadFromZipFile(String workJarPath, String reloadJarPath, String prfixName) {
		// 将已经加载进来的类都加载一遍, 避免出现没有加载过的类也会被热更, 而发生异常
        cacheAllLoadedClasses(prfixName);

		// 将原始的jar文件读取一遍，进行md5的初始化工作, 避免第一次热更的时候将所有的类都热更
		if (classMD5.size() == 0) {
			getLoadedClass(workJarPath);
		}

		Map<String, byte[]> loadClass = getLoadedClass(reloadJarPath);

		StringBuffer stringBuffer = new StringBuffer();
		for (Map.Entry<String, byte[]> entry : loadClass.entrySet()) {
            boolean isOk = redefineClassesFromBytes(entry.getValue(), entry.getKey());
			if (!isOk) {
				stringBuffer.append(entry.getKey()).append("<br>");
			}
		}
		return stringBuffer.toString();
    }

	public static Map<String, byte[]> getLoadedClass(String jarPath) {
		Map<String, byte[]> loadClass = new HashMap<>();
		try(InputStream in = new BufferedInputStream(new FileInputStream(new File(jarPath)));
			ZipInputStream zin = new ZipInputStream(in)) {
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
						try(ZipFile zf = new ZipFile(jarPath); InputStream input = zf.getInputStream(ze);
							ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
							if (input == null) {
								logger.error("Code Reload cant find file : " + fileName);
								continue;
							}
							int b = 0;
							while ((b = input.read()) != -1) {
								byteArrayOutputStream.write(b);
							}
							byte[] bytes = byteArrayOutputStream.toByteArray();
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
		return loadClass;
	}

    /* 使用instrumentation将读取的class byte数组加载进虚拟机
     */
    private static boolean redefineClassesFromBytes(byte[] bytes, String fileName) {
		String className = getClassName(fileName);
        try {
            logger.info("Start Hot Reload Class : " + fileName + "  (" + className + ")");
	        Class loadedClass = allLoadClassesMap.get(className);
			if (loadedClass != null) {
				instrumentation.redefineClasses(new ClassDefinition(loadedClass, bytes));
			}
			return true;
        } catch (final Exception e) {
            logger.error("Code Reload Failed : " + fileName, e);
			return false;
        } catch (final GenericSignatureFormatError error) {
			logger.error("Code Reload GenericSignatureFormatError : " + fileName, error);
			return false;
		} catch (final UnsupportedClassVersionError error) {
			logger.error("Code Reload UnsupportedClassVersionError : " + fileName, error);
			return false;
		} catch (final Error error) {
			printErrorFile(bytes, fileName);
			logger.error("Code Reload Error : " + fileName + " -> " + bytes.length, error);
			return false;
		}
	}

	private static void printErrorFile(byte[] bytes, String fileName) {
		try {
			File file = new File("./errorFiles/" + fileName);
			if (!file.exists()) {
				String path = file.getAbsolutePath();
				int index = path.lastIndexOf("/");
				if (index == -1) {
					index = path.lastIndexOf("\\");
				}
				String dirPath = path.substring(0, index);
				File dir = new File(dirPath);
				if (!dir.exists()) {
					dir.mkdirs();
				}
			}
			FileOutputStream fileOutputStream = new FileOutputStream(file);
			fileOutputStream.write(bytes);
			fileOutputStream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
