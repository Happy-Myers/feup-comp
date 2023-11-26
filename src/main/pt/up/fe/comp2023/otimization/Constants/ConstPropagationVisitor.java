package pt.up.fe.comp2023.otimization.Constants;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.analysis.SemanticHelper;

public class ConstPropagationVisitor extends AJmmVisitor<ConstPropagationData, Boolean> {

    private boolean alterar;

    public ConstPropagationVisitor() {
        buildVisitor();
        alterar = true;
    }


    @Override
    protected void buildVisitor() {
        addVisit("MethodDeclaration", this::visitMethods);
        addVisit("MainDeclaration", this::visitMethods);
        addVisit("Assign", this::visitAssign);
        addVisit("ArrayAssign", this::visitArrayAssign);
        addVisit("Array", this::visitArray);
        addVisit("Var", this::visitVar);
        addVisit("If", this::visitIf);
        addVisit("While", this::visitWhile);

        setDefaultVisit(this::visitAll);
    }

    private Boolean visitArray(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        boolean modifications = visitAll(jmmNode, constPropagationData);
        if (!alterar){
            return modifications;
        }

        JmmNode varNode = jmmNode.getJmmChild(0);
        JmmNode indexNode = jmmNode.getJmmChild(1);

        if (varNode.getKind().equals("Var") && indexNode.getKind().equals("Int")){
            varNode = varNode.getJmmChild(0);
            String varName = varNode.get("var");
            String index = indexNode.get("value");

            String arrayName = varName + "[" + index + "]";
            String constantValue = constPropagationData.getConstant(arrayName);

            if (constantValue == null){
                return modifications;
            }

            JmmNode newNode = null;

            if (SemanticHelper.sameType(jmmNode, new Type(SemanticHelper.INT, false))){
                newNode = Utils.createNode(Integer.parseInt(constantValue), jmmNode );
            }else  if (SemanticHelper.sameType(jmmNode, new Type(SemanticHelper.BOOL, false))){
                newNode = Utils.createNode(Boolean.parseBoolean(constantValue), jmmNode );
            }

            jmmNode.replace(newNode);

            return true;
        }

        return modifications;
    }

    private Boolean visitArrayAssign(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        boolean modifications = visitAll(jmmNode, constPropagationData);
        String varName = jmmNode.getJmmChild(0).get("var");
        JmmNode index = jmmNode.getJmmChild(1);
        JmmNode rhs = jmmNode.getJmmChild(2);

        if (index.getKind().equals("Int")){
            String arrayName = varName + "[" + index.get("value") + "]";

            if (rhs.getKind().equals("Int") || rhs.getKind().equals("Boolean")){
                constPropagationData.putConstant(arrayName, rhs.get("value"));
            }else{
                constPropagationData.removeConstant(arrayName);
            }
        }

        return modifications;
    }

    private Boolean visitWhile(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        JmmNode condition = jmmNode.getJmmChild(0);
        JmmNode code = jmmNode.getJmmChild(1);

        boolean alterarOld = alterar;

        alterar = false;

        // visitar para ver se altera o ConstPropagationData
        ConstPropagationData dataCode = new ConstPropagationData(constPropagationData);
        visit(code, dataCode);

        alterar = alterarOld;

        // alterar o ConstPropagationData para nao usar as variaveis que foram alteradas no while
        constPropagationData.intersection(dataCode);
        boolean modifications = visit(condition, constPropagationData);

        modifications = visit(code, dataCode) || modifications;

        constPropagationData.replace(dataCode);

        return modifications;
    }

    private Boolean visitIf(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        JmmNode condition = jmmNode.getJmmChild(0);
        boolean modifications = visit(condition, constPropagationData);

        JmmNode ifCode = jmmNode.getJmmChild(1);
        JmmNode elseCode = jmmNode.getJmmChild(2);



        ConstPropagationData dataIf = new ConstPropagationData(constPropagationData);
        modifications = visit(ifCode, dataIf) || modifications;

        ConstPropagationData dataElse = new ConstPropagationData(constPropagationData);
        modifications = visit(elseCode, dataElse) || modifications;

        dataIf.intersection(dataElse);

        constPropagationData.replace(dataIf);

        return modifications;

    }

    private Boolean visitVar(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        if (!alterar){
            return false;
        }

        JmmNode varNode = jmmNode.getJmmChild(0);
        String varName = varNode.get("var");
        String constantValue = constPropagationData.getConstant(varName);
        if (constantValue == null){
            return false;
        }

        JmmNode newNode = null;

        if (SemanticHelper.sameType(jmmNode, new Type(SemanticHelper.INT, false))){
            newNode = Utils.createNode(Integer.parseInt(constantValue), jmmNode );
        }else  if (SemanticHelper.sameType(jmmNode, new Type(SemanticHelper.BOOL, false))){
            newNode = Utils.createNode(Boolean.parseBoolean(constantValue), jmmNode );
        }

        jmmNode.replace(newNode);

        return true;
    }

    private Boolean visitAssign(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        boolean modifications = visitAll(jmmNode, constPropagationData);

        String varName = jmmNode.getJmmChild(0).get("var");

        JmmNode rhs = jmmNode.getJmmChild(1);

        if (rhs.getKind().equals("Int") || rhs.getKind().equals("Boolean")){
            constPropagationData.putConstant(varName, rhs.get("value"));
        }else{
            constPropagationData.removeConstant(varName);
        }


        return modifications;
    }

    private Boolean visitMethods(JmmNode jmmNode, ConstPropagationData constPropagationData) {
        ConstPropagationData newData = new ConstPropagationData();
        Boolean modifications = false;
        for (JmmNode node : jmmNode.getChildren()){
            if (node.getKind().equals("Statements") || node.getKind().equals("ReturnStatement") || node.getKind().equals("MainDeclaration"))
                modifications = visit(node, newData) || modifications;
        }

        return modifications;
    }

    private Boolean visitAll(JmmNode jmmNode, ConstPropagationData constPropagationData){
        boolean modifications = false;
        for (JmmNode node : jmmNode.getChildren())
            modifications = visit(node, constPropagationData) || modifications;
        return modifications;
    }

}
