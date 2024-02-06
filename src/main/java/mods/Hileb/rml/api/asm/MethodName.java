package mods.Hileb.rml.api.asm;

import mods.Hileb.rml.api.EarlyClass;
import mods.Hileb.rml.api.PrivateAPI;
import mods.Hileb.rml.api.PublicAPI;
import org.objectweb.asm.tree.MethodNode;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2023/12/14 23:16
 **/
@EarlyClass
@PublicAPI
public class MethodName {
    @PublicAPI public String mcpName;@PublicAPI public String mcpDesc;
    @PublicAPI public String srgName;@PublicAPI public String srgDesc;
    @PublicAPI public String notchName;@PublicAPI public String notchDesc;
    @PrivateAPI MethodName(){}
    @PublicAPI public static Builder of(){return new Builder();}
    @PublicAPI public boolean is(MethodNode mn){return is(mn.name,mn.desc);}
    @PublicAPI public boolean is(String name,String desc){
        if (srgName.equals(name))return true;
        else if (mcpName.equals(name) && mcpDesc.equals(desc))return true;
        else return notchName.equals(name) && notchDesc.equals(desc);
    }
    @EarlyClass
    @PublicAPI
    public static class Builder{
        @PrivateAPI MethodName methodName;
        @PublicAPI public Builder(){
            methodName=new MethodName();
            methodName.notchName="<null>";
            methodName.mcpName="<null>";
            methodName.srgName="<null>";
            methodName.notchDesc="<null>";
            methodName.mcpDesc="<null>";
            methodName.srgDesc="<null>";
        }
        @PublicAPI public Builder mcp(String name,String desc){
            methodName.mcpName=name;
            methodName.mcpDesc=desc;
            return this;
        }
        @PublicAPI public Builder srg(String name,String desc){
            methodName.srgName=name;
            methodName.srgDesc=desc;
            return this;
        }
        @PublicAPI public Builder notch(String name,String desc){
            methodName.notchName=name;
            methodName.notchDesc=desc;
            return this;
        }
        @PublicAPI public MethodName build(){
            return methodName;
        }
    }
}
