package rml.loader.core;


import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * java.lang.VerifyError: Instruction type does not match stack map
 * Exception Details:
 *   Location:
 *     org/codehaus/groovy/runtime/InvokerHelper.createMap([Ljava/lang/Object;)Ljava/util/Map; @20: iload_2
 *   Reason:
 *     Type 'it/unimi/dsi/fastutil/objects/Object2ObjectLinkedOpenHashMap' (current frame, locals[1]) is not assignable to 'java/util/LinkedHashMap' (stack map, locals[1])
 *   Current Frame:
 *     bci: @20
 *     flags: { }
 *     locals: { '[Ljava/lang/Object;', 'it/unimi/dsi/fastutil/objects/Object2ObjectLinkedOpenHashMap', integer, integer }
 *     stack: { }
 *   Stackmap Frame:
 *     bci: @20
 *     flags: { }
 *     locals: { '[Ljava/lang/Object;', 'java/util/LinkedHashMap', integer, integer }
 *     stack: { }
 *   Bytecode:
 *     0x0000000: bb01 b659 2abe 056c b801 b8b7 01b9 4c03
 *     0x0000010: 3d2a be3e 1c1d 0464 a200 7d2a 1c32 c101
 *     0x0000020: 9299 005e 2a1c 0460 32c1 0009 9900 532a
 *     0x0000030: 1c04 6032 c000 093a 0419 04b9 01be 0100
 *     0x0000040: b901 8d01 003a 0519 05b9 013d 0100 9900
 *     0x0000050: 2b19 05b9 0140 0100 3a06 1906 c000 073a
 *     0x0000060: 072b 1907 b901 c101 0019 07b9 01c4 0100
 *     0x0000070: b901 c703 0057 a7ff d184 0202 a7ff 982b
 *     0x0000080: 2a1c 8402 0132 2a1c 8402 0132 b901 c703
 *     0x0000090: 0057 a7ff 822b b0
 *   Stackmap Table:
 *     append_frame(@20,Object[#443],Integer,Integer)
 *     append_frame(@71,Object[#9],Object[#314])
 *     same_frame(@121)
 *     chop_frame(@127,2)
 *     same_frame(@149)
 *
 * 	at groovy.lang.GroovyObjectSupport.getDefaultMetaClass(GroovyObjectSupport.java:46)
 * 	at groovy.lang.GroovyObjectSupport.<init>(GroovyObjectSupport.java:32)
 * 	at groovy.lang.Closure.<init>(Closure.java:215)
 * 	at groovy.lang.Closure.<init>(Closure.java:232)
 * 	at groovy.lang.Closure$1.<init>(Closure.java:197)
 * 	at groovy.lang.Closure.<clinit>(Closure.java:197)
 * 	at com.cleanroommc.groovyscript.mapper.ObjectMapperManager.init(ObjectMapperManager.java:77)
 * 	at com.cleanroommc.groovyscript.GroovyScript.initializeGroovyPreInit(GroovyScript.java:166)
 * 	at net.minecraftforge.fml.common.LoadController.handler$zzg000$preInit(LoadController.java:1018)
 * 	at net.minecraftforge.fml.common.LoadController.distributeStateMessage(LoadController.java)
 * 	at net.minecraftforge.fml.common.Loader.preinitializeMods(Loader.java:629)
 * 	at net.minecraftforge.fml.client.FMLClientHandler.beginMinecraftLoading(FMLClientHandler.java:252)
 * 	at net.minecraft.client.Minecraft.init(Minecraft.java:514)
 * 	at net.minecraft.client.Minecraft.run(Minecraft.java:422)
 * 	at net.minecraft.client.main.Main.main(Main.java:118)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
 * 	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 * 	at java.lang.reflect.Method.invoke(Method.java:498)
 * 	at net.minecraft.launchwrapper.Launch.launch(Launch.java:135)
 * 	at net.minecraft.launchwrapper.Launch.main(Launch.java:28)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
 * 	at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
 * 	at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
 * 	at java.lang.reflect.Method.invoke(Method.java:498)
 * 	at net.minecraftforge.gradle.GradleStartCommon.launch(GradleStartCommon.java:97)
 * 	at GradleStart.main(GradleStart.java:25)
 *
 * No Mixin Metadata is found in the Stacktrace.
 **/
// IDK why it/unimi/dsi/fastutil/objects/Object2ObjectLinkedOpenHashMap would be referenced.
public class InvokerHelperTransformer implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("org.codehaus.groovy.runtime.InvokerHelper".equals(transformedName)){
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(0);
            ClassVisitor visitor = new ClassRemapper(writer, new Remapper() {
                @Override
                public String map(String typeName) {
                    if (typeName == null) {
                        return null;
                    }
                    if (typeName.equals("it/unimi/dsi/fastutil/objects/Object2ObjectLinkedOpenHashMap")){
                        return "java/util/LinkedHashMap";
                    }

                    return typeName;
                }
            });
            try {
                reader.accept(visitor, ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            } catch (Exception e) {
                RMLFMLLoadingPlugin.LOGGER.warn("Couldn't remap class {}", transformedName, e);
                return basicClass;
            }
        }else return basicClass;
    }
}
