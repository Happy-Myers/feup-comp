package pt.up.fe.comp2023.otimization.Constants;

import java.util.*;

public class ConstPropagationData {
    private Map<String, String> varValues;

    public ConstPropagationData(){
        varValues = new HashMap<>();
    }

    public ConstPropagationData(ConstPropagationData data){
        varValues = new HashMap<>(data.varValues);
    }

    public void replace(ConstPropagationData data){
        varValues = new HashMap<>(data.varValues);
    }

    public boolean removeConstant(String varName) {
        return varValues.remove(varName) != null;
    }

    public void putConstant(String varName, String value) {
        varValues.put(varName, value);
    }

    public String getConstant(String varName){
        return varValues.get(varName);
    }

    public void intersection(ConstPropagationData data){
        Set<String> vars = new HashSet<>(varValues.keySet());
        for (String var : vars)
            if (!getConstant(var).equals(data.getConstant(var)))
                removeConstant(var);

    }
}
