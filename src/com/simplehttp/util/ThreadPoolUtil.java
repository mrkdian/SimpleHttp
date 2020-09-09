package com.simplehttp.util;

import cn.hutool.log.LogFactory;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

public class ThreadPoolUtil {
	private static ThreadPoolExecutor threadPool = new ThreadPoolExecutor(20, 100, 60, TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(10));

	public static void run(Runnable r) {
		threadPool.execute(r);
	}

	private static OrderingExecutor orderThreadPool = new OrderingExecutor(threadPool);

	public static void runByOrder(Runnable r, Object key) {
	    orderThreadPool.execute(r, key);
    }

    public static void removeKey(Object key) {
		orderThreadPool.removeKey(key);
	}
}
/**
 * This Executor warrants task ordering for tasks with same key (key have to implement hashCode and equal methods correctly).
 */
class OrderingExecutor implements Executor{

	private final Executor delegate;
	private final Map<Object, LinkedList<Runnable>> keyedTasks = new ConcurrentHashMap<>();

	public OrderingExecutor(Executor delegate){
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable task) {
		// task without key can be executed immediately
		delegate.execute(task);
	}

	// Producer
	public void execute(Runnable task, Object key) {
		if (key == null){ // if key is null, execute without ordering
			execute(task);
			return;
		}

		LinkedList<Runnable> dependencyQueue = keyedTasks.get(key);
		if (dependencyQueue == null){
			dependencyQueue = new LinkedList<>();
			keyedTasks.put(key, dependencyQueue);
		}

		synchronized (dependencyQueue) {
			if(dependencyQueue.size() == 0) { // ProcessTask that process the queue has exited
				dependencyQueue.offer(task);
				delegate.execute(new ProcessTask(dependencyQueue));
			} else {
				dependencyQueue.offer(task);
			}
		}
	}

	// Not a good way
	public void removeKey(Object key) {
		LinkedList<Runnable> dependencyQueue = keyedTasks.get(key);
		if(dependencyQueue == null) return;
		synchronized (dependencyQueue) {
			// drain queue
			dependencyQueue.clear();
		}
		keyedTasks.remove(key);
	}

	// Consumer
	class ProcessTask implements Runnable{
		private final LinkedList<Runnable> dependencyQueue;

		public ProcessTask(LinkedList<Runnable> dependencyQueue) {
			this.dependencyQueue = dependencyQueue;
		}
		@Override
		public void run() {
			Runnable task = null;
			Runnable nextTask = null;
			synchronized (dependencyQueue) { // atomic get element and check the next element
				task = dependencyQueue.poll();
				nextTask = dependencyQueue.peek();
			}

			while(task != null) {
				try {
					task.run();
				} catch (Throwable x) {
					LogFactory.get().error(x);
				}
				if(nextTask != null) {
					synchronized (dependencyQueue) {
						task = dependencyQueue.poll();
						nextTask = dependencyQueue.peek();
					}
				} else break; // release the current thread, not block.
			}
		}
	}
}
