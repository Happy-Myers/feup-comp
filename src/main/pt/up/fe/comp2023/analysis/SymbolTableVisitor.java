package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;

public class SymbolTableVisitor extends PreorderJmmVisitor<List<Report>, Integer > {
    private final SimpleSymbolTable symbolTable;

    public SymbolTableVisitor(SimpleSymbolTable symbolTable){
        buildVisitor();
        this.symbolTable = symbolTable;
    }

    @Override
    protected void buildVisitor() {
        addVisit("ImportDeclaration", this::dealWithImports);
        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("VarDeclaration", this::dealWithVars);
        addVisit("MethodDeclaration", this::dealWithMethods);
        addVisit("MainDeclaration", this::dealWithMain);
        addVisit("Param", this::dealWithParam);
        addVisit("Variable", this::dealWithVariable);
        addVisit("This", this::dealWithThis);
        addVisit("Type", this::dealWithType);



        setDefaultVisit(this::defaultVisit);
    }

    private Integer dealWithType(JmmNode jmmNode, List<Report> reports) {
        if (jmmNode.get("isClass").equals(SemanticHelper.TRUE) && !
                (symbolTable.getImportsNames().contains(jmmNode.get("name")) ||
                jmmNode.get("name").equals(SemanticHelper.STRING) ||
                jmmNode.get("name").equals(symbolTable.className) ||
                jmmNode.get("name").equals(symbolTable.classSuper) )){

            reports.add(SemanticHelper.createReport(jmmNode, "This class does not exists"));
            return 1;
        }

        return defaultVisit(jmmNode, reports);

    }

