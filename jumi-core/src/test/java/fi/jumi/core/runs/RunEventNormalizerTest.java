// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package fi.jumi.core.runs;

import fi.jumi.api.drivers.TestId;
import fi.jumi.core.api.*;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class RunEventNormalizerTest {

    private final SuiteListenerSpy spy = new SuiteListenerSpy();
    private final SuiteListener target = spy(spy);

    private static final String INFORMATION_ABOUT_THE_CURRENT_CONTEXT = "Incorrect notifier API usage in com.example.DummyTest: ";
    private final TestFile testFile = TestFile.fromClassName("com.example.DummyTest");
    private final RunEventNormalizer normalizer = new RunEventNormalizer(target, testFile);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void forwards_all_events() {
        normalizer.onTestFound(TestId.ROOT, "test name");
        normalizer.onPrintedOut(new RunId(7), "stdout");
        normalizer.onPrintedErr(new RunId(8), "stderr");
        normalizer.onFailure(new RunId(9), TestId.of(1), new Exception("dummy exception"));
        normalizer.onTestStarted(new RunId(10), TestId.ROOT);
        normalizer.onTestFinished(new RunId(11), TestId.of(3));
        normalizer.onRunStarted(new RunId(20));
        normalizer.onRunFinished(new RunId(21));
        normalizer.onInternalError("the message", new Exception("dummy exception"));

        verify(target).onTestFound(testFile, TestId.ROOT, "test name");
        verify(target).onPrintedOut(new RunId(7), "stdout");
        verify(target).onPrintedErr(new RunId(8), "stderr");
        verify(target).onFailure(eq(new RunId(9)), notNull(StackTrace.class));
        verify(target).onTestStarted(new RunId(10), TestId.ROOT);
        verify(target).onTestFinished(new RunId(11));
        verify(target).onRunStarted(new RunId(20), testFile);
        verify(target).onRunFinished(new RunId(21));
        verify(target).onInternalError(eq("the message"), notNull(StackTrace.class));
        verifyNoMoreInteractions(target);

        for (Method sourceMethod : RunListener.class.getMethods()) {
            Method targetMethod = getMethod(sourceMethod.getName(), SuiteListener.class);
            assertThat("this test failed to check all event types", spy.methodInvocations.keySet(), hasItem(targetMethod));
        }
    }

    @Test
    public void forwards_unique_onTestFound_events() {
        normalizer.onTestFound(TestId.ROOT, "root");
        normalizer.onTestFound(TestId.of(1), "testOne");

        verify(target).onTestFound(testFile, TestId.ROOT, "root");
        verify(target).onTestFound(testFile, TestId.of(1), "testOne");
        verifyNoMoreInteractions(target);
    }

    @Test
    public void removes_duplicate_onTestFound_events() {
        normalizer.onTestFound(TestId.ROOT, "root");
        normalizer.onTestFound(TestId.ROOT, "root");

        verify(target, times(1)).onTestFound(testFile, TestId.ROOT, "root");
        verifyNoMoreInteractions(target);
    }

    @Test
    public void tests_must_be_found_always_with_the_same_name() {
        normalizer.onTestFound(TestId.ROOT, "first name");

        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(INFORMATION_ABOUT_THE_CURRENT_CONTEXT);
        thrown.expectMessage("test TestId() was already found with another name: first name");
        normalizer.onTestFound(TestId.ROOT, "second name");
    }

    @Test
    public void parents_must_be_found_before_their_children() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(INFORMATION_ABOUT_THE_CURRENT_CONTEXT);
        thrown.expectMessage("parent of TestId(0) must be found first");
        normalizer.onTestFound(TestId.of(0), "child");
    }

    @Test
    public void onTestFound_must_be_called_before_onTestStarted() {
        thrown.expect(IllegalStateException.class);
        thrown.expectMessage(INFORMATION_ABOUT_THE_CURRENT_CONTEXT);
        thrown.expectMessage("the test TestId() must be found first");
        normalizer.onTestStarted(new RunId(1), TestId.ROOT);
    }

    @Test
    public void has_custom_toString() {
        assertThat(normalizer.toString(), is("RunEventNormalizer(com.example.DummyTest)"));
    }


    // helpers

    private static Method getMethod(String name, Class<SuiteListener> type) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("No method named " + name + " in " + type);
    }
}
