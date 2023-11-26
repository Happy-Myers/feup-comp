package pt.up.fe.comp2023.ollir;

public class OllirMode {
    private final String type;
    private final boolean needTempVar;

    public OllirMode(String type, boolean needTempVar){
        this.type = type;
        this.needTempVar = needTempVar;
    }

    public OllirMode(boolean needTempVar){
        this(null, needTempVar);
    }

    public String getType() {
        return type;
    }

    public boolean isNeedTempVar() {
        return needTempVar;
    }
}
