package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.List;

public class TypeVisitor extends PostorderJmmVisitor<List<Report>, Integer> {

    public SimpleSymbolTable symbolTable;

    public TypeVisitor(SimpleSymbolTable symbolTable){
        buildVisitor();
        this.symbolTable = symbolTable;
    }

    @Override
    protected void buildVisitor() {
        addVisit("Neg", this::dealWithNeg);
        addVisit("ArithmeticBinaryOP", this::dealWithArithmetics);
        addVisit("LogicalBinaryOP", this::dealWithLogicalBinaryOP);
        addVisit("Array", this::dealWithArray);
        addVisit("NewArray", this::dealWithNewArray);
        addVisit("Boolean", this::dealWithBoolean);
        addVisit("Int", this::dealWithInt);
        addVisit("This", this::dealWithThis);
        addVisit("Var", this::dealWithVar);
        addVisit("Parenthesis", this::dealWithParenthesis);
        addVisit("If", this::dealWithConditions);
        addVisit("While", this::dealWithConditions);
        addVisit("Assign", this::dealWithAssign);
        addVisit("ArrayAssign", this::dealWithArrayAssign);
        addVisit("VariableId", this::dealWithVariableId);
        addVisit("MethodDeclaration", this::dealWithMethod);
        addVisit("ReturnStatement", this::dealWithReturns);
        addVisit("FunctionCall", this::dealWithFunctionCall);
        addVisit("New", this::dealWithNew);
        addVisit("Length", this::dealWithLength);

        setDefaultVisit(this::visitDefault);



    }

    private Integer dealWithLength(JmmNode jmmNode, List<Report> reports) {
        JmmNode child = jmmNode.getJmmChild(0);

        if (child.get("isArray").equals(SemanticHelper.TRUE)){
            jmmNode.put("type", SemanticHelper.INT);
            jmmNode.put("isArray", SemanticHelper.FALSE);
            return 0;
        }

        reports.add(SemanticHelper.createReport(child, "The variable must be an array"));
        return 1;
    }

    private Integer dealWithNewArray(JmmNode jmmNode, List<Report> reports){
        JmmNode child = jmmNode.getJmmChild(0);

        // if is int[expr]
        if (SemanticHelper.sameType(child, new Type(SemanticHelper.INT, false))){
            jmmNode.put("type", SemanticHelper.INT);
            jmmNode.put("isArray", SemanticHelper.TRUE);
            return 0;
        }

        reports.add(SemanticHelper.createReport(child, "The array size must be an integer"));
        return 1;
    }

    private Integer dealWithNew(JmmNode jmmNode, List<Report> reports) {
        JmmNode child = jmmNode.getJmmChild(0);

        if (child.get("isClass").equals(SemanticHelper.TRUE)) {
            jmmNode.put("type", child.get("name"));
            jmmNode.put("isArray", SemanticHelper.FALSE);
            return 0;
        }

        reports.add(SemanticHelper.createReport(child, "The type of new, must be a class"));
        return 1;




    }

    private Integer dealWithFunctionCall(JmmNode jmmNode, List<Report> reports) {

        JmmNode object = jmmNode.getJmmChild(0);
        String methodName = jmmNode.get("method");

        // When calling methods of the class declared in the code, verify if the types of arguments of the
        // call are compatible with the types in the method declaration
        if (!object.get("type").equals(SemanticHelper.ANY) && SemanticHelper.sameType(object, new Type(symbolTable.className, false) )){
            if (symbolTable.getMethods().contains(methodName)){
                jmmNode.put("type", symbolTable.getReturnType(methodName).getName());
                jmmNode.put("isArray", Boolean.toString(symbolTable.getReturnType(methodName).isArray()));

                JmmNode args = jmmNode.getJmmChild(1);

                List<Symbol> parameters = symbolTable.getParameters(methodName);
                int parameterSize= parameters.size();
                if (parameterSize != args.getNumChildren()){
                    String error_msg = "Method " + methodName + " receives " + parameterSize + " arguments" ;
                    reports.add(SemanticHelper.createReport(jmmNode, error_msg));
                    return 1;
                }

                int i = 0;
                for (Symbol methodParameter : parameters){
                    JmmNode argument = args.getJmmChild(i++);

                    if (!SemanticHelper.sameType(argument, methodParameter.getType())){
                        String error_msg = "Method " + methodName + " require argument " + methodParameter.getName() + " to be of type " + methodParameter.getType().toString() ;
                        reports.add(SemanticHelper.createReport(jmmNode, error_msg));
                        return 1;
                    }
                }



                return 0;
            }else if(symbolTable.classSuper.isEmpty()){
                String error_msg = "Method " + methodName + " doesnt exists" ;
                reports.add(SemanticHelper.createReport(jmmNode, error_msg));
                return 1;
            }

        }

        jmmNode.put("type", SemanticHelper.ANY);
        jmmNode.put("isArray", SemanticHelper.MAYBE);
        return 0;
    }

