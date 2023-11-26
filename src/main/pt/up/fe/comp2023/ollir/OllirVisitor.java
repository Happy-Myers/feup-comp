package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.analysis.SimpleSymbolTable;
import pt.up.fe.comp2023.analysis.SemanticHelper;

import java.util.*;
import java.util.stream.Collectors;

public class OllirVisitor extends AJmmVisitor<OllirMode, String> {
    private final StringBuilder code;
    private final SymbolTable symbolTable;
    private int indentation;
    private int tempVarNum;
    private int whileNum;
    private int ifNum;

    public OllirVisitor(SymbolTable symbolTable){
        this.code = new StringBuilder();
        this.symbolTable = symbolTable;
        this.indentation = 0;
        this.tempVarNum = 0;
        this.whileNum = 0;
        this.ifNum = 0;
    }

    @Override
    protected void buildVisitor() {
        addVisit("Program", this::dealWithProgram);
        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("MethodDeclaration", this::dealWithMethods);
        addVisit("MainDeclaration", this::dealWithMain);
        addVisit("Block", this::dealWithBlock);
        addVisit("If", this::dealWithIf);
        addVisit("While", this::dealWithWhile);
        addVisit("Line", this::dealWithLine);
        addVisit("Assign", this::dealWithAssign);
        addVisit("ArrayAssign", this::dealWithArrayAssign);
        addVisit("Neg", this::dealWithNeg);
        addVisit("ArithmeticBinaryOP", this::dealWithArithmetics);
        addVisit("LogicalBinaryOP", this::dealWithLogicalBinaryOP);
        addVisit("Array", this::dealWithArray);
        addVisit("Length", this::dealWithLength);
        addVisit("FunctionCall", this::dealWithFunctionCall);
        addVisit("New", this::dealWithNew);
        addVisit("NewArray", this::dealWithNewArray);
        addVisit("Parenthesis", this::dealWithParenthesis);
        addVisit("Boolean", this::dealWithBoolean);
        addVisit("Var", this::dealWithVar);
        addVisit("Int", this::dealWithInt);
        addVisit("This", this::dealWithThis);
        addVisit("VariableId", this::dealWithVariable);
        addVisit("ReturnStatement", this::dealWithReturn);
        setDefaultVisit(this::defaultVisit);
    }

    private String dealWithReturn(JmmNode jmmNode, OllirMode ollirMode) {
        String methodName = getCurrentMethod(jmmNode).get("methodName");

        String returnType, returnValue;

        returnType = getOllirType(symbolTable.getReturnType(methodName)) + " ";

        returnValue = visit(jmmNode.getJmmChild(0));

        code.append(getIndentation()).append("ret").append(returnType).append(returnValue).append(";\n");

        return "";
    }

    private String defaultVisit(JmmNode jmmNode, OllirMode ollirMode) {
        return "";
    }


    private void incrementIndentation(){
        this.indentation++;
    }

    private void decrementIndentation(){
        this.indentation--;
    }

    private String getIndentation(){
        return "\t".repeat(indentation);
    }

    private int getAndAddTempVar(JmmNode node){
        JmmNode method = getCurrentMethod(node);

        while(true) {
            this.tempVarNum++;
            if(!SemanticHelper.findVariable(symbolTable, method, "t" + tempVarNum)){
                return this.tempVarNum;
            }
        }
    }

    private int getAndAddWhileNum(){
        return this.whileNum++;
    }

    private int getAndAddIfThenElseNum(){
        return this.ifNum++;
    }

    private String getIndexIntoReg(String index, JmmNode jmmNode){
        int tempVar = getAndAddTempVar(jmmNode);

        code.append(getIndentation()).append("t").append(tempVar).append(".i32 :=.i32 ").append(index).append(";\n");
        return "t" + tempVar + ".i32";
    }

    private static JmmNode getCurrentMethod(JmmNode node){
        Optional<JmmNode> method = node.getAncestor("MethodDeclaration");

        if(method.isPresent() && method.get().getJmmChild(0).getKind().equals("MainDeclaration"))
            return method.get().getJmmChild(0);

        return method.orElse(null);
    }

    public String getCode(){
        return code.toString();
    }

