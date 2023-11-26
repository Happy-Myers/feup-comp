package pt.up.fe.comp2023.otimization.Constants;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;
import pt.up.fe.comp2023.analysis.SemanticHelper;

public class ConstFolding extends PostorderJmmVisitor<Object, Boolean> {

    private boolean modifications = false;

    public ConstFolding(){
        buildVisitor();
    }

    @Override
    protected void buildVisitor() {
        addVisit("ArithmeticBinaryOP", this::visitArithmetics);
        addVisit("LogicalBinaryOP", this::visitLogicalBinary);
        addVisit("Neg", this::visitNeg);
        addVisit("Parenthesis", this::visitParenthesis);
        
        
        setDefaultVisit(this::visitDefault);

    }

    private Boolean visitParenthesis(JmmNode jmmNode, Object o) {

        JmmNode expr = jmmNode.getJmmChild(0);
        if (expr.getKind().equals("Boolean")) {
            JmmNode result = Utils.createNode(Boolean.parseBoolean(expr.get("value")), expr);
            jmmNode.replace(result);
            modifications = true;
        }else if (expr.getKind().equals("Int")) {
            int value = Integer.parseInt(expr.get("value"));
            JmmNode result = Utils.createNode(value, expr);
            jmmNode.replace(result);
            modifications = true;
        }

        return modifications;
    }

    private Boolean visitNeg(JmmNode jmmNode, Object o) {

        JmmNode expr = jmmNode.getJmmChild(0);
        if (expr.getKind().equals("Boolean") ) {
            boolean value = expr.get("value").equals(SemanticHelper.TRUE);

            JmmNode result =  Utils.createNode(!value, expr);
            jmmNode.replace(result);
            modifications = true;
        }


        return modifications;
    }

    private Boolean visitLogicalBinary(JmmNode jmmNode, Object o) {
        JmmNode left = jmmNode.getJmmChild(0);
        String operator = jmmNode.get("op");
        JmmNode right = jmmNode.getJmmChild(1);

        switch (operator) {
            case "&&" -> {
                // if both are constant booleans
                if (left.getKind().equals("Boolean") && right.getKind().equals("Boolean")) {
                    Boolean leftValue = left.get("value").equals(SemanticHelper.TRUE);
                    Boolean rightValue = right.get("value").equals(SemanticHelper.TRUE);

                    JmmNode result =  Utils.createNode(leftValue && rightValue, left);
                    jmmNode.replace(result);

                    modifications = true;
                }
                // todo
            }
            case "<" -> {
                // if both are int constants
                if (left.getKind().equals("Int") && right.getKind().equals("Int")){
                    int leftValue = Integer.parseInt(left.get("value"));
                    int rightValue = Integer.parseInt(right.get("value"));

                    JmmNode result = Utils.createNode(leftValue<rightValue , left);
                    jmmNode.replace(result);
                    modifications = true;
                }
            }
            default -> System.out.println("ERROR visitLogicalBinary");
        }

        return modifications;
    }

    private Boolean visitArithmetics(JmmNode jmmNode, Object o) {
        JmmNode left = jmmNode.getJmmChild(0);
        String operator = jmmNode.get("op");
        JmmNode right = jmmNode.getJmmChild(1);

        Integer leftValue = getValue(left);
        Integer rightValue = getValue(right);

        // if both are constants

        if (leftValue != null && rightValue != null){

            int value = 0;
            switch (operator) {
                case "+" -> value = leftValue + rightValue;
                case "-" -> value = leftValue - rightValue;
                case "*" -> value = leftValue * rightValue;
                case "/" -> value = leftValue / rightValue;
                default -> System.out.println("ERROR visitArithmetics");
            }
            JmmNode result = Utils.createNode(value, left);
            jmmNode.replace(result);
            modifications = true;
        }

        //todo



        return modifications;
    }

    private Integer getValue(JmmNode jmmNode){
        if (jmmNode.getKind().equals("Int"))
            return Integer.parseInt(jmmNode.get("value"));

        if (jmmNode.getKind().equals("Parenthesis")){
            JmmNode child = jmmNode.getJmmChild(0);
            if (child.getKind().equals("Int"))
                return Integer.parseInt(child.get("value"));
        }

        return null;
    }


    @Override
    public Boolean visit(JmmNode jmmNode) {
        modifications = false;
        super.visit(jmmNode);
        return modifications;
    }

    private Boolean visitDefault(JmmNode jmmNode, Object o) {
        return false;
    }



}
