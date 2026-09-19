import java.lang.instrument.Instrumentation;

public final class JdkClassPreloaderAgent {

    private JdkClassPreloaderAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation)
        throws ClassNotFoundException {
        // Avoid first loading this JDK class while SkyWalking holds an application JAR lock.
        Class.forName("jdk.internal.misc.ThreadTracker", true, null);
    }
}
