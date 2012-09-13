// Copyright © 2011-2012, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package fi.jumi.core.output;

import org.apache.commons.io.output.WriterOutputStream;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

public class OutputCapturerTest {

    private static final long TIMEOUT = 1000;
    private final StringWriter printedToOut = new StringWriter();
    private final PrintStream realOut = new PrintStream(new WriterOutputStream(printedToOut));

    private final OutputCapturer capturer = new OutputCapturer(realOut);

    // TODO: the same tests for stderr


    // basic capturing

    @Test
    public void passes_through_stdout_to_the_real_stdout() {
        capturer.out().print("foo");
        capturer.out().write('.');

        assertThat(printedToOut.toString(), is("foo."));
    }

    @Test
    public void captures_stdout() {
        OutputListener listener = mock(OutputListener.class);

        capturer.captureTo(listener);
        capturer.out().print("foo");
        capturer.out().write('.');

        verify(listener).out("foo");
        verify(listener).out(".");
        verifyNoMoreInteractions(listener);
    }

    @Test
    public void after_starting_a_new_capture_all_new_events_to_to_the_new_output_listener() {
        OutputListener listener1 = mock(OutputListener.class, "listener1");
        OutputListener listener2 = mock(OutputListener.class, "listener2");

        capturer.captureTo(listener1);
        capturer.captureTo(listener2);
        capturer.out().print("foo");

        verifyZeroInteractions(listener1);
        verify(listener2).out("foo");
    }

    @Test
    public void starting_a_new_capture_does_not_require_installing_a_new_PrintStream_to_SystemOut() {
        OutputListener listener = mock(OutputListener.class);

        PrintStream out = capturer.out();
        capturer.captureTo(listener);
        out.print("foo");

        verify(listener).out("foo");
    }


    // concurrency

    @Test(timeout = TIMEOUT)
    public void concurrent_captures_are_isolated_from_each_other() throws InterruptedException {
        final CountDownLatch barrier = new CountDownLatch(2);
        final OutputListenerSpy listener1 = new OutputListenerSpy();
        final OutputListenerSpy listener2 = new OutputListenerSpy();

        runConcurrently(
                new Runnable() {
                    @Override
                    public void run() {
                        capturer.captureTo(listener1);
                        sync(barrier);
                        capturer.out().print("from thread 1");
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        capturer.captureTo(listener2);
                        sync(barrier);
                        capturer.out().print("from thread 2");
                    }
                }
        );

        assertThat(listener1.out).containsExactly("from thread 1");
        assertThat(listener2.out).containsExactly("from thread 2");
    }

    @Test(timeout = TIMEOUT)
    public void captures_what_is_printed_in_spawned_threads() throws InterruptedException {
        OutputListenerSpy listener = new OutputListenerSpy();

        capturer.captureTo(listener);
        runConcurrently(new Runnable() {
            @Override
            public void run() {
                capturer.out().print("from spawned thread");
            }
        });

        assertThat(listener.out).containsExactly("from spawned thread");
    }

    @Test(timeout = TIMEOUT)
    public void spawned_threads_outliving_the_main_thread_do_WHAT() throws InterruptedException {
        final CountDownLatch beforeFinished = new CountDownLatch(2);
        final CountDownLatch afterFinished = new CountDownLatch(2);
        OutputListenerSpy listener1 = new OutputListenerSpy();
        OutputListenerSpy listener2 = new OutputListenerSpy();

        capturer.captureTo(listener1);
        Thread t = startThread(new Runnable() {
            @Override
            public void run() {
                capturer.out().print("before main finished");
                sync(beforeFinished);
                sync(afterFinished);
                capturer.out().print("after main finished");
            }
        });
        sync(beforeFinished);
        capturer.captureTo(listener2);
        sync(afterFinished);
        t.join();

        assertThat(listener1.out).containsExactly("before main finished", "after main finished");
        assertThat(listener2.out).containsExactly();
    }


    // helpers

    private static void runConcurrently(Runnable... commands) throws InterruptedException {
        List<Thread> threads = new ArrayList<>();
        for (Runnable command : commands) {
            threads.add(startThread(command));
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }

    private static Thread startThread(Runnable command) {
        Thread t = new Thread(command);
        t.start();
        return t;
    }

    private void sync(CountDownLatch barrier) {
        barrier.countDown();
        try {
            barrier.await(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class OutputListenerSpy implements OutputListener {
        public List<String> out = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public void out(String text) {
            out.add(text);
        }
    }
}