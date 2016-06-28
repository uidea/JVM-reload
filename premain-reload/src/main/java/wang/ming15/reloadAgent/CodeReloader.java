package wang.ming15.reloadAgent;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

/**
 * 代码热更
 * 监控某个目录,一旦发现被监控的文件发生变化则对其进行热更
 *
 * Created by wangming on 2016/4/6.
 */
public class CodeReloader {

    private static final Logger logger = Logger.getLogger(CodeReloader.class);

    private FileAlterationMonitor monitor;

	/**
	 * "D:\\ming\\test\\target\\"   "test-1.0-SNAPSHOT.jar"
	 *
	 * @param watchPath  监控路径
	 * @param fileName  排除的文件名
	 * @param prfixName  热加载文件的全限定名的前缀, 指定这个是为了只热加载业务的
	 * @throws IOException
	 */
    public void startWatchFileChange(String watchPath, String fileName) throws IOException {
        if (StringUtils.isBlank(watchPath) || StringUtils.isBlank(fileName)) {
            logger.error("watchPath : " + watchPath + ", fileName " + fileName);
            return;
        }

        FileAlterationObserver observer = new FileAlterationObserver(watchPath);
        observer.addListener(new FileAlterationListenerAdaptor() {

			@Override
			public void onFileChange(File file) {
				if (!fileName.equals(file.getName())) {
					logger.warn("File Changed, but not monitor file!!!");
					return;
				}
				logger.info("Jar File Changed, start hot reload!!!");
				Premain.loadFromZipFile(file.getAbsolutePath());
			}

		});

		logger.info("Hot Reload Monitor on " + watchPath + "/" + fileName);
        monitor = new FileAlterationMonitor(500, observer);
        try {
            monitor.start();
        } catch (Exception e) {
            logger.error("", e);
        }
    }

    public void stopWatchFileChange() {
        if (monitor == null) {
            throw new RuntimeException("监控器为空, 没有处于监视状态");
        }
        try {
            monitor.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CodeReloader newOne() {
        return new CodeReloader();
    }
}