    private String dealWithProgram(JmmNode jmmNode, OllirMode ollirMode) {
        for(String importString : symbolTable.getImports()){
            code.append("import ").append(importString).append(";\n");
        }
        code.append("\n\n");


        visit(jmmNode.getJmmChild(jmmNode.getNumChildren()-1));
        return "";
    }

    private String dealWithClass(JmmNode jmmNode, OllirMode ollirMode) {
        code.append(getIndentation()).append(symbolTable.getClassName());
        String superClass = symbolTable.getSuper();

        if(!Objects.equals(superClass, "")){
            code.append(" extends ").append(superClass);
        }

        code.append(" {\n");

        this.incrementIndentation();

        for(Symbol field : symbolTable.getFields()){
            code.append(getIndentation()).append(".field ").append(field.getName()).append(getOllirType(field.getType())).append(";\n");
        }

        code.append("\n");

        code.append(getIndentation()).append(".construct ").append(symbolTable.getClassName()).append("().V {\n");
        this.incrementIndentation();
        code.append(getIndentation()).append("invokespecial(this, \"<init>\").V;\n");
        this.decrementIndentation();
        code.append(getIndentation()).append(("}\n"));

        for(JmmNode method : jmmNode.getJmmChild(1).getChildren()){
            code.append("\n");
            visit(method);
        }

        this.decrementIndentation();

        code.append(getIndentation()).append("}\n");

        return "";
    }

    private String dealWithMethods(JmmNode jmmNode, OllirMode ollirMode) {
        if(jmmNode.getJmmChild(0).getKind().equals("MainDeclaration"))
            visit(jmmNode.getJmmChild(0));
        else{
            String methodName = jmmNode.get("methodName");

            code.append(getIndentation()).append(".method public ").append(methodName).append("(");

            List<Symbol> params = symbolTable.getParameters(methodName);

            List<JmmNode> statements = jmmNode.getJmmChild(jmmNode.getNumChildren()-2).getChildren();

            String paramCode = params.stream().map(OllirVisitor::getCode).collect(Collectors.joining(", "));

            code.append(paramCode).append(")");
            code.append(getOllirType(symbolTable.getReturnType(methodName)));

            code.append("{\n");

            this.incrementIndentation();

            for(JmmNode node : statements){
                visit(node);
            }

            JmmNode returnNode = jmmNode.getJmmChild(jmmNode.getNumChildren()-1);
            visit(returnNode);

            this.decrementIndentation();

            code.append(getIndentation()).append("}\n");
        }
        return "";
    }

    private String dealWithMain(JmmNode jmmNode, OllirMode ollirMode) {
        String methodName = "main";

        code.append(getIndentation()).append(".method public static ").append(methodName).append("(");

        List<Symbol> params = symbolTable.getParameters(methodName);

        List<JmmNode> statements = jmmNode.getJmmChild(1).getChildren();

        String paramCode = params.stream().map(OllirVisitor::getCode).collect(Collectors.joining(", "));

        code.append(paramCode).append(")");
        code.append(getOllirType(symbolTable.getReturnType(methodName)));

        code.append("{\n");

        this.incrementIndentation();

        for(JmmNode node : statements){
            visit(node);
        }

        String returnString;

        returnString = ".V";


        code.append(getIndentation()).append("ret").append(returnString).append(";\n");

        this.decrementIndentation();

        code.append(getIndentation()).append("}\n");
        return "";
    }

    private String dealWithBlock(JmmNode jmmNode, OllirMode ollirMode) {
        for(JmmNode child : jmmNode.getChildren()){
            visit(child);
        }

        return "";
    }

