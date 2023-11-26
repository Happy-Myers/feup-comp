package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.*;

public class SimpleSymbolTable implements SymbolTable {

    List<String> imports;
    List<String> importsNames;
    String className;
    String classSuper;
    List<Symbol> fields;
    Map<String, Type> methods;
    Map<String, List<String>> methodModifiers;
    Map<String, List<Symbol>> methodParameters;
    Map<String, List<Symbol>> methodLocalVariables;

    public SimpleSymbolTable(){
        imports = new ArrayList<>();
        importsNames = new ArrayList<>();
        methodModifiers = new HashMap<>();
        fields = new ArrayList<>();
        methods = new HashMap<>();
        methodParameters = new HashMap<>();
        methodLocalVariables = new HashMap<>();


    }

    @Override
    public List<String> getImports() {
        return imports;
    }


    public List<String> getImportsNames() {
        return importsNames;
    }

    public String getNameFromImport(String importStr){
        List<String> list = Arrays.stream(importStr.split("\\.")).toList();
        return list.get(list.size()-1);
    }


    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return classSuper;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods.keySet().stream().toList();
    }

    @Override
    public Type getReturnType(String s) {
        return methods.get(s);
    }

    @Override
    public List<Symbol> getParameters(String s) {
        return methodParameters.get(s);
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methodLocalVariables.get(s);
    }

    public boolean addImport(String importStr) {
        String importName = getNameFromImport(importStr);
        if (imports.contains(importStr) || importsNames.contains(importName))
            return false;
        imports.add(importStr);
        importsNames.add(importName);
        return true;
    }

    public void setClassName(String name) {
        this.className = name;
    }


    public void setClassSuper(String extendedClass) {
        this.classSuper = extendedClass;
    }

    public boolean addMethodVariables(String methodName, Symbol field) {
        if (methodLocalVariables.get(methodName).contains(field))
            return false;

        this.methodLocalVariables.get(methodName).add(field);
        return  true;
    }

    public boolean addMethodParameters(String methodName, Symbol parameter){
        if (this.methodParameters.get(methodName).contains(parameter))
            return false;

        this.methodParameters.get(methodName).add(parameter);
        return true;
    }

    public boolean addMethod(String methodName, Type returnType){
        if (this.methods.containsKey(methodName))
            return true;

        this.methods.put(methodName, returnType);

        this.methodLocalVariables.put(methodName, new ArrayList<>());
        this.methodParameters.put(methodName, new ArrayList<>());
        this.methodModifiers.put(methodName, new ArrayList<>());

        return false;
    }

    public boolean addField(Symbol e){
        if (fields.contains(e))
            return false;

        this.fields.add(e);
        return true;
    }

    public Symbol getVariable(String method, String varName){

        for (Symbol symbol : methodLocalVariables.get(method))
            if (symbol.getName().equals(varName))
                return symbol;

        for (Symbol symbol : methodParameters.get(method))
            if (symbol.getName().equals(varName))
                return symbol;

        for (Symbol symbol : fields)
            if (symbol.getName().equals(varName))
                return symbol;

        return null;
    }

    public String getOllirLikeReference(String method, String var){
        List<Symbol> parameters = methodParameters.get(method);

        int id = -1;

        for(int i = 0; i < parameters.size(); i++){
            if(parameters.get(i).getName().equals(var)){
                id = i +1;
                break;
            }
        }

        return id == -1 ? "" : "$" + id + ".";
    }

    public boolean isField(String method, String varName){

        boolean found = false;

        for (Symbol symbol : getLocalVariables(method))
            if (symbol.getName().equals(varName))
                found = true;

        for (Symbol symbol : getParameters(method))
            if (symbol.getName().equals(varName))
                found = true;

        if(found)
            return false;

        for (Symbol symbol : fields)
            if (symbol.getName().equals(varName))
                found = true;

        return found;
    }

    public boolean isImportedClass(String method, String var){
        List<Symbol> params = getParameters(method);
        List<Symbol> local = getLocalVariables(method);

        for (Symbol symbol : local)
            if (symbol.getName().equals(var))
                return false;

        for (Symbol symbol : params)
            if (symbol.getName().equals(var))
                return false;

        for (Symbol symbol : fields)
            if (symbol.getName().equals(var))
                return false;

        for(String importName : getImports())
            if(importName.endsWith(var))
                return true;

        return false;
    }

    public boolean methodExists(String method){
        return methods.containsKey(method);
    }

    public void addMethodModifiers(String method, String modifier){
        methodModifiers.get(method).add(modifier);
    }

    public List<String> getMethodModifiers(String method){
        return methodModifiers.get(method);
    }

    @Override
    public String toString() {
        return print();
    }
}

