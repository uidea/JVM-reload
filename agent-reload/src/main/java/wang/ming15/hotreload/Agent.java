package wang.ming15.hotreload;

import wang.ming15.hotreload.core.Reloader;

import java.lang.instrument.Instrumentation;

public class Agent {
    public static void agentmain(String agentArgs, Instrumentation inst) {
        Reloader.init(agentArgs, inst);
    }
}
