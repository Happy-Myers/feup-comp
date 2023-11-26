package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.ArrayList;
import java.util.List;

public class Analysis implements JmmAnalysis {
    private SimpleSymbolTable symbolTable;

    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {
        this.symbolTable = new SimpleSymbolTable();
        List<Report> reportList = new ArrayList<>();

        SymbolTableVisitor symbolTableVisitor = new SymbolTableVisitor(symbolTable);
        symbolTableVisitor.visit(jmmParserResult.getRootNode(), reportList);

        if (reportList.isEmpty()) {
            TypeVisitor typeVisitor = new TypeVisitor(symbolTable);
            typeVisitor.visit(jmmParserResult.getRootNode(), reportList);
        }

        return new JmmSemanticsResult(jmmParserResult, symbolTable, reportList);
    }

}
