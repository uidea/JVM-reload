package wang.ming15.reloadExample;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import wang.ming15.reloadAgent.Premain;

public class TestReload {

	public static void main(String[] args) throws Exception {
        watchFile();
    }

    public static void watchFile() throws IOException {
        FileAlterationObserver observer = new FileAlterationObserver("D:\\ming\\test\\target\\");
        observer.addListener(new FileAlterationListenerAdaptor() {

            @Override
            public void onFileCreate(File file) {
                if (!"test-1.0-SNAPSHOT.jar".equals(file.getName())) {
                    return;
                }
                Premain.loadFromZipFile(file.getAbsolutePath());
                new TestReload().printNewTime();
            }

            @Override
            public void onFileDelete(File file) {
            }

            @Override
            public void onDirectoryCreate(File dir) {
            }

            @Override
            public void onDirectoryDelete(File dir) {
            }
        });

        try {
            FileAlterationMonitor monitor = new FileAlterationMonitor(500, observer);
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void fromJar() throws InterruptedException{
        for (int i = 0; i < 300; i++) {
            Premain.loadFromZipFile("D:\\abc\\target\\abc-1.0-SNAPSHOT.jar");
            TimeUnit.SECONDS.sleep(5);
        }
    }

    public static void fromDirection() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Premain.loadFromDirection("D:\\ming\\test\\target\\classes");
            new TestReload().printNewTime();
            TimeUnit.SECONDS.sleep(5);
        }
    }

    public void printNewTime() {
        System.out.println(id);
    }

    public int id = 9;
}