    private Integer dealWithImports(JmmNode jmmNode, List<Report> reports) {
        ArrayList<String> attributes = (ArrayList<String>) jmmNode.getObject("importClass");

        String importStr = String.join(".", attributes);
        if(!symbolTable.addImport(importStr)){
            SemanticHelper.createReport(jmmNode, "Import '"+ importStr +"' is duplicated");
            return 1;
        }

        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithClass(JmmNode jmmNode, List<Report> reports) {
        String className = jmmNode.get("name");
        symbolTable.setClassName(className);
        if (symbolTable.importsNames.contains(className)){
            reports.add(SemanticHelper.createReport(jmmNode, "Class '"+ className +"' is already defined"));
            return 1;
        }

        String extendClass = jmmNode.getOptional("extendedClass").orElse("");
        symbolTable.setClassSuper(extendClass);

        if (!extendClass.isEmpty()){
            if (!symbolTable.importsNames.contains(extendClass)){
                reports.add(SemanticHelper.createReport(jmmNode, "Class '"+ extendClass +"' is not defined"));
                return 1;
            }
        }


        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithVars(JmmNode jmmNode, List<Report> reports) {
        JmmNode typeNode = jmmNode.getJmmChild(0);
        Type type = new Type(typeNode.get("name"), (Boolean) typeNode.getObject("isArray"));
        Symbol varName = new Symbol(type, jmmNode.get("var"));

        // fields
        if (jmmNode.getJmmParent().getJmmParent().getKind().equals("ClassDeclaration")) {
            if (!symbolTable.addField(varName)){
                reports.add(SemanticHelper.createReport(jmmNode, "Variable '"+ varName.getName() +"' is already defined"));
                return -1;
            }

            return defaultVisit(jmmNode, reports);
        }

        String methodName;
        if (jmmNode.getJmmParent().getJmmParent().getKind().equals("MethodDeclaration"))
            methodName = jmmNode.getJmmParent().get("methodName");
        else if (jmmNode.getJmmParent().getJmmParent().getKind().equals("MainDeclaration"))
            methodName = "main";
        else
            throw new RuntimeException("Node Var must be child of MethodDeclaration or ClassDeclaration");

        //boolean createReport = !symbolTable.getMethodModifiers(methodName).contains(SemanticHelper.STATIC) && SemanticHelper.containVariable(symbolTable.fields, varName.getName());

        if (SemanticHelper.containVariable(symbolTable.getParameters(methodName), varName.getName()) ||
                !symbolTable.addMethodVariables(methodName, varName)){
            reports.add(SemanticHelper.createReport(jmmNode, "Variable '"+ varName.getName() +"' is already defined"));
            return 1;
        }



        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithMethods(JmmNode jmmNode, List<Report> reports) {

        if (jmmNode.getOptional("methodName").isPresent()) {
            JmmNode typeNode = jmmNode.getJmmChild(0);
            Type type = new Type(typeNode.get("name"), (Boolean) typeNode.getObject("isArray"));

            if (!(SemanticHelper.isValidType(type, symbolTable) || type.getName().equals(SemanticHelper.VOID))){
                reports.add(SemanticHelper.createReport(jmmNode, "Invalid type '"+ type.getName() + "'"));
                return 1;
            }

            String methodName = jmmNode.get("methodName");

            if (symbolTable.addMethod(methodName, type)){
                reports.add(SemanticHelper.createReport(jmmNode, "Method '"+ type.getName() + "' is already defined"));
                return 1;
            }

            boolean isStatic = jmmNode.get("isStatic").equals(SemanticHelper.TRUE);
            if (isStatic){
                symbolTable.addMethodModifiers(methodName, SemanticHelper.STATIC);
            }
        }
        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithMain(JmmNode jmmNode, List<Report> reports) {
        String typeName = jmmNode.get("typeName");

        if (!typeName.equals(SemanticHelper.STRING)) {
            reports.add(SemanticHelper.createReport(jmmNode, "The main method should receive a String[] as a parameter."));
            return 1;
        }

        Type returnType = new Type("void", false);
        if (symbolTable.addMethod("main", returnType)){
            reports.add(SemanticHelper.createReport(jmmNode, "The main method is already declared."));
            return 1;
        }

        Type stringType = new Type("String", true);
        Symbol symbol = new Symbol(stringType, jmmNode.get("name"));
        symbolTable.addMethodParameters("main", symbol);
        symbolTable.addMethodModifiers("main", SemanticHelper.STATIC);


        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithParam(JmmNode jmmNode, List<Report> reports) {

        String method = jmmNode.getJmmParent().getJmmParent().get("methodName");
        JmmNode typeNode = jmmNode.getJmmChild(0);

        Type type = new Type(typeNode.get("name"), (Boolean) typeNode.getObject("isArray"));
        Symbol varName = new Symbol(type, jmmNode.get("var"));

        if (!SemanticHelper.isValidType(type, symbolTable)){
            reports.add(SemanticHelper.createReport(jmmNode, "Invalid type'"+ type.getName() + "'"));
            return 1;
        }

        /*
        if (SemanticHelper.containVariable(symbolTable.fields, varName.getName())){
            reports.add(SemanticHelper.createReport(jmmNode, "Variable '"+ varName.getName() +"' is already defined"));
            return 1;
        }
        */


        if (!symbolTable.addMethodParameters(method, varName)){
            reports.add(SemanticHelper.createReport(jmmNode, "Parameter '"+ varName.getName() +"' is already defined"));
            return 1;
        }


        return defaultVisit(jmmNode, reports);
    }

    private Integer dealWithThis(JmmNode jmmNode, List<Report> reports) {


        String method = jmmNode.get("methodName");

        if (symbolTable.getMethodModifiers(method).contains(SemanticHelper.STATIC)){
            reports.add(SemanticHelper.createReport(jmmNode, "Cannot use 'this' in a Static method"));
            return 1;
        }

        return defaultVisit(jmmNode, reports);
    }

    private Integer defaultVisit(JmmNode jmmNode, List<Report> reports) {
        Integer errors = 0;
        for(JmmNode child : jmmNode.getChildren()) {
            if (jmmNode.hasAttribute("methodName"))
                child.put("methodName", jmmNode.get("methodName"));

            if (jmmNode.hasAttribute("isStatic"))
                child.put("isStatic", jmmNode.get("isStatic"));

        }

        return  errors;
    }

    private Integer dealWithVariable(JmmNode jmmNode, List<Report> reports) {
        String varName = jmmNode.get("var");

        // find in variables
        if (SemanticHelper.findVariable(symbolTable, jmmNode, varName))
            return defaultVisit(jmmNode, reports);

        // find in imports
        if (symbolTable.importsNames.contains(varName))
            return defaultVisit(jmmNode, reports);

        String error_msg = "Variable " + varName +  " is not declared";
        reports.add(SemanticHelper.createReport(jmmNode, error_msg));

        return  1 ;
    }
}