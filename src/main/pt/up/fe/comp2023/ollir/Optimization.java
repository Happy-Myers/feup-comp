package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2023.otimization.Constants.ConstFolding;
import pt.up.fe.comp2023.otimization.Constants.ConstPropagationVisitor;
import pt.up.fe.comp2023.otimization.Constants.DeadCodeVisitor;
import pt.up.fe.comp2023.otimization.RegisterAllocation.RegisterAllocation;

import java.util.Collections;

public class Optimization implements JmmOptimization {
    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {

        if (!semanticsResult.getConfig().containsKey("optimize") || !semanticsResult.getConfig().get("optimize").equals("true"))
            return JmmOptimization.super.optimize(semanticsResult);

        ConstPropagationVisitor constPropagationVisitor = new ConstPropagationVisitor();
        ConstFolding constFolding = new ConstFolding();
        DeadCodeVisitor deadCodeVisitor = new DeadCodeVisitor(semanticsResult.getSymbolTable());

        boolean modifications ;
        do {
            modifications = constPropagationVisitor.visit(semanticsResult.getRootNode());
            System.out.println(modifications);
            modifications = constFolding.visit(semanticsResult.getRootNode()) || modifications;
            System.out.println(modifications);
            modifications = deadCodeVisitor.visit(semanticsResult.getRootNode()) || modifications;

            System.out.println("END");
        }while (modifications);

        return JmmOptimization.super.optimize(semanticsResult);
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        System.out.println("Generating OLLIR ...");

        OllirVisitor ollirVisitor = new OllirVisitor(jmmSemanticsResult.getSymbolTable());
        ollirVisitor.visit(jmmSemanticsResult.getRootNode());

        String ollirCode = ollirVisitor.getCode();

        System.out.println("ollir: \n" + ollirCode);

        return new OllirResult(jmmSemanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        String localVariablesN = ollirResult.getConfig().get("registerAllocation");
        int localVariables = localVariablesN == null ? -1 : Integer.parseInt(localVariablesN);

        if (localVariables == -1)
            return JmmOptimization.super.optimize(ollirResult);

        System.out.println("Optimizing OLLIR ... with n= " + localVariables);

        RegisterAllocation registerAllocation = new RegisterAllocation(ollirResult);
        registerAllocation.otimize(localVariables);

        return ollirResult;

    }
}