    private String dealWithIf(JmmNode jmmNode, OllirMode ollirMode) {
        JmmNode condition = jmmNode.getJmmChild(0);
        JmmNode trueBlock = jmmNode.getJmmChild(1);
        JmmNode falseBlock = jmmNode.getJmmChild(2);

        int ifThenElseNum = getAndAddIfThenElseNum();

        String conditionKind = condition.getKind();

        boolean notAssignToTemp = conditionKind.equals("ArithmeticBinaryOP") || conditionKind.equals("LogicalBinaryOP")
                || conditionKind.equals("Boolean") || conditionKind.equals("Neg") || (conditionKind.equals("Var") && ((SimpleSymbolTable)symbolTable).isField(getCurrentMethod(jmmNode).get("methodName"), condition.getJmmChild(0).get("var")));

        String conditionRegOrExpression = visit(condition, new OllirMode(".bool", !notAssignToTemp));

        code.append(getIndentation()).append("if (").append(conditionRegOrExpression).append(") goto ifTrue").append(ifThenElseNum).append(";\n");

        this.incrementIndentation();
        visit(falseBlock);

        code.append(getIndentation()).append("goto endIf").append(ifThenElseNum).append(";\n");
        this.decrementIndentation();

        code.append(getIndentation()).append("ifTrue").append(ifThenElseNum).append(":\n");

        this.incrementIndentation();
        visit(trueBlock);

        this.decrementIndentation();

        code.append(getIndentation()).append("endIf").append(ifThenElseNum).append(":\n");

        return "";
    }

    private String dealWithWhile(JmmNode jmmNode, OllirMode ollirMode) {
        JmmNode condition = jmmNode.getJmmChild(0);
        JmmNode block = jmmNode.getJmmChild(1);

        int whileNum = getAndAddWhileNum();

        String conditionKind = condition.getKind();

        boolean notAssignToTemp = conditionKind.equals("ArithmeticBinaryOP") || conditionKind.equals("LogicalBinaryOP")
                || conditionKind.equals("Boolean") || conditionKind.equals("Neg") || conditionKind.equals("Var");

        code.append(getIndentation()).append("whileCondition").append(whileNum).append(":\n");

        String conditionRegOrExpression = visit(condition, new OllirMode(".bool", !notAssignToTemp));

        code.append(getIndentation()).append("if (").append(conditionRegOrExpression).append(") goto whileBody").append(whileNum).append(";\n");
        this.incrementIndentation();
        code.append(getIndentation()).append("goto endWhile").append(whileNum).append(";\n");
        this.decrementIndentation();

        code.append(getIndentation()).append("whileBody").append(whileNum).append(":\n");
        this.incrementIndentation();
        visit(block);

        code.append(getIndentation()).append("goto whileCondition").append(whileNum).append(";\n");
        this.decrementIndentation();

        code.append(getIndentation()).append("endWhile").append(whileNum).append(":\n");

        return "";
    }

    private String dealWithLine(JmmNode jmmNode, OllirMode ollirMode) {
        visit(jmmNode.getJmmChild(0));

        code.append("\n");
        return "";
    }

    private String dealWithAssign(JmmNode jmmNode, OllirMode ollirMode) {
        String var = visit(jmmNode.getJmmChild(0), new OllirMode(false));

        String varName = getOllirNameNoTypeOrParam(var);

        String type;

        JmmNode method = getCurrentMethod(jmmNode);

        String methodName = method.get("methodName");

        type = getOllirType(((SimpleSymbolTable) symbolTable).getVariable(methodName, varName).getType());

        if(((SimpleSymbolTable) symbolTable).isField(methodName, varName)){
            String child = visit(jmmNode.getJmmChild(1), new OllirMode(type, true));

            code.append(getIndentation()).append("putfield(this, ").append(var).append(", ").append(child).append(").V;\n");
        }
        else{
            String child = visit(jmmNode.getJmmChild(1), new OllirMode(type, false));

            code.append(getIndentation()).append(var).append(" :=").append(type).append(" ").append(child).append(";\n");
        }

        return "";
    }

    private String dealWithArrayAssign(JmmNode jmmNode, OllirMode ollirMode){
        String child = visit(jmmNode.getJmmChild(0), new OllirMode( false));

        String varName = getOllirNameNoTypeOrParam(child);

        String index = visit(jmmNode.getJmmChild(1), new OllirMode(".i32", true));
        String indexReg = index;

        if(isImmediateValueIndex(index))
            indexReg = getIndexIntoReg(index, jmmNode);

        String var = varName + "[" + indexReg + "].i32";

        String type = ".i32";

        JmmNode method = getCurrentMethod(jmmNode);

        String methodName = method.get("methodName");

        if(((SimpleSymbolTable) symbolTable).isField(methodName, varName)){
            String val = visit(jmmNode.getJmmChild(2), new OllirMode(type, true));

            code.append(getIndentation()).append("putfield(this, ").append(var).append(", ").append(val).append(").V;\n");
        }
        else{
            String val = visit(jmmNode.getJmmChild(2), new OllirMode(type, false));

            code.append(getIndentation()).append(var).append(" :=").append(type).append(" ").append(val).append(";\n");
        }

        return "";
    }

