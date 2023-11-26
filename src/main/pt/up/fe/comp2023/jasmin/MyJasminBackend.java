package pt.up.fe.comp2023.jasmin;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.*;

public class MyJasminBackend implements JasminBackend {

    ClassUnit classUnit = null;

    String jasminCode;

    String superClassName;

    int stackPointer = 0;
    int stackLimit = 0;

    int labelNumber = 0;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {

        this.classUnit = ollirResult.getOllirClass();

        List<Report> reports = ollirResult.getReports();

        try {
            this.jasminCode = generateJasmin();
        }
        catch (Exception e) {
            Report report = Report.newError(Stage.GENERATION, 0, 0, e.getMessage(), e);
            reports.add(report);
            for (Report r : reports) {
                System.out.println("Report: " + r);
            }
            return new JasminResult(ollirResult, "", reports);
        }

        System.out.println("jasminCode: ");
        System.out.println(jasminCode);

        return new JasminResult(ollirResult, jasminCode, reports);

    }

    private String generateJasmin() {

        StringBuilder jasminBuilder = new StringBuilder();

        jasminBuilder.append(".class ")
                .append(translateAccessModifier(this.classUnit.getClassAccessModifier()))
                .append(this.classUnit.getClassName())
                .append("\n");

        this.superClassName = this.classUnit.getSuperClass();

        jasminBuilder.append(".super ");

        if (this.superClassName != null) jasminBuilder.append(this.superClassName).append("\n");
        else {
            jasminBuilder.append("java/lang/Object").append("\n");
            this.superClassName = "java/lang/Object";
        }

        jasminBuilder.append("\n");

        for (Field field : this.classUnit.getFields()) {
            jasminBuilder.append(".field ")
                    .append(translateAccessModifier(field.getFieldAccessModifier()));

            if (field.isStaticField()) {
                jasminBuilder.append("static ");
            }

            if (field.isFinalField()) {
                jasminBuilder.append("final ");
            }

            jasminBuilder.append(field.getFieldName()).append(" ")
                    .append(translateType(field.getFieldType())).append("\n");
        }

        for (Method method : this.classUnit.getMethods()) {

            jasminBuilder.append(generateMethodHeader(method));

            jasminBuilder.append(generateMethodBody(method));

        }

        return jasminBuilder.toString();
    }

    private String generateMethodHeader(Method method) {

        StringBuilder methodHeader = new StringBuilder();

        methodHeader.append("\n")
                .append(".method ")
                .append(translateAccessModifier(method.getMethodAccessModifier()));

        if (method.isStaticMethod()) methodHeader.append("static ");
        if (method.isFinalMethod()) methodHeader.append("final ");
        if (method.isConstructMethod()) methodHeader.append("<init>");
        else methodHeader.append(method.getMethodName());

        methodHeader.append("(");

        for (Element param : method.getParams()) {
            methodHeader.append(translateType(param.getType()));
        }

        methodHeader.append(")")
                .append(translateType(method.getReturnType()))
                .append("\n");

        return methodHeader.toString();
    }

    private String generateMethodBody(Method method) {

        StringBuilder methodBody = new StringBuilder();

        int maxLocals = -1;
        for (Descriptor var : method.getVarTable().values()) {
            if (var.getVirtualReg() > maxLocals) maxLocals = var.getVirtualReg();
        }

        if (maxLocals < 0 && !method.isStaticMethod()){
            maxLocals = 0;
        }

        this.stackPointer = 0;
        this.stackLimit = 0;

        String instructions = generateMethodInstructions(method);

        if (!method.isConstructMethod()) {
            methodBody.append("\t.limit stack ").append(this.stackLimit).append("\n")
                    .append("\t.limit locals ").append(maxLocals+1).append("\n");
        }

        methodBody.append(instructions);

        return methodBody.toString();

    }

    private String generateMethodInstructions(Method method) {

        StringBuilder methodInstructions = new StringBuilder();

        List<Instruction> instructionList = method.getInstructions();

        for (Instruction instruction : instructionList) {

            for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                if (label.getValue().equals(instruction)) {
                    methodInstructions.append(label.getKey()).append(":\n");
                }
            }

            methodInstructions.append(generateInstruction(instruction, method.getVarTable()));

            if (instruction.getInstType() == InstructionType.CALL
                    && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {
                methodInstructions.append("\tpop\n");
                updateStackLimits(-1);
            }

        }

        InstructionType lastInstructionType = instructionList.get(instructionList.size() - 1).getInstType();

        boolean checkReturnInstruction = instructionList.size() > 0 && lastInstructionType == InstructionType.RETURN;

        if (!checkReturnInstruction && method.getReturnType().getTypeOfElement() == ElementType.VOID) {
            methodInstructions.append("\treturn\n");
        }

        methodInstructions.append(".end method\n");

        return methodInstructions.toString();
    }

