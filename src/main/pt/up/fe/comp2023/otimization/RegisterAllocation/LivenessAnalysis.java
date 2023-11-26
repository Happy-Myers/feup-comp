package pt.up.fe.comp2023.otimization.RegisterAllocation;

import org.specs.comp.ollir.*;

import java.util.*;

public class LivenessAnalysis {

    private final Method method;
    private final Map<Instruction, LivenessData> livenessData;

    public LivenessAnalysis(Method method){
        this.method = method;
        this.livenessData = new HashMap<>();

        for (Instruction instruction : method.getInstructions()){
            LivenessData data = new LivenessData(getDefineVariables(instruction), getUseVariables(instruction),
                    new HashSet<>(), new HashSet<>());
            livenessData.put(instruction, data);
        }

    }

    public Method getMethod() {
        return method;
    }

    public Map<Instruction, LivenessData> getLivenessData() {
        return livenessData;
    }

    public void analyze(){
        boolean anyModification = true;

        List<Instruction> instructions = new ArrayList<>(method.getInstructions());
        Collections.reverse(instructions);

        while (anyModification){
            anyModification = false;

            for (Instruction instruction : instructions){

                LivenessData data = livenessData.get(instruction);

                Set<String> in = new HashSet<>(data.getUse());
                in.addAll(data.getOut());
                in.removeAll(data.getDef());

                anyModification |= data.getIn().addAll(in);

                for (Node successor : instruction.getSuccessors()){
                    if (successor.getNodeType().equals(NodeType.INSTRUCTION)) {
                        LivenessData successorData = livenessData.get((Instruction) successor);
                        anyModification |= data.getOut().addAll(successorData.getIn());
                    }
                }
            }
        }

    }

    private Set<String> getDefineVariables(Instruction instruction){
        HashSet<String> def = new HashSet<>();

        if (instruction.getInstType() == InstructionType.ASSIGN){
            Operand element = (Operand) ((AssignInstruction) instruction).getDest();
            addElement(def, element);
        }else if (instruction.getInstType() == InstructionType.GETFIELD){
            Operand operand = (Operand) ((GetFieldInstruction) instruction).getSecondOperand();
            addElement(def, operand);
        }

        return def;
    }

    private Set<String> getUseVariables(Instruction instruction){
        HashSet<String> use = new HashSet<>();

        switch (instruction.getInstType()){
            case ASSIGN:
                AssignInstruction assignInstruction = (AssignInstruction) instruction;
                return getUseVariables(assignInstruction.getRhs());
            case CALL:
                CallInstruction callInstruction = (CallInstruction) instruction;
                if (callInstruction.getListOfOperands() == null)
                    break;
                for (Element element : callInstruction.getListOfOperands())
                    addElement(use, element);
                return use;
            case RETURN:
                ReturnInstruction returnInstruction = (ReturnInstruction) instruction;
                if (returnInstruction.hasReturnValue()) {
                    addElement(use, returnInstruction.getOperand());
                }
                return use;
            case BRANCH:
                CondBranchInstruction condBranchInstruction = (CondBranchInstruction) instruction;
                return getUseVariables(condBranchInstruction.getCondition());
            case BINARYOPER:
                BinaryOpInstruction binaryOperationInstruction = (BinaryOpInstruction) instruction;
                addElement(use, binaryOperationInstruction.getLeftOperand());
                addElement(use, binaryOperationInstruction.getRightOperand());
                return use;
            case UNARYOPER:
                UnaryOpInstruction unaryOperationInstruction = (UnaryOpInstruction) instruction;
                addElement(use, unaryOperationInstruction.getOperand());
                return use;
            case PUTFIELD:
                PutFieldInstruction putFieldInstruction = (PutFieldInstruction) instruction;
                addElement(use, putFieldInstruction.getThirdOperand());
                return use;
            case GETFIELD:
            case NOPER:
            case GOTO:
                return use;

        }
        return use;
    }

    private void addElement(Set<String> set, Element element){
        if (element.isLiteral())
            return;

        Operand operand = (Operand) element;
        if (!operand.isParameter())
            set.add(operand.getName());
    }

}