    private String dealWithNeg(JmmNode jmmNode, OllirMode ollirMode) {
        String child = visit(jmmNode.getJmmChild(0), new OllirMode(".bool", true));

        String operation = " !.bool " + child;

        if(ollirMode == null || ollirMode.isNeedTempVar()){
            int tempVar = getAndAddTempVar(jmmNode);

            code.append(getIndentation()).append("t").append(tempVar).append(".bool").append(" :=.bool").append(operation).append(";\n");

            return "t" + tempVar + ".bool";
        }
        else {
            return operation;
        }
    }

    private String dealWithBinaryOP(JmmNode jmmNode, OllirMode ollirMode){
        String operator = jmmNode.get("op");

        String returnType = getReturnType(operator);

        String assignmentType = getoperandType(operator);

        String left = visit(jmmNode.getJmmChild(0), new OllirMode(assignmentType, true));
        String right = visit(jmmNode.getJmmChild(1), new OllirMode(assignmentType, true));

        String operation = left + " " + getOperator(operator) + " " + right;

        if(ollirMode == null || ollirMode.isNeedTempVar()){
            int tempVar = getAndAddTempVar(jmmNode);

            code.append(getIndentation()).append("t").append(tempVar).append(returnType).append(" :=").append(returnType).append(" ").append(operation).append(";\n");

            return "t" + tempVar + returnType;
        } else{
            return operation;
        }
    }

    private String dealWithArithmetics(JmmNode jmmNode, OllirMode ollirMode) {
        return dealWithBinaryOP(jmmNode, ollirMode);
    }

    private String dealWithLogicalBinaryOP(JmmNode jmmNode, OllirMode ollirMode){
        return dealWithBinaryOP(jmmNode, ollirMode);
    }

    private String dealWithArray(JmmNode jmmNode, OllirMode ollirMode) {
        String child = visit(jmmNode.getJmmChild(0), new OllirMode(".array.i32", true));

        String id = getArrayId(child);

        String index = visit(jmmNode.getJmmChild(1), new OllirMode(".i32", true));
        String indexReg = index;

        if(isImmediateValueIndex(index))
            indexReg = getIndexIntoReg(index, jmmNode);

        String op = id + "[" + indexReg + "].i32";

        if(ollirMode == null || ollirMode.isNeedTempVar()){
            int tempvar = getAndAddTempVar(jmmNode);

            code.append(getIndentation()).append("t").append(tempvar).append(".i32 :=.i32 ").append(op).append(";\n");

            return "t" + tempvar + ".i32";
        }
        return op;
    }

    private String dealWithLength(JmmNode jmmNode, OllirMode ollirMode) {
        String child = visit(jmmNode.getJmmChild(0), new OllirMode(".array.i32", true));

        String op = " arraylength(" + child + ").i32";

        if(ollirMode == null || ollirMode.isNeedTempVar()){
            int tempVar = getAndAddTempVar(jmmNode);

            code.append(getIndentation()).append("t").append(tempVar).append(".i32 :=.i32").append(op).append(";\n");

            return "t" + tempVar + ".i32";
        }
        return op;
    }

