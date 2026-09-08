package com.antondev.chats;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.opentest4j.TestAbortedException;
import java.lang.reflect.Method;

/** Unsupported mock operations must fail visibly, not silently skip release coverage. */
public final class RequiredMockBukkitCoverage implements InvocationInterceptor {
    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> context, ExtensionContext extension) throws Throwable {
        try { invocation.proceed(); }
        catch (TestAbortedException ex) {
            throw new AssertionError("Regression test was aborted: " + context.getExecutable().getName()
                    + ". Supply a supported mock fixture rather than skipping the assertion.", ex);
        }
    }
}
