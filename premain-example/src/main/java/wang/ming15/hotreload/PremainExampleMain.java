package wang.ming15.hotreload;

import java.util.concurrent.TimeUnit;

/**
 * Created by wangming on 2016/7/1.
 */
public class PremainExampleMain {
	public static void main(String[] args) throws InterruptedException {

		System.out.println("premain started!!!");
		while (true) {
			Printer.print();
			TimeUnit.SECONDS.sleep(5);
		}
	}
}