    private String dealWithFunctionCall(JmmNode jmmNode, OllirMode ollirMode) {
        JmmNode varNode = jmmNode;

        String varKind = varNode.getJmmChild(0).getKind();

        if(varKind.equals("Parenthesis")){
            varNode = varNode.getJmmChild(0);
            varKind = varNode.getJmmChild(0).getKind();
        }

        String firstArg;

        JmmNode method = getCurrentMethod(jmmNode);

        if(varKind.equals("This")){
            firstArg = "this";
        }
        else if(varKind.equals("FunctionCall") || varKind.equals("New") || varKind.equals("NewArray")){
            firstArg = visit(varNode.getJmmChild(0));
        }
        else{
            firstArg = varNode.getJmmChild(0).getJmmChild(0).get("var");
            Symbol var = ((SimpleSymbolTable)symbolTable).getVariable(method.get("methodName"), firstArg);
            if(var != null){
                firstArg += getOllirType(((SimpleSymbolTable)symbolTable).getVariable(method.get("methodName"), firstArg).getType());
            }
        }
        String invokeType = getInvoke(firstArg, symbolTable);
        String methodName = jmmNode.get("method");

        String returnType;
        if(ollirMode == null || ollirMode.getType() == null){
            String type;
            if(firstArg.equals("this")){
                type = symbolTable.getClassName();
            } else if(((SimpleSymbolTable)symbolTable).isImportedClass(method.get("methodName"), firstArg)){
                type = firstArg;
            }else{
                type = getOllirNameNoParam(firstArg);
            }

            if(type.equals(symbolTable.getClassName())){
                Type retType = symbolTable.getReturnType(methodName);

                if(retType == null){
                    returnType = ".V";
                } else{
                    returnType = getOllirType(retType);
                }
            } else {
                returnType = ".V";
            }
        } else {
            returnType = ollirMode.getType();
        }

        List<Symbol> params = symbolTable.getParameters(methodName);

        int argNum = 0;

        List<String> args = new ArrayList<>();

        for(JmmNode arg : jmmNode.getJmmChild(1).getChildren()){
            if(((SimpleSymbolTable)symbolTable).methodExists(methodName)){
                Symbol param = params.get(argNum);
                if(param != null){
                    String ArgType = getOllirType(param.getType());
                    args.add(visit(arg, new OllirMode(ArgType, true)));
                    argNum++;
                    continue;
                }
            }
            args.add(visit(arg, new OllirMode(true)));
            argNum++;
        }

        StringBuilder op = new StringBuilder(invokeType + "(" + firstArg + ", \"" + methodName + "\"");

        for(String arg : args){
            op.append(", ").append(arg);
        }
        op.append(")").append(returnType);

        if(ollirMode == null || ollirMode.isNeedTempVar()){
            code.append(getIndentation());

            int tempVar = getAndAddTempVar(jmmNode);
            if(!returnType.equals(".V")){
                code.append("t").append(tempVar).append(returnType).append(" :=").append(returnType).append(" ");
            }
            code.append(op);
            code.append(";\n");

            return "t" + tempVar + returnType;
        }else{
            return op.toString();
        }
    }

    private String dealWithNew(JmmNode jmmNode, OllirMode ollirMode){
        return dealWithNews(jmmNode, ollirMode);
    }

    private String dealWithNewArray(JmmNode jmmNode, OllirMode ollirMode){
        return dealWithNews(jmmNode, ollirMode);
    }

    private String dealWithNews(JmmNode jmmNode, OllirMode ollirMode) {
        boolean isArrayNew = jmmNode.getKind().equals("NewArray");

        String type;
        String arraySize = null;

        if(isArrayNew){
            type = "array.i32";

            arraySize = visit(jmmNode.getJmmChild(0));
        }
        else{
            type = jmmNode.getJmmChild(0).get("name");
        }

        int tempVar = getAndAddTempVar(jmmNode);

        code.append(getIndentation()).append("t").append(tempVar).append(".").append(type).append(" :=.").append(type).append(" ").append("new(");

        if(isArrayNew)
            code.append("array, ").append(arraySize);
        else
            code.append(type);

        code.append(").").append(type).append(";\n");

        if(!isArrayNew)
            code.append(getIndentation()).append("invokespecial(t").append(tempVar).append(".").append(type).append(", \"<init>\").V;\n");

        return "t" + tempVar + "." + type;
    }

    private String dealWithParenthesis(JmmNode jmmNode, OllirMode ollirMode) {

        String op = visit(jmmNode.getJmmChild(0));
        return op;
    }

    private String dealWithBoolean(JmmNode jmmNode, OllirMode ollirMode) {
        return getBoolValue(jmmNode.get("value")) + ".bool";
    }

