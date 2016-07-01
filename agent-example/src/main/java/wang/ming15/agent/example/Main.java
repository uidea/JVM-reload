package wang.ming15.agent.example;

import java.lang.management.ManagementFactory;

import com.sun.tools.attach.VirtualMachine;

/**
 * Created by wangming on 2016/7/1.
 */
public class Main {

	public static void main(String[] args) {
		String jarFile = "";
		try {
			String name = ManagementFactory.getRuntimeMXBean().getName();
			String pid = name.split("@")[0];
			System.out.println("pid : " + pid);
			VirtualMachine vm = VirtualMachine.attach(pid);
			vm.loadAgent(jarFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
