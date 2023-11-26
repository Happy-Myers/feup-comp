package pt.up.fe.comp2023.otimization.Constants;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.analysis.SemanticHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class DeadCodeVisitor extends AJmmVisitor<Object, Boolean> {

    Map<String, JmmNode> assignValue;
    Map<String, Boolean> used;
    SymbolTable symbolTable;

    public DeadCodeVisitor(SymbolTable symbolTable){
        this.symbolTable = symbolTable;
        assignValue = new HashMap<>();
        used = new HashMap<>();
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethod);
        addVisit("Assign", this::visitAssign);
        addVisit("Var", this::visitVar);
        addVisit("If", this::visitIf);
        addVisit("While", this::visitWhile);

        setDefaultVisit(this::visitAll);
    }

    private Boolean visitWhile(JmmNode jmmNode, Object o) {

        JmmNode condition = jmmNode.getJmmChild(0);
        JmmNode code = jmmNode.getJmmChild(1);

        if ( condition.getKind().equals("Boolean")){
            if (condition.get("value").equals(SemanticHelper.FALSE)){
                jmmNode.getJmmParent().removeJmmChild(jmmNode);
                return true ;
            }
        }

        assignValue.clear();
        used.clear();

        return visitAll(code, o);
    }

    private Boolean visitIf(JmmNode jmmNode, Object o) {
        JmmNode condition = jmmNode.getJmmChild(0);
        JmmNode codeIf = jmmNode.getJmmChild(1);
        JmmNode codeElse = jmmNode.getJmmChild(2);

        if ( condition.getKind().equals("Boolean")){
            if (condition.get("value").equals(SemanticHelper.TRUE)){
                jmmNode.replace(codeIf);
                return true ;
            }

            if (condition.get("value").equals(SemanticHelper.FALSE)){
                jmmNode.replace(codeElse);
                return true ;
            }

        }

        assignValue.clear();
        used.clear();
        return visitAll(jmmNode, o);
    }

    private Boolean visitAll(JmmNode jmmNode, Object o) {

        boolean modifications = false;
        Iterator<JmmNode> it = jmmNode.getChildren().iterator();
        while (it.hasNext()) {
            JmmNode child = it.next();
            modifications = visit(child, o) || modifications;
        }

        return modifications;
    }

    private Boolean visitVar(JmmNode jmmNode, Object o) {

        JmmNode varNode = jmmNode.getJmmChild(0);
        String varName = varNode.get("var");

        if (used.containsKey(varName)) {
            used.put(varName, true);
        }

        return false;
    }

    private Boolean visitAssign(JmmNode jmmNode, Object o) {

        String varName = jmmNode.getJmmChild(0).get("var");
        JmmNode rhs = jmmNode.getJmmChild(1);

        String methodName = jmmNode.get("methodName");

        boolean modification = visitAll(rhs, o);

        // verificar se é local
        boolean isLocal = false;
        for (Symbol var : symbolTable.getLocalVariables(methodName)){
            if (var.getName().equals(varName)){
                isLocal = true;
                break;
            }
        }

        for (Symbol var : symbolTable.getParameters(methodName)){
            if (var.getName().equals(varName)){
                isLocal = true;
                break;
            }
        }
        if (!isLocal)
            return modification;



        if (assignValue.containsKey(varName)){
            // se antes houve um assign a esta variavel verificar se foi usada
            if (!used.containsKey(varName) || !used.get(varName)){
                // se nao foi usada remover o assign
                JmmNode assign = assignValue.get(varName);
                assign.getJmmParent().removeJmmChild(assign);
                used.remove(varName);
                assignValue.remove(varName);
                return true;
            }
        }

        assignValue.put(varName, jmmNode);

        if (hasCall(rhs))
            used.put(varName, true);
        else
            used.put(varName, false);

        return modification;

    }

    private boolean hasCall(JmmNode jmmNode){
        if (jmmNode.getKind().equals("Call"))
            return true;

        for (JmmNode child : jmmNode.getChildren()){
            if (hasCall(child))
                return true;
        }

        return false;
    }

    private Boolean visitMethod(JmmNode jmmNode, Object o) {
        assignValue.clear();
        used.clear();


        boolean modification = false;
        Iterator<JmmNode> iterator = jmmNode.getChildren().iterator();
        while (iterator.hasNext()){
            JmmNode child = iterator.next();
            if (child.getKind().equals("Statements") || child.getKind().equals("ReturnStatement")) {
                modification = visit(child, o) || modification;
            }
        }

        for (String varName : assignValue.keySet()){
            if (!used.get(varName)){
                JmmNode assign = assignValue.get(varName);
                assign.getJmmParent().removeJmmChild(assign);
                modification = true;
            }
        }

        return modification;
    }


}