    private String dealWithVar(JmmNode jmmNode, OllirMode ollirMode) {
        jmmNode = jmmNode.getJmmChild(0);
        String name = jmmNode.get("var");
        String type;

        JmmNode method = getCurrentMethod(jmmNode);


        String methodName = method.get("methodName");

        type = getOllirType(((SimpleSymbolTable)symbolTable).getVariable(methodName, name).getType());

        if(((SimpleSymbolTable)symbolTable).isField(methodName, name)){
            String operation = "getfield(this, " + name + type + ")" + type;

            if(ollirMode == null || ollirMode.isNeedTempVar()){
                int tempVar = getAndAddTempVar(jmmNode);

                code.append(getIndentation()).append("t").append(tempVar).append(type).append(" :=").append(type).append(" ").append(operation).append(";\n");

                return "t" + tempVar + type;
            }else{
                return operation;
            }
        }
        String reference = ((SimpleSymbolTable)symbolTable).getOllirLikeReference(methodName, name);

        return reference + name + type;
    }

    private String dealWithInt(JmmNode jmmNode, OllirMode ollirMode) {
        return jmmNode.get("value") + ".i32";
    }

    private String dealWithThis(JmmNode jmmNode, OllirMode ollirMode) {
        return "this." + symbolTable.getClassName();
    }

    private String dealWithVariable(JmmNode jmmNode, OllirMode ollirMode){
        String name = jmmNode.get("var");
        String type;

        JmmNode method = getCurrentMethod(jmmNode);


        String methodName = method.get("methodName");

        type = getOllirType(((SimpleSymbolTable)symbolTable).getVariable(methodName, name).getType());

        String reference = ((SimpleSymbolTable)symbolTable).getOllirLikeReference(methodName, name);

        return reference + name + type;
    }


    public static String getCode(Symbol symbol){
        return symbol.getName() + getOllirType(symbol.getType());
    }

    public static String getOllirType(Type type){
        switch(type.getName()){
            case "void" -> {
                return ".V";
            }
            case "boolean"->{
                return ".bool";
            }
            case "int" ->{
                if(type.isArray()){
                    return ".array.i32";
                }
                else{
                    return ".i32";
                }
            }
            case "String"->{
                return ".array.String";
            }
            default -> {
                return "." + type.getName();
            }
        }
    }

    public static String getInvoke(String invokee, SymbolTable symbolTable){
        if(invokee.equals("this")){
            return "invokevirtual";
        }

        List<String> imports = symbolTable.getImports();

        for(String _import : imports){
            String[] tokens = _import.split("\\.");
            if(tokens[tokens.length-1].equals(invokee)){
                return "invokestatic";
            }
        }

        return "invokevirtual";
    }

    public static String getOperator(String operator){
        return switch (operator){
            case "+" -> "+.i32";
            case "-" -> "-.i32";
            case "*" -> "*.i32";
            case "/" -> "/.i32";
            case "&&" -> "&&.bool";
            case "<" -> "<.bool";
            default -> "// error: invalid operator\n";
        };
    }

    public static String getReturnType(String operator){
        return switch(operator){
            case "+", "-", "*", "/" -> ".i32";
            case "&&", "<" -> ".bool";
            default -> "//error: invalid operator\n";
        };
    }

    public static String getBoolValue(String value){
        return switch(value){
            case "true" -> "1";
            case "false" -> "0";
            default -> "//error: invalid bool value\n";
        };
    }

    public static String getoperandType(String operator){
        return switch(operator){
            case "+", " <", "-", "*", "/" -> ".i32";
            case "&&"-> ".bool";
            default -> "//error: invalid operator\n";
        };
    }


    public static String getOllirNameNoTypeOrParam(String ollirId){
        String[] ollirIdSplit = ollirId.split("\\.");
        int n = ollirId.charAt(0) == '$' ? 1 : 0;
        return ollirIdSplit[n];
    }

    public static String getOllirNameNoParam(String ollirId) {
        String[] ollirIdSplit = ollirId.split("\\.");
        int n = ollirId.charAt(0) == '$' ? 2 : 1;
        return String.join(".", Arrays.copyOfRange(ollirIdSplit, n, ollirIdSplit.length));
    }

    public static String getArrayId(String child){
        String[] childSplit = child.split("\\.");
        return String.join(".", Arrays.copyOf(childSplit, childSplit.length == 3 ? 1 : 2));
    }

    public boolean isImmediateValueIndex(String index){
        if(index.charAt(0) == '-')
            return isInteger(index.substring(0, 2));

        return isInteger(index.substring(0, 1));
    }

    public boolean isInteger(String string){
        try{
            Integer.parseInt(string);
            return true;
        }
        catch (NumberFormatException e){
            return false;
        }
    }

}