    private String generateInstruction(Instruction instruction, HashMap<String, Descriptor> varTable) {

        return switch (instruction.getInstType()) {
            case ASSIGN -> generateAssignInstruction((AssignInstruction) instruction, varTable);
            case CALL -> generateCallInstruction((CallInstruction) instruction, varTable);
            case GOTO -> generateGoToInstruction((GotoInstruction) instruction);
            case BRANCH -> generateCondBranchInstruction((CondBranchInstruction) instruction, varTable);
            case RETURN -> generateReturnInstruction((ReturnInstruction) instruction, varTable);
            case PUTFIELD -> generatePutFieldInstruction((PutFieldInstruction) instruction, varTable);
            case GETFIELD -> generateGetFieldInstruction((GetFieldInstruction) instruction, varTable);
            case UNARYOPER -> generateUnaryOperationInstruction((UnaryOpInstruction) instruction, varTable);
            case BINARYOPER -> generateBinaryOperationInstruction((BinaryOpInstruction) instruction, varTable);
            case NOPER -> generatePushToStack(((SingleOpInstruction) instruction).getSingleOperand(), varTable);
        };
    }

    private String generateAssignInstruction(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder assignBuilder = new StringBuilder();

        Operand dest = (Operand) instruction.getDest();

        if (dest instanceof ArrayOperand array) {
            updateStackLimits(1);

            assignBuilder.append("\taload").append(generateVarNumber(array.getName(), varTable)).append("\n");
            assignBuilder.append(generatePushToStack(array.getIndexOperands().get(0), varTable));

        } else {

            if (instruction.getRhs().getInstType() == InstructionType.BINARYOPER) {
                BinaryOpInstruction binaryOp = (BinaryOpInstruction) instruction.getRhs();

                if (binaryOp.getOperation().getOpType() == OperationType.ADD ||
                        binaryOp.getOperation().getOpType() == OperationType.SUB) {
                    boolean checkLeftLiteral = binaryOp.getLeftOperand().isLiteral();
                    boolean checkRightLiteral = binaryOp.getRightOperand().isLiteral();

                    LiteralElement literalElement = null;
                    Operand operand = null;

                    if (!checkLeftLiteral && checkRightLiteral) {
                        literalElement = (LiteralElement) binaryOp.getRightOperand();
                        operand = (Operand) binaryOp.getLeftOperand();
                    } else if (checkLeftLiteral && !checkRightLiteral) {
                        literalElement = (LiteralElement) binaryOp.getLeftOperand();
                        operand = (Operand) binaryOp.getRightOperand();
                    }

                    if (literalElement != null && operand != null) {
                        if (operand.getName().equals(dest.getName())) {

                            int integerValue = Integer.parseInt((literalElement).getLiteral());

                            if (binaryOp.getOperation().getOpType() == OperationType.SUB) {
                                if (integerValue <= 128)
                                    return "\tiinc " + varTable.get(operand.getName()).getVirtualReg() + " -" + integerValue + "\n";
                                else throw new JasminException("ERROR: VALUE FOR IINC OUT OF BOUNDS");
                            }
                            else if (integerValue <= 127) {
                                return "\tiinc " + varTable.get(operand.getName()).getVirtualReg() + " " + integerValue + "\n";
                            }
                            else throw new JasminException("ERROR: VALUE FOR IINC OUT OF BOUNDS");
                        }
                    }
                }
            }
        }

        assignBuilder.append(generateInstruction(instruction.getRhs(), varTable));
        assignBuilder.append(generateStore(dest, varTable));

        return assignBuilder.toString();

    }

