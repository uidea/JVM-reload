package wang.ming15.hotreload;

/**
 * Created by wangming on 2016/7/1.
 */
public enum Printer {

	INSTANCE;

	private int id = 123111111;
	public void print() {
		System.out.println(id);
	}
}
