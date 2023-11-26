package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.List;

public class SemanticHelper {

    // types
    public static final String INT = "int";
    public static final String BOOL = "boolean";
    public static final String STRING = "String";
    public static final String VOID = "void";
    public static final String ANY = "?? ANY !!";
    public static final String MAYBE = "maybe";


    // booleans
    public static final String FALSE = Boolean.toString(false);
    public static final String TRUE = Boolean.toString(true);

    // modifiers
    public static final String STATIC = "static";

    public static Report createReport( JmmNode jmmNode, String error_msg ){

        return (Report.newError(Stage.SEMANTIC, Integer.parseInt(jmmNode.get("lineStart")),
                Integer.parseInt(jmmNode.get("colStart")), error_msg, new Exception(error_msg)));
    }

    public static boolean findVariable(SymbolTable symbolTable, JmmNode jmmNode, String varName){
        // find in local variables and method parameters
        if (containVariable(symbolTable.getLocalVariables(jmmNode.get("methodName")), varName) ||
                containVariable(symbolTable.getParameters(jmmNode.get("methodName")), varName))
            return true;

        if (jmmNode.getOptional("isStatic").orElse("").equals(FALSE)){
            // find in fields
            return containVariable(symbolTable.getFields(), varName);
        }

        return false;

    }
/*
    public static boolean findVariable(SymbolTable symbolTable, JmmNode jmmNode, String varName){
        if(jmmNode.getJmmChild(0).getKind().equals("mainDeclaration")){
            jmmNode = jmmNode.getJmmChild(0);
        }else if(containVariable(symbolTable.getFields(), varName)){
            return true;
        }

        return containVariable(symbolTable.getLocalVariables(jmmNode.get("methodName")), varName) || containVariable(symbolTable.getParameters(jmmNode.get("methodName")), varName);
    }
*/
    public static boolean sameType(JmmNode jmmNode, Type type){
        if (!jmmNode.get("type").equals(type.getName()) &&  !jmmNode.get("type").equals(ANY) && !type.getName().equals(ANY)  )
            return false;

        return jmmNode.get("isArray").equals(Boolean.toString(type.isArray())) ||
                jmmNode.get("isArray").equals(MAYBE);

    }

    public static boolean sameType(JmmNode jmmNode1, JmmNode jmmNode2){
        //Type type1 = new Type(jmmNode1.get("type"), jmmNode1.get("isArray").equals(TRUE));
        Type type2 = new Type(jmmNode2.get("type"), jmmNode2.get("isArray").equals(TRUE));

        return sameType(jmmNode1, type2);
    }

    public static boolean containVariable(List<Symbol> symbols, String var){
        if ( symbols == null)
            return false;

        for (Symbol symbol : symbols)
            if (symbol.getName().equals(var))
                return true;

        return false;
    }

    public static boolean isValidType(Type type, SimpleSymbolTable symbolTable){

        return  type.getName().equals(SemanticHelper.INT)   ||
                type.getName().equals(SemanticHelper.BOOL)  ||
                type.getName().equals(SemanticHelper.STRING)||
                type.getName().equals(symbolTable.className)||
                type.getName().equals(symbolTable.classSuper)||
                symbolTable.getImportsNames().contains(type.getName()) ;
    }

}
