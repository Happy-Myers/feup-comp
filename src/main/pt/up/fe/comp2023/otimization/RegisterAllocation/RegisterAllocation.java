package pt.up.fe.comp2023.otimization.RegisterAllocation;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;

public class RegisterAllocation {

    private final OllirResult ollirResult;
    private final ClassUnit classUnit;

    public RegisterAllocation(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.classUnit = ollirResult.getOllirClass();

    }

    public void otimize(int maxRegisters) {
        ollirResult.getOllirClass().buildCFGs();
        ollirResult.getOllirClass().buildVarTables();


        for (Method method : classUnit.getMethods()){
            LivenessAnalysis livenessAnalysis = new LivenessAnalysis(method);
            livenessAnalysis.analyze();

            InferenceGraph inferenceGraph = new InferenceGraph(livenessAnalysis);
            inferenceGraph.build();

            int numberOfRegisters = inferenceGraph.colorize(maxRegisters);
            if ( maxRegisters != 0 && numberOfRegisters > maxRegisters){
                String message ="The number of registers is greater than the maximum allowed." +
                        " The min number of registers is " + numberOfRegisters + " and the maximum allowed is " + maxRegisters + ".";
                ollirResult.getReports().add(Report.newError(Stage.OPTIMIZATION,-1,-1, message, new RuntimeException(message)));
                return;
            }
        }

    }
}
