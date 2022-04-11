package V1;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author: wengyifan
 * @description: 不带策略的线程池
 * @date: 2022/4/11 2:27 下午
 */

@Slf4j(topic = "V1.TestPool")
public class TestPool {
    public static void main(String[] args) {
        ThreadPool threadPool = new ThreadPool(2, 10, 1000, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 15; i ++) {
            int j = i;
            threadPool.execute(()->{
                log.debug("{}", j);
            });
        }
    }
}

@Slf4j(topic = "V1.ThreadPool")
class ThreadPool {
    // 自己定义的任务阻塞队列
    private  BlockingQueue<Runnable> taskQueue;
    // 线程集合
    private HashSet<Worker> workers = new HashSet<Worker>();
    // 核心线程数
    private int coreSize;
    // 获取任务的超时时间
    private long timeout;
    private TimeUnit timeUnit;

    public ThreadPool(int coreSize, int queueCapacity, long timeout, TimeUnit timeUnit) {
        this.taskQueue = new BlockingQueue<>(queueCapacity);
        this.coreSize = coreSize;
        this.timeout = timeout;
        this.timeUnit = timeUnit;
    }

    public void execute(Runnable task) {
        // workers线程不安全，所以用一个synchronized保证安全
        synchronized (workers) {
            // 当任务数没有超过coreSize时，直接交给Worker对象执行
            if (workers.size() < coreSize) {
                Worker worker = new Worker(task);
                log.info("新增 worder {}, 新增 task {}", worker, task);
                workers.add(worker);
                worker.start();
            } else {
                // 如果任务数超过coreSize,加入任务队列暂存
                taskQueue.offer(task);
                // log.info("加入任务队列 task {}", task);
            }
        }
    }

    class Worker extends Thread {
        private Runnable task;

        public Worker(Runnable task) {
            this.task = task;
        }

        public void run() {
            // 执行任务
            // 1) 当task不为空, 执行任务
            // 2) 当task执行完毕，接着从任务队列获取任务
            while (task != null || (task = taskQueue.poll(1000, TimeUnit.MILLISECONDS)) != null) {
                try {
                    log.info("正在执行... {}", task);
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    task = null;
                }
            }
            synchronized (workers) {
                log.info("worker被移除... {}", this);
                workers.remove(this);
            }
        }
    }
}


// 阻塞队列
@Slf4j(topic = "V1.BlockingQueue")
class BlockingQueue<T> {
    // 任务队列
    private Deque<T> queue = new ArrayDeque<T>();
    // 锁
    private ReentrantLock lock = new ReentrantLock();
    // 生产者条件变量
    private Condition fullWaitSet = lock.newCondition();
    // 消费者条件变量
    private Condition emptyWaitSet = lock.newCondition();
    // 容量
    private int capacity;

    public BlockingQueue(int capacity) {
        this.capacity = capacity;
    }
    // 阻塞获取
    public T poll() {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                try {
                    emptyWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            T element = queue.removeFirst();
            fullWaitSet.signal();
            return element;
        } finally {
            lock.unlock();
        }
    }

    // 带超时的阻塞获取
    public T poll(long timeout, TimeUnit unit) {
        lock.lock();
        try {
            // 将 timeout 统一转换为 纳秒
            long nanos = unit.toNanos(timeout);
            while (queue.isEmpty()) {
                try {
                    if (nanos <= 0) {
                        return null;
                    }
                    // 返回的是剩余的时间, 无需永久的等待
                    nanos = emptyWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            T element = queue.removeFirst();
            fullWaitSet.signal();
            return element;
        } finally {
            lock.unlock();
        }
    }

    // 阻塞添加
    public void offer(T element) {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                try {
                    log.debug("等待加入任务队列{}...", element);
                    fullWaitSet.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("加入任务队列{}", element);
            queue.addLast(element);
            emptyWaitSet.signal();
        } finally {
            lock.unlock();
        }
    }
    // 带超时的阻塞添加
    public boolean offer(T task, long timeout, TimeUnit unit) {
        lock.lock();
        try {
            long nanos = unit.toNanos(timeout);
            while (queue.size() == capacity) {
                try {
                    log.debug("等待加入任务队列{}...", task);
                    if (nanos <= 0) {
                        return false;
                    }
                    nanos = fullWaitSet.awaitNanos(nanos);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.debug("加入任务队列{}", task);
            queue.addLast(task);
            emptyWaitSet.signal();
            return true;
        } finally {
            lock.unlock();
        }
    }

    // 获取大小
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }
}