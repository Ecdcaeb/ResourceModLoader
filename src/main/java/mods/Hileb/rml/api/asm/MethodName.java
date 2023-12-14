package mods.Hileb.rml.api.asm;

import org.objectweb.asm.tree.MethodNode;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:16
 **/
public class MethodName {
    public static final MethodName m_193061=of()
            .mcp("loadFunctions","()V")
            .srg("func_193061_h","()V")
            .notch("h","()V")
            .build();



























    public String mcpName;public String mcpDesc;
    public String srgName;public String srgDesc;
    public String notchName;public String notchDesc;
    MethodName(){}
    public static Builder of(){
        return new Builder();
    }
    public boolean is(MethodNode mn){
        return is(mn.name,mn.desc);
    }
    public boolean is(String name,String desc){
        if (srgName.equals(name))return true;
        else if (mcpName.equals(name) && mcpDesc.equals(desc))return true;
        else return notchName.equals(name) && notchDesc.equals(desc);
    }
    private static class Builder{
        MethodName methodName;
        public Builder(){
            methodName=new MethodName();
            methodName.notchName="<null>";
            methodName.mcpName="<null>";
            methodName.srgName="<null>";
            methodName.notchDesc="<null>";
            methodName.mcpDesc="<null>";
            methodName.srgDesc="<null>";
        }
        public Builder mcp(String name,String desc){
            methodName.mcpName=name;
            methodName.mcpDesc=desc;
            return this;
        }
        public Builder srg(String name,String desc){
            methodName.srgName=name;
            methodName.srgDesc=desc;
            return this;
        }
        public Builder notch(String name,String desc){
            methodName.notchName=name;
            methodName.notchDesc=desc;
            return this;
        }
        public MethodName build(){
            return methodName;
        }
    }
}
