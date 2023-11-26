package pt.up.fe.comp2023.otimization.RegisterAllocation;

import org.specs.comp.ollir.*;
import pt.up.fe.specs.util.collections.MultiMap;

import java.util.*;

public class InferenceGraph {

    private final Map<Instruction,LivenessData> livenessData;
    private final Method method;
    private final HashMap<String, Descriptor> vartable;
    private final Map<String, Set<String>> graph;
    private int minColors;

    public InferenceGraph(LivenessAnalysis livenessAnalysis) {
        this.livenessData = livenessAnalysis.getLivenessData();
        this.method = livenessAnalysis.getMethod();
        this.vartable = method.getVarTable();
        this.graph = new HashMap<>();
        this.minColors = -1;
    }

    public void build() {

        // create a node for each variable
        for (String var : vartable.keySet()) {
            Descriptor varDescriptor = vartable.get(var);
            if (varDescriptor.getScope().equals(VarScope.LOCAL) && !varDescriptor.getVarType().getTypeOfElement().equals(ElementType.THIS))
                graph.put(var, new HashSet<>());
            else {
                if (varDescriptor.getVirtualReg() > minColors)
                    minColors = varDescriptor.getVirtualReg();
            }
        }
        minColors+=1;

        // each node is connected to nodes that are live at the same time
        for (Instruction instruction : method.getInstructions()) {
            LivenessData data = livenessData.get(instruction);

            Set<String> out_def = new HashSet<>(data.getOut());
            out_def.addAll(data.getDef());

            addEdges(out_def);
            addEdges(data.getIn());

        }


    }

    private void addEdges(Set<String> alive){
        for (String var1 : alive) {
            Set<String> var1Set = graph.get(var1);

            for (String var2 : alive)
                if (!var1.equals(var2))
                    var1Set.add(var2);
        }
    }

    public int colorize(int maxRegisters) {
        Stack<String> stack = new Stack<>();

        Set<String> nodes = new HashSet<>(graph.keySet());


        while (!nodes.isEmpty()) {
            String minNode = getMinNode(nodes);
            stack.push(minNode);
            nodes.remove(minNode);
        }


        // inicialize registers
        for (String var : graph.keySet()) {
            vartable.get(var).setVirtualReg(-1);
        }

        int numberRegisters = minColors;

        while (!stack.isEmpty()){
            String var = stack.pop();
            Set<String> neighbors = graph.get(var);

            int color = minColors;

            for (String neighbor : neighbors){
                int neighborColor = vartable.get(neighbor).getVirtualReg();
                color = Math.max(color, neighborColor+1);
            }

            vartable.get(var).setVirtualReg(color);
        }

        return numberRegisters;
    }

    private String getMinNode(Set<String> nodes){
        String minNode = null;
        int min = Integer.MAX_VALUE;

        for (String node : nodes){
            int neighborgs = getNumberNeigh(node, nodes);

            if (neighborgs < min){
                min = neighborgs;
                minNode = node;
            }
        }

        return minNode;
    }

    private int getNumberNeigh(String node, Set<String> validNodes){
        int size = 0;
        for (String neighbor : graph.get(node))
            if (!validNodes.contains(neighbor))
                size++;
        return size;

    }


}
