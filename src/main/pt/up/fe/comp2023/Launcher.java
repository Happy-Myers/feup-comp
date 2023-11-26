package pt.up.fe.comp2023;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.analysis.Analysis;
import pt.up.fe.comp2023.jasmin.MyJasminBackend;
import pt.up.fe.comp2023.ollir.Optimization;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));
        config.replace("otime", "true");

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();
        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);

        showErrors(parserResult.getReports());


        if(!parserResult.getReports().isEmpty()){
            return;
        }

        // Instantiate Analysis
        Analysis analysis = new Analysis();
        JmmSemanticsResult semanticsResult = analysis.semanticAnalysis(parserResult);
        showErrors(semanticsResult.getReports());

        if(!semanticsResult.getReports().isEmpty()){
            throw new RuntimeException("Semantic Errors Found");
        }

        Optimization optimizer = new Optimization();
        semanticsResult = optimizer.optimize(semanticsResult);

        var ollirResult = optimizer.toOllir(semanticsResult);

        if (!ollirResult.getReports().isEmpty()){
            throw new RuntimeException("Error generating Ollir");
        }
        ollirResult= optimizer.optimize(ollirResult);


        JasminBackend jasminBackend = new MyJasminBackend();

        var jasminResult = jasminBackend.toJasmin(ollirResult);

        if (!jasminResult.getReports().isEmpty()){
            throw new RuntimeException("Error generating Jasmin code");
        }

        jasminResult.compile(new File("programg.class"));


    }

    private static void showErrors(List<Report> reportList){
        for (Report report : reportList){
            System.out.println( report.getStage().toString() + " error in line " + report.getLine() + " and column " + report.getColumn());
            System.out.println("\t" + report.getMessage() + "\n");
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Check if there is at least one argument
        if (args.length != 1) {
            throw new RuntimeException("Expected a single argument, a path to an existing input file.");
        }

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        config.put("optimize", "true");
        config.put("registerAllocation", "0");
        config.put("debug", "false");

        return config;
    }

}
