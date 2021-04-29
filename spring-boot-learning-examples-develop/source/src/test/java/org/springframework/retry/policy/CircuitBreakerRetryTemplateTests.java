package org.springframework.retry.policy;

import org.junit.Before;
import org.junit.Test;
import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.DefaultRetryState;
import org.springframework.retry.support.RetryTemplate;

import static org.junit.Assert.*;

/**
 * Created by ZhongCB on 2017/9/8.
 */
public class CircuitBreakerRetryTemplateTests {

    private static final String RECOVERED = "RECOVERED";
    private static final String RESULT = "RESULT";
    private RetryTemplate retryTemplate;
    private RecoveryCallback<Object> recovery;
    private MockRetryCallback callback;
    private DefaultRetryState state;

    @Before
    public void init() {
        this.callback = new MockRetryCallback();
        this.recovery = new RecoveryCallback<Object>() {
            @Override
            public Object recover(RetryContext context) throws Exception {
                return RECOVERED;
            }
        };
        this.retryTemplate = new RetryTemplate();
        this.callback.setAttemptsBeforeSuccess(1);
        // No rollback by default (so exceptions are not rethrown)
        this.state = new DefaultRetryState("retry", new BinaryExceptionClassifier(false));
    }

    @Test
    public void testCircuitOpenWhenNotRetryable() throws Throwable {
        this.retryTemplate
                .setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
        Object result = this.retryTemplate.execute(this.callback, this.recovery,
                this.state);
        assertEquals(1, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
        // circuit is now open so no more attempts
        assertEquals(1, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
    }

    @Test
    public void testCircuitOpenWithNoRecovery() throws Throwable {
        this.retryTemplate
                .setRetryPolicy(new CircuitBreakerRetryPolicy(new NeverRetryPolicy()));
        this.retryTemplate.setThrowLastExceptionOnExhausted(true);
        try {
            this.retryTemplate.execute(this.callback, this.state);
        } catch (Exception e) {
            assertEquals(this.callback.exceptionToThrow, e);
            assertEquals(1, this.callback.getAttempts());
        }
        try {
            this.retryTemplate.execute(this.callback, this.state);
        } catch (Exception e) {
            assertEquals(this.callback.exceptionToThrow, e);
            // circuit is now open so no more attempts
            assertEquals(1, this.callback.getAttempts());
        }
    }

    @Test
    public void testCircuitOpensWhenDelegateNotRetryable() throws Throwable {
        this.retryTemplate
                .setRetryPolicy(new CircuitBreakerRetryPolicy(new SimpleRetryPolicy()));
        this.callback.setAttemptsBeforeSuccess(10);
        Object result = this.retryTemplate.execute(this.callback, this.recovery,
                this.state);
        assertEquals(1, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        assertFalse(this.callback.status.isOpen());
        result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
        result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
        // circuit is now open so no more attempts
        assertEquals(3, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        assertTrue(this.callback.status.isOpen());
    }

    @Test
    public void testWindowResetsAfterTimeout() throws Throwable {
        CircuitBreakerRetryPolicy retryPolicy = new CircuitBreakerRetryPolicy(
                new SimpleRetryPolicy());
        this.retryTemplate.setRetryPolicy(retryPolicy);
        retryPolicy.setOpenTimeout(100);
        this.callback.setAttemptsBeforeSuccess(10);
        Object result = this.retryTemplate.execute(this.callback, this.recovery,
                this.state);
        assertEquals(1, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        assertFalse(this.callback.status.isOpen());
        Thread.sleep(200L);
        result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
        // circuit is reset after sleep window
        assertEquals(2, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        assertFalse(this.callback.status.isOpen());
    }

    @Test
    public void testCircuitClosesAfterTimeout() throws Throwable {
        CircuitBreakerRetryPolicy retryPolicy = new CircuitBreakerRetryPolicy(
                new NeverRetryPolicy());
        this.retryTemplate.setRetryPolicy(retryPolicy);
        retryPolicy.setResetTimeout(100);
        Object result = this.retryTemplate.execute(this.callback, this.recovery,
                this.state);
        assertEquals(1, this.callback.getAttempts());
        assertEquals(RECOVERED, result);
        assertTrue(this.callback.status.isOpen());
        // Sleep longer than the timeout
        Thread.sleep(200L);
        assertFalse(this.callback.status.isOpen());
        result = this.retryTemplate.execute(this.callback, this.recovery, this.state);
        // circuit closed again now
        assertEquals(RESULT, result);
    }

    protected static class MockRetryCallback implements RetryCallback<Object, Exception> {

        private int attemptsBeforeSuccess;

        private Exception exceptionToThrow = new Exception();

        private CircuitBreakerRetryPolicy.CircuitBreakerRetryContext status;

        @Override
        public Object doWithRetry(RetryContext status) throws Exception {
            this.status = (CircuitBreakerRetryPolicy.CircuitBreakerRetryContext) status;
            int attempts = getAttempts();
            attempts++;
            status.setAttribute("attempts", attempts);
            if (attempts <= this.attemptsBeforeSuccess) {
                throw this.exceptionToThrow;
            }
            return RESULT;
        }

        public int getAttempts() {
            if (!this.status.hasAttribute("attempts")) {
                this.status.setAttribute("attempts", 0);
            }
            int attempts = (Integer) this.status.getAttribute("attempts");
            return attempts;
        }

        public void setAttemptsBeforeSuccess(int attemptsBeforeSuccess) {
            this.attemptsBeforeSuccess = attemptsBeforeSuccess;
        }

        public void setExceptionToThrow(Exception exceptionToThrow) {
            this.exceptionToThrow = exceptionToThrow;
        }
    }

}
