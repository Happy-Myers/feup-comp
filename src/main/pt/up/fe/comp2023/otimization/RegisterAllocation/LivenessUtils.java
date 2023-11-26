package pt.up.fe.comp2023.otimization.RegisterAllocation;

import org.specs.comp.ollir.Method;

import java.util.Set;

public class LivenessUtils {

    private Method method;
    private Set<String> excludedVariables;

    public LivenessUtils(Method method) {
        this.method = method;
        excludedVariables.add("this");
    }


}
