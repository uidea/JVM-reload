package wang.ming15.reloadAgent;

import com.sun.tools.attach.VirtualMachine;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

public class Main {
	public static void main(String[] args) throws Exception {
		String name = ManagementFactory.getRuntimeMXBean().getName();
		String pid = name.split("@")[0];
		System.out.println(pid);
		VirtualMachine vm = VirtualMachine.attach(pid);
		for (int i = 0; i < 100; i++) {
			TimeUnit.SECONDS.sleep(10);
            vm.loadAgent("D:\\ming\\test\\target\\test-1.0-SNAPSHOT.jar");
			System.out.println("Load Agent Over!!!");
		}
	}
}
