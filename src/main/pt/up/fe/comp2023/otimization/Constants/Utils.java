package pt.up.fe.comp2023.otimization.Constants;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2023.analysis.SemanticHelper;

public class Utils {

    public static JmmNode createNode(String kind, String value, String type, String isArray, String methodName , String isStatic){
        JmmNode result = new JmmNodeImpl(kind);
        result.put("value", value);
        result.put("type", type);
        result.put("isArray", isArray);
        if (methodName != null)
            result.put("methodName", methodName);
        if (isStatic != null)
            result.put("isStatic", isStatic);

        return result;
    }

    public static JmmNode createNode(boolean value, JmmNode oldNode){
        return createNode("Boolean", Boolean.toString(value), SemanticHelper.BOOL, SemanticHelper.FALSE,
                oldNode.get("methodName"), oldNode.get("isStatic"));
    }

    public static JmmNode createNode(int value, JmmNode oldNode){
        return createNode("Int", Integer.toString(value), SemanticHelper.INT, SemanticHelper.FALSE,
                oldNode.get("methodName"), oldNode.get("isStatic"));
    }
}
