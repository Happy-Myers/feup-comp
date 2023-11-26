package pt.up.fe.comp2023.otimization.RegisterAllocation;

import java.util.HashSet;
import java.util.Set;

public class LivenessData {

    private final Set<String> def;
    private final Set<String> use;
    private final Set<String> in;
    private final Set<String> out;


    public LivenessData() {
        this.def = new HashSet<>();
        this.use = new HashSet<>();
        this.in = new HashSet<>();
        this.out = new HashSet<>();
    }

    public LivenessData(Set<String> def, Set<String> use, Set<String> in, Set<String> out) {
        this.def = def;
        this.use = use;
        this.in = in;
        this.out = out;
    }

    public Set<String> getDef() {
        return def;
    }

    public Set<String> getUse() {
        return use;
    }

    public Set<String> getIn() {
        return in;
    }

    public Set<String> getOut() {
        return out;
    }
}