    private String generateCallInstruction(CallInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder callBuilder = new StringBuilder();

        int variation = 0;

        switch (instruction.getInvocationType()) {
            case invokevirtual -> {
                callBuilder.append(generatePushToStack(instruction.getFirstArg(), varTable));

                variation = 1;

                for (Element element : instruction.getListOfOperands()) {
                    callBuilder.append(generatePushToStack(element, varTable));
                    variation++;
                }

                callBuilder.append("\tinvokevirtual ")
                        .append(convertClassName(((ClassType) instruction.getFirstArg().getType()).getName()))
                        .append("/")
                        .append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : instruction.getListOfOperands()) {
                    callBuilder.append(translateType(element.getType()));
                }

                callBuilder.append(")").append(translateType(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    variation--;
                }

            }
            case invokespecial -> {
                callBuilder.append(generatePushToStack(instruction.getFirstArg(), varTable));

                variation = 1;
                callBuilder.append("\tinvokespecial ");

                if (instruction.getFirstArg().getType().getTypeOfElement() == ElementType.THIS)
                    callBuilder.append(this.superClassName);
                else
                    callBuilder.append(convertClassName(((ClassType) instruction.getFirstArg().getType()).getName()));

                callBuilder.append("/").append("<init>(");

                for (Element element : instruction.getListOfOperands()) {
                    callBuilder.append(translateType(element.getType()));
                }

                callBuilder.append(")")
                        .append(translateType(instruction.getReturnType()))
                        .append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    variation--;
                }

            }
            case invokestatic -> {

                variation = 0;

                for (Element element : instruction.getListOfOperands()) {
                    callBuilder.append(generatePushToStack(element, varTable));
                    variation++;
                }

                callBuilder.append("\tinvokestatic ")
                        .append(convertClassName(((Operand) instruction.getFirstArg()).getName()))
                        .append("/")
                        .append(((LiteralElement) instruction.getSecondArg()).getLiteral().replace("\"", ""))
                        .append("(");

                for (Element element : instruction.getListOfOperands()) {
                    callBuilder.append(translateType(element.getType()));
                }

                callBuilder.append(")")
                        .append(translateType(instruction.getReturnType()))
                        .append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID) {
                    variation--;
                }

            }
            case NEW -> {

                variation = -1;

                switch (instruction.getReturnType().getTypeOfElement()) {
                    case OBJECTREF -> {
                        for (Element element : instruction.getListOfOperands()) {
                            callBuilder.append(generatePushToStack(element, varTable));
                            variation++;
                        }

                        callBuilder.append("\tnew ")
                                .append(convertClassName(((Operand) instruction.getFirstArg()).getName()))
                                .append("\n");
                    }
                    case ARRAYREF -> {
                        for (Element element : instruction.getListOfOperands()) {
                            callBuilder.append(generatePushToStack(element, varTable));
                            variation++;
                        }

                        callBuilder.append("\tnewarray ");
                        if (instruction.getListOfOperands().get(0).getType().getTypeOfElement()== ElementType.INT32) {
                            callBuilder.append("int\n");
                        } else {
                            throw new JasminException("ERROR: ONLY INT ARRAYS ARE SUPPORTED");
                        }
                    }
                    default -> throw new JasminException("ERROR: INVALID NEW INVOCATION");
                }

            }
            case arraylength -> {
                callBuilder.append(generatePushToStack(instruction.getFirstArg(), varTable));
                callBuilder.append("\tarraylength\n");
            }
            case ldc -> callBuilder.append(generatePushToStack(instruction.getFirstArg(), varTable));
            default -> throw new JasminException("ERROR: CALL INSTRUCTION NOT RECOGNIZED: " + instruction.getInvocationType().toString() + "\n");
        }

        updateStackLimits(-variation);

        return callBuilder.toString();
    }

    private String generateGoToInstruction(GotoInstruction instruction) {
        return "\tgoto " + instruction.getLabel() + "\n";
    }

    private String generateCondBranchInstruction(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder condBranchBuilder = new StringBuilder();

        Instruction condition;

        if (instruction instanceof SingleOpCondInstruction singleOpCondInstruction) {
            condition = singleOpCondInstruction.getCondition();

        } else if (instruction instanceof OpCondInstruction opCondInstruction) {
            condition = opCondInstruction.getCondition();
        }
        else {
            throw new JasminException("ERROR: BRANCH INSTRUCTION NOT RECOGNIZED\n");
        }

        String operationToAdd;

        switch (condition.getInstType()) {
            case UNARYOPER -> {
                UnaryOpInstruction unaryOpInstruction = (UnaryOpInstruction) condition;
                if (unaryOpInstruction.getOperation().getOpType() == OperationType.NOTB) {
                    condBranchBuilder.append(generatePushToStack(unaryOpInstruction.getOperand(), varTable));
                    operationToAdd = "ifeq";
                }
                else throw new JasminException("ERROR: INVALID UNARY OPERATOR");
            }
            case BINARYOPER -> {
                BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) condition;
                switch (binaryOpInstruction.getOperation().getOpType()) {
                    case LTH -> {
                        Element leftElement = binaryOpInstruction.getLeftOperand();
                        Element rightElement = binaryOpInstruction.getRightOperand();

                        operationToAdd = "if_icmplt";

                        Integer numberToCompare = null;
                        Element element = null;

                        if (rightElement.isLiteral()) {
                            String literal = ((LiteralElement) rightElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = leftElement;
                            operationToAdd = "iflt";
                        } else if (leftElement.isLiteral()) {
                            String literal = ((LiteralElement) leftElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = rightElement;
                            operationToAdd = "ifgt";
                        }

                        if (numberToCompare != null && numberToCompare == 0) {
                            condBranchBuilder.append(generatePushToStack(element, varTable));
                        } else {
                            condBranchBuilder.append(generatePushToStack(leftElement, varTable))
                                    .append(generatePushToStack(rightElement, varTable));

                            operationToAdd = "if_icmplt";
                        }
                    }
                    case LTE -> {
                        Element leftElement = binaryOpInstruction.getLeftOperand();
                        Element rightElement = binaryOpInstruction.getRightOperand();

                        operationToAdd = "if_icmple";

                        Integer numberToCompare = null;
                        Element element = null;

                        if (rightElement.isLiteral()) {
                            String literal = ((LiteralElement) rightElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = leftElement;
                            operationToAdd = "ifle";
                        } else if (leftElement.isLiteral()) {
                            String literal = ((LiteralElement) leftElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = rightElement;
                            operationToAdd = "ifge";
                        }

                        if (numberToCompare != null && numberToCompare == 0) {
                            condBranchBuilder.append(generatePushToStack(element, varTable));
                        } else {
                            condBranchBuilder.append(generatePushToStack(leftElement, varTable))
                                    .append(generatePushToStack(rightElement, varTable));

                            operationToAdd = "if_icmple";
                        }
                    }
                    case GTH -> {
                        Element leftElement = binaryOpInstruction.getLeftOperand();

                        Element rightElement = binaryOpInstruction.getRightOperand();

                        operationToAdd = "if_icmpgt";

                        Integer numberToCompare = null;
                        Element element = null;

                        if (rightElement.isLiteral()) {
                            String literal = ((LiteralElement) rightElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = leftElement;
                            operationToAdd = "ifgt";
                        } else if (leftElement.isLiteral()) {
                            String literal = ((LiteralElement) leftElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = rightElement;
                            operationToAdd = "iflt";
                        }

                        if (numberToCompare != null && numberToCompare == 0) {
                            condBranchBuilder.append(generatePushToStack(element, varTable));
                        } else {
                            condBranchBuilder.append(generatePushToStack(leftElement, varTable))
                                    .append(generatePushToStack(rightElement, varTable));

                            operationToAdd = "if_icmpgt";
                        }
                    }
                    case GTE -> {
                        Element leftElement = binaryOpInstruction.getLeftOperand();
                        Element rightElement = binaryOpInstruction.getRightOperand();

                        operationToAdd = "if_icmpge";

                        Integer numberToCompare = null;
                        Element element = null;

                        if (rightElement.isLiteral()) {
                            String literal = ((LiteralElement) rightElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = leftElement;
                            operationToAdd = "ifge";
                        } else if (leftElement.isLiteral()) {
                            String literal = ((LiteralElement) leftElement).getLiteral();
                            numberToCompare = Integer.parseInt(literal);
                            element = rightElement;
                            operationToAdd = "ifle";
                        }

                        if (numberToCompare != null && numberToCompare == 0) {
                            condBranchBuilder.append(generatePushToStack(element, varTable));
                        } else {
                            condBranchBuilder.append(generatePushToStack(leftElement, varTable))
                                    .append(generatePushToStack(rightElement, varTable));

                            operationToAdd = "if_icmpge";
                        }
                    }
                    case ANDB -> {
                        condBranchBuilder.append(generateInstruction(condition, varTable));
                        operationToAdd = "ifne";
                    }
                    default ->
                            throw new JasminException("ERROR: INVALID BINARY OPERATOR " + binaryOpInstruction.getOperation().getOpType());
                }
            }
            default -> {
                condBranchBuilder.append(generateInstruction(condition, varTable));
                operationToAdd = "ifne";
            }
        }

        condBranchBuilder.append("\t").append(operationToAdd).append(" ").append(instruction.getLabel()).append("\n");

        if (operationToAdd.equals("if_icmplt") || operationToAdd.equals("if_icmpgt")
                || operationToAdd.equals("if_icmple") || operationToAdd.equals("if_icmpge")) {
            updateStackLimits(-2);
        } else {
            updateStackLimits(-1);
        }

        return condBranchBuilder.toString();
    }

    private String generateReturnInstruction(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder returnBuilder = new StringBuilder();

        if (instruction.hasReturnValue()) returnBuilder.append(generatePushToStack(instruction.getOperand(), varTable));

        returnBuilder.append("\t");

        if (instruction.getOperand() != null) {
            ElementType typeOfElement = instruction.getOperand().getType().getTypeOfElement();

            if (typeOfElement == ElementType.BOOLEAN || typeOfElement == ElementType.INT32) {
                returnBuilder.append("i");
            } else {
                returnBuilder.append("a");
            }
        }

        returnBuilder.append("return\n");

        return returnBuilder.toString();
    }

    private String generatePutFieldInstruction(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {

        String putInstruction = generatePushToStack(instruction.getFirstOperand(), varTable) +
                generatePushToStack(instruction.getThirdOperand(), varTable) + "\tputfield " +
                convertClassName(((Operand) instruction.getFirstOperand()).getName()) + "/" +
                ((Operand) instruction.getSecondOperand()).getName() + " " +
                translateType(instruction.getSecondOperand().getType()) + "\n";

        updateStackLimits(-2);

        return putInstruction;
    }

    private String generateGetFieldInstruction(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {

        return generatePushToStack(instruction.getFirstOperand(), varTable) +
                "\tgetfield " + convertClassName(((Operand) instruction.getFirstOperand()).getName()) + "/" +
                ((Operand) instruction.getSecondOperand()).getName() + " "
                + translateType(instruction.getSecondOperand().getType()) + "\n";
    }

    private String generateUnaryOperationInstruction(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder unaryOpBuilder = new StringBuilder();

        unaryOpBuilder.append(generatePushToStack(instruction.getOperand(), varTable))
                .append("\t").append(translateOperationType(instruction.getOperation()));

        if (instruction.getOperation().getOpType() == OperationType.NOTB) {
            unaryOpBuilder.append(pushComparisonResultToStack());
        }
        else throw new JasminException("ERROR: INVALID UNARY OPERATOR");

        unaryOpBuilder.append("\n");

        return unaryOpBuilder.toString();
    }

    private String generateBinaryOperationInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder binaryOpBuilder = new StringBuilder();

        Element leftElement = instruction.getLeftOperand();
        Element rightElement = instruction.getRightOperand();

        OperationType operationType = instruction.getOperation().getOpType();
        boolean isComparison =
                operationType == OperationType.EQ
                        || operationType == OperationType.NEQ
                        || operationType == OperationType.LTH
                        || operationType == OperationType.LTE
                        || operationType == OperationType.GTH
                        || operationType == OperationType.GTE;


        binaryOpBuilder.append(generatePushToStack(leftElement, varTable))
                .append(generatePushToStack(rightElement, varTable))
                .append("\t").append(translateOperationType(instruction.getOperation()));

        if (isComparison) {
            binaryOpBuilder.append(pushComparisonResultToStack());
        }

        binaryOpBuilder.append("\n");

        updateStackLimits(-1);

        return binaryOpBuilder.toString();
    }

    private String translateOperationType(Operation operation) {
        switch (operation.getOpType()) {
            case LTH -> {
                return "if_icmplt";
            }
            case ANDB -> {
                return "iand";
            }
            case NOTB -> {
                return "ifeq";
            }
            case ADD -> {
                return "iadd";
            }
            case SUB -> {
                return "isub";
            }
            case MUL -> {
                return "imul";
            }
            case DIV -> {
                return "idiv";
            }
            default ->
                    throw new JasminException("ERROR: OPERATION NOT RECOGNIZED: " + operation.getOpType().toString() + "\n");
        }
    }

    private String generateStore(Operand dest, HashMap<String, Descriptor> varTable) {

        StringBuilder storeBuilder = new StringBuilder();

        switch (dest.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                if (varTable.get(dest.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    storeBuilder.append("\tiastore").append("\n");
                    updateStackLimits(-3);
                } else {
                    storeBuilder.append("\tistore").append(generateVarNumber(dest.getName(), varTable)).append("\n");
                    updateStackLimits(-1);
                }


            }
            case OBJECTREF, STRING, ARRAYREF, THIS -> {
                storeBuilder.append("\tastore").append(generateVarNumber(dest.getName(), varTable)).append("\n");
                updateStackLimits(-1);
            }
            default -> throw new JasminException("ERROR: GENERATE STORE NOT RECOGNIZED " + dest.getType().getTypeOfElement() + "\n");
        }

        return storeBuilder.toString();

    }

    private String generatePushToStack(Element element, HashMap<String, Descriptor> varTable) {

        StringBuilder pushToStackBuilder = new StringBuilder();

        if (element instanceof ArrayOperand operand) {

            pushToStackBuilder.append("\taload")
                    .append(generateVarNumber(operand.getName(), varTable)).append("\n");
            updateStackLimits(1);

            pushToStackBuilder.append(generatePushToStack(operand.getIndexOperands().get(0), varTable));
            pushToStackBuilder.append("\tiaload");

            updateStackLimits(-1);
        }
        else if (element instanceof Operand operand) {
            switch (operand.getType().getTypeOfElement()) {
                case INT32, BOOLEAN ->
                        pushToStackBuilder.append("\tiload").append(generateVarNumber(operand.getName(), varTable));
                case OBJECTREF, STRING, ARRAYREF, THIS ->
                        pushToStackBuilder.append("\taload").append(generateVarNumber(operand.getName(), varTable));
                default -> throw new JasminException("ERROR: PUSH TO STACK TYPE NOT RECOGNIZED " + operand.getType().getTypeOfElement() + "\n");
            }

            updateStackLimits(1);
        }
        else if (element instanceof LiteralElement) {
            String literal = ((LiteralElement) element).getLiteral();

            if (element.getType().getTypeOfElement() == ElementType.INT32
                    || element.getType().getTypeOfElement() == ElementType.BOOLEAN) {

                int parsedInt = Integer.parseInt(literal);

                if (parsedInt >= -1 && parsedInt <= 5) pushToStackBuilder.append("\ticonst_");
                else if (parsedInt >= -128 && parsedInt <= 127) pushToStackBuilder.append("\tbipush ");
                else if (parsedInt >= -32768 && parsedInt <= 32767) pushToStackBuilder.append("\tsipush ");
                else pushToStackBuilder.append("\tldc ");

                if (parsedInt == -1) pushToStackBuilder.append("m1");
                else pushToStackBuilder.append(parsedInt);

            }
            else pushToStackBuilder.append("\tldc ").append(literal);

            updateStackLimits(1);

        }

        pushToStackBuilder.append("\n");

        return pushToStackBuilder.toString();
    }

    private void updateStackLimits(int variation) {
        this.stackPointer += variation;
        if (this.stackLimit < this.stackPointer) this.stackLimit = this.stackPointer;
    }

    private String convertClassName(String className) {
        if (className.equals("this")) {
            return this.classUnit.getClassName();
        }

        return className;
    }

    private String translateType(Type type) {
        switch (type.getTypeOfElement()) {
            case ARRAYREF -> {
                return "[" + translateType(((ArrayType) type).getElementType());
            }
            case INT32 -> {
                return "I";
            }
            case BOOLEAN -> {
                return "Z";
            }
            case OBJECTREF -> {
                return "L" + convertClassName(((ClassType) type).getName()) + ";";
            }
            case STRING -> {
                return "Ljava/lang/String;";
            }
            case VOID -> {
                return "V";
            }
            default ->
                    throw new JasminException("ERROR: TYPE NOT RECOGNIZED: " + type.getTypeOfElement().toString() + "\n");
        }
    }

    private String pushComparisonResultToStack() {
        return " LABELX" + this.labelNumber + "\n"
                + "\ticonst_0\n"
                + "\tgoto SKIPX" + this.labelNumber + "\n"
                + "LABELX" + this.labelNumber + ":\n"
                + "\ticonst_1\n"
                + "SKIPX" + this.labelNumber++ + ":";
    }

    private String generateVarNumber(String varName, HashMap<String, Descriptor> varTable) {

        if (varName.equals("this")) {
            return "_0";
        }

        int varNumber = varTable.get(varName).getVirtualReg();

        StringBuilder stringBuilder = new StringBuilder();

        if (varNumber <= 3) stringBuilder.append("_");
        else stringBuilder.append(" ");

        stringBuilder.append(varNumber);

        return stringBuilder.toString();
    }

    private String translateAccessModifier(AccessModifiers accessModifier) {
        return switch (accessModifier) {
            case PUBLIC, DEFAULT -> "public ";
            case PRIVATE -> "private ";
            case PROTECTED -> "protected ";
        };
    }
}


