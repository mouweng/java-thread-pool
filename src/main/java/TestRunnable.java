import java.util.HashSet;

/**
 * @author: wengyifan
 * @description:
 * @date: 2022/4/11 4:20 下午
 */
public class TestRunnable extends Thread{
    public HashSet<Runnable> s = new HashSet<>();

    public TestRunnable() {
        for (int i = 0 ;i < 5; i ++) {
            int j = i;
            s.add(()->{
                System.out.println(j);
            });
        }
    }

    @Override
    public void run() {
        for (Runnable r : s) {
            r.run();
        }
    }

    public static void main(String[] args) {
        TestRunnable t = new TestRunnable();
        t.start();
    }
}