    private Integer dealWithReturns(JmmNode jmmNode, List<Report> reports) {
        JmmNode child = jmmNode.getJmmChild(0);
        if(child.getOptional("type").isEmpty() || child.getOptional("isArray").isEmpty())
            return 1;
        jmmNode.put("type", child.get("type"));
        jmmNode.put("isArray", child.get("isArray"));

        return 0;
    }


    private Integer dealWithMethod(JmmNode jmmNode, List<Report> reports) {
        if(jmmNode.getJmmChild(0).getKind().equals("MainDeclaration"))
            return 0;
        String methodName = jmmNode.get("methodName");
        Type returnType = symbolTable.getReturnType(methodName);

        JmmNode lastChild = jmmNode.getJmmChild(jmmNode.getChildren().size()-1);

        if (returnType.getName().equals(SemanticHelper.VOID)){
            if (lastChild.getKind().equals("ReturnStatement")){
                String error_msg = "Cannot return a value from void method";
                reports.add(SemanticHelper.createReport(jmmNode, error_msg));
                return 1;
            }

            return 0;
        }

        if (!lastChild.getKind().equals("ReturnStatement") || !SemanticHelper.sameType(lastChild, returnType) ){
            String error_msg = "This method must return " + returnType.getName() + (returnType.isArray() ? "[]" :"");
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        return 0;
    }

    private Integer dealWithVariableId(JmmNode jmmNode, List<Report> reports) {
        String varName = jmmNode.get("var");

        Symbol var = symbolTable.getVariable(jmmNode.get("methodName"), varName);

        if (var == null){
            if (symbolTable.getImportsNames().contains(varName)){
                jmmNode.put("type", varName);
                jmmNode.put("isArray", SemanticHelper.FALSE);
                return 0;
            }
            return 1;
        }

        jmmNode.put("type", var.getType().getName());
        jmmNode.put("isArray", Boolean.toString(var.getType().isArray()));
        return 0;
    }

    private Integer dealWithConditions(JmmNode jmmNode, List<Report> reports) {
        JmmNode conditionExpr = jmmNode.getJmmChild(0);

        if (conditionExpr.getOptional("type").isEmpty()){
            String error_msg = "Can't identify the type of this condition";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        if (!conditionExpr.get("type").equals(SemanticHelper.BOOL) || conditionExpr.get("isArray").equals(SemanticHelper.TRUE)){
            String error_msg = "The type of the condition must be bool";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        return 0;
    }

    private Integer dealWithArrayAssign(JmmNode jmmNode, List<Report> reports) {
        JmmNode leftExpr = jmmNode.getJmmChild(0);
        JmmNode indexExpr = jmmNode.getJmmChild(1);
        JmmNode rightExpr = jmmNode.getJmmChild(2);

        if(leftExpr.getOptional("type").isEmpty() || indexExpr.getOptional("type").isEmpty() || rightExpr.getOptional("type").isEmpty())
            return 1;

        if (verifyArrayAccess(jmmNode, reports, leftExpr, indexExpr)) return 1;

        List<String> importList = symbolTable.importsNames;

        // accept if type of both is imported
        if (importList.contains(leftExpr.get("type")) && leftExpr.get("isArray").equals(SemanticHelper.TRUE) &&
                importList.contains(rightExpr.get("type")) && rightExpr.get("isArray").equals(SemanticHelper.FALSE) )
            return 0;
        Type classType = new Type(symbolTable.className, false);
        Type classTypeArr = new Type(symbolTable.className, true);
        Type superTypeArr = new Type(symbolTable.classSuper, true);

        // accept if rightExpr is class and left is this class type or the super
        if ( SemanticHelper.sameType(rightExpr, classType) &&
            (SemanticHelper.sameType(leftExpr, classTypeArr) || SemanticHelper.sameType(leftExpr, superTypeArr) ))
            return 0;

        // accept if rightExpr is this class type and the left is the super
        if ( SemanticHelper.sameType(rightExpr, classType) &&
            SemanticHelper.sameType(leftExpr, superTypeArr))
            return 0;

        // accept if the type of both is the same
        Type leftType = new Type(leftExpr.get("type"), false);
        if ( SemanticHelper.sameType(rightExpr, leftType))
            return 0;

        String error_msg = "The type of the assignee must be compatible with the assigned";
        reports.add(SemanticHelper.createReport(jmmNode, error_msg));
        return 1;
    }

    private boolean verifyArrayAccess(JmmNode jmmNode, List<Report> reports, JmmNode leftExpr, JmmNode indexExpr) {
        jmmNode.put("type", leftExpr.get("type"));
        jmmNode.put("isArray", SemanticHelper.FALSE);

        if ( !leftExpr.get("isArray").equals(SemanticHelper.TRUE)){
            String error_msg = "Array access is done over an array";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return true;
        }

        if ( !SemanticHelper.sameType(indexExpr, new Type(SemanticHelper.INT, false)) ){
            String error_msg = "Array access index must be an integer";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return true;
        }

        return false;
    }

    private Integer dealWithAssign(JmmNode jmmNode, List<Report> reports) {
        JmmNode leftExpr = jmmNode.getJmmChild(0);
        JmmNode rightExpr = jmmNode.getJmmChild(1);

        if(leftExpr.getOptional("type").isEmpty() || rightExpr.getOptional("type").isEmpty())
            return 1;

        List<String> importList = symbolTable.importsNames;

        // accept if type of both is imported
        if (importList.contains(leftExpr.get("type")) && leftExpr.get("isArray").equals(SemanticHelper.FALSE) &&
                importList.contains(rightExpr.get("type")) && rightExpr.get("isArray").equals(SemanticHelper.FALSE) )
            return 0;

        // accept if rightExpr is this class and left is this class type or the super
        if (SemanticHelper.sameType(rightExpr, new Type(symbolTable.className, false)) &&
                (SemanticHelper.sameType(leftExpr, new Type(symbolTable.classSuper, false)) ||
                SemanticHelper.sameType(leftExpr, new Type(symbolTable.className, false))) )
            return 0;

        // accept if rightExpr is this class type and the left is the super
        if (SemanticHelper.sameType(rightExpr, new Type(symbolTable.className, false)) &&
                SemanticHelper.sameType(leftExpr, new Type(symbolTable.classSuper, false)) )
            return 0;


        // accept if they have the same type
        // accept if the type of both is the same
        if ( SemanticHelper.sameType(leftExpr, rightExpr))
            return 0;

        String error_msg = "The type of the assignee must be compatible with the assigned";
        reports.add(SemanticHelper.createReport(jmmNode, error_msg));
        return 1;
    }

    private Integer dealWithVar(JmmNode jmmNode, List<Report> reports) {

        JmmNode child = jmmNode.getJmmChild(0);

        if (child.getOptional("type").isEmpty() || child.getOptional("isArray").isEmpty()){
            return -1;
        }

        jmmNode.put("type", child.get("type"));
        jmmNode.put("isArray", child.get("isArray"));
        return 0;
    }

    private Integer dealWithParenthesis(JmmNode jmmNode, List<Report> reports) {
        JmmNode child = jmmNode.getJmmChild(0);

        if (child.getOptional("type").isEmpty() || child.getOptional("isArray").isEmpty()){
            return -1;
        }
        jmmNode.put("type", child.get("type"));
        jmmNode.put("isArray", child.get("isArray"));
        // todo
        return 0;
    }

    private Integer dealWithThis(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", symbolTable.className);
        jmmNode.put("isArray", SemanticHelper.FALSE);
        return 0;
    }

    private Integer dealWithInt(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", SemanticHelper.INT);
        jmmNode.put("isArray", SemanticHelper.FALSE);
        return 0;
    }

    private Integer dealWithBoolean(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", SemanticHelper.BOOL);
        jmmNode.put("isArray", SemanticHelper.FALSE);
        return 0;
    }

    private Integer dealWithArray(JmmNode jmmNode, List<Report> reports) {
        JmmNode leftExpr = jmmNode.getJmmChild(0);
        JmmNode indexExpr = jmmNode.getJmmChild(1);

        if (verifyArrayAccess(jmmNode, reports, leftExpr, indexExpr)) return 1;

        jmmNode.put("type", leftExpr.get("type"));
        jmmNode.put("isArray", SemanticHelper.FALSE);
        return 0;
    }



    private Integer dealWithLogicalBinaryOP(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", SemanticHelper.BOOL);
        jmmNode.put("isArray", SemanticHelper.FALSE);

        JmmNode leftExpr = jmmNode.getJmmChild(0);
        String operator = jmmNode.get("op");
        JmmNode rightExpr = jmmNode.getJmmChild(1);
        if (leftExpr.getOptional("type").isEmpty() || rightExpr.getOptional("type").isEmpty()){
            String error_msg = "Can't identify the type of this expression";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        if (operator.equals("&&")){
            if ( !SemanticHelper.sameType(leftExpr, new Type(SemanticHelper.BOOL, false)) ||
                    !SemanticHelper.sameType(rightExpr, new Type(SemanticHelper.BOOL, false)) ){
                String error_msg = "Operator '"+ operator +"' must be applied to integers";
                reports.add(SemanticHelper.createReport(jmmNode, error_msg));
                return 1;
            }
            return 0;
        }

        if ( !SemanticHelper.sameType(leftExpr, new Type(SemanticHelper.INT, false)) ||
                !SemanticHelper.sameType(rightExpr, new Type(SemanticHelper.INT, false)) ){
            String error_msg = "Operator '"+ operator +"' must be applied to integers";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        return  0;
    }

    private Integer dealWithArithmetics(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", SemanticHelper.INT);
        jmmNode.put("isArray", SemanticHelper.FALSE);

        JmmNode leftExpr = jmmNode.getJmmChild(0);
        String operator = jmmNode.get("op");
        JmmNode rightExpr = jmmNode.getJmmChild(1);

        if ( !SemanticHelper.sameType(leftExpr, new Type(SemanticHelper.INT, false)) ||
                !SemanticHelper.sameType(rightExpr, new Type(SemanticHelper.INT, false)) ){
            String error_msg = "Operator '"+ operator +"' must be applied to ints";
            reports.add(SemanticHelper.createReport(jmmNode, error_msg));
            return 1;
        }

        return  0;
    }

    private Integer dealWithNeg(JmmNode jmmNode, List<Report> reports) {
        jmmNode.put("type", SemanticHelper.BOOL);
        jmmNode.put("isArray", SemanticHelper.FALSE);

        JmmNode child = jmmNode.getJmmChild(0);

        if (SemanticHelper.sameType(child, new Type(SemanticHelper.BOOL, false)) ){
            return  0;
        }

        String error_msg = "Operator ! must be applied to boolean";
        reports.add(SemanticHelper.createReport(jmmNode, error_msg));

        return 1;
    }

    private Integer visitDefault(JmmNode jmmNode, List<Report> reports) {

        return  0;
    }


}
