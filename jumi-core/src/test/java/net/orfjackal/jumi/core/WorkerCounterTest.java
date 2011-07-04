// Copyright © 2011, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package net.orfjackal.jumi.core;

import net.orfjackal.jumi.core.actors.Actors;
import net.orfjackal.jumi.core.dynamicevents.DynamicListenerFactory;
import org.junit.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

public class WorkerCounterTest {

    private static final int TIMEOUT = 1000;

    private final Actors actors = new Actors(DynamicListenerFactory.factoriesFor());
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    @After
    public void shutdownThreadPool() throws InterruptedException {
        threadPool.shutdown();
        actors.shutdown(TIMEOUT);
    }

    @Test(timeout = TIMEOUT)
    public void notifies_when_all_workers_have_finished_executing() throws InterruptedException {
        final BlockingQueue<String> events = new LinkedBlockingQueue<String>();

        WorkerCounterListener listener = new WorkerCounterListener() {
            public void onAllWorkersFinished() {
                events.add("all finished");
            }
        };
        WorkerCounter workers = new WorkerCounter(listener, actors, threadPool);
        workers.startWorker(new Runnable() {
            public void run() {
                events.add("run worker");
            }
        });

        assertThat("1st event", events.take(), is("run worker"));
        assertThat("2nd event", events.take(), is("all finished"));
        assertThat("no more events", events.isEmpty(), is(true));
    }

    @Test(timeout = TIMEOUT)
    public void workers_are_run_each_in_their_own_thread() throws InterruptedException {
        final CountDownLatch allFinished = new CountDownLatch(1);
        final AtomicReference<Thread> workerThread = new AtomicReference<Thread>();

        WorkerCounterListener listener = new WorkerCounterListener() {
            public void onAllWorkersFinished() {
                allFinished.countDown();
            }
        };
        WorkerCounter workers = new WorkerCounter(listener, actors, threadPool);
        workers.startWorker(new Runnable() {
            public void run() {
                workerThread.set(Thread.currentThread());
            }
        });
        allFinished.await();

        Thread thread = workerThread.get();
        assertThat("worker was not run", thread, is(notNullValue()));
        assertThat("worker was run in current thread", thread, is(not(Thread.currentThread())));
    }

    @Test
    public void notifies_when_all_workers_have_finished_executing_even_if_a_worker_throws_an_exception() throws InterruptedException {
        final CountDownLatch allFinished = new CountDownLatch(1);

        WorkerCounterListener listener = new WorkerCounterListener() {
            public void onAllWorkersFinished() {
                allFinished.countDown();
            }
        };
        WorkerCounter workers = new WorkerCounter(listener, actors, threadPool);
        workers.startWorker(new Runnable() {
            public void run() {
                throw new AssertionError("dummy exception");
            }
        });

        assertTrue("did not receive notification", allFinished.await(TIMEOUT, TimeUnit.MILLISECONDS));
    }
}