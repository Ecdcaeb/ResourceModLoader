package rml.loader.content.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import rml.deserializer.AbstractDeserializer;
import rml.deserializer.DeserializerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Public recursive value codecs shared by worldgen definitions and other native modules. */
public final class WorldgenCodecs {
    private WorldgenCodecs() { }

    @FunctionalInterface public interface DensityFunction { double sample(long seed, int x, int y, int z); }
    @FunctionalInterface public interface Noise { double sample(long seed,double x,double y,double z); }
    @FunctionalInterface public interface BlockStateProvider { IBlockState provide(Random random, BlockPos position); }
    @FunctionalInterface public interface MaterialCondition { boolean test(MaterialContext context); }
    @FunctionalInterface public interface MaterialRule { IBlockState apply(MaterialContext context); }
    public interface MaterialContext { long seed(); int x(); int y(); int z(); IBlockState current(); Random random(); BlockPos position(); }
    public interface TemplateProcessor extends net.minecraft.world.gen.structure.template.ITemplateProcessor {
        @Override net.minecraft.world.gen.structure.template.Template.BlockInfo processBlock(net.minecraft.world.World world,BlockPos position,net.minecraft.world.gen.structure.template.Template.BlockInfo block);
        default BlockPos transformOrigin(BlockPos origin) { return origin; }
        default net.minecraft.util.Rotation rotation() { return net.minecraft.util.Rotation.NONE; }
        default BlockPos translation() { return BlockPos.ORIGIN; }
    }

    /** Registers operators on the caller's existing decoder manager; safe before world initialization. */
    public static void registerDecoders(DeserializerManager manager) { registerDecoders(manager,id->null); }
    public static void registerDecoders(DeserializerManager manager,java.util.function.Function<ResourceLocation,Noise> noiseLookup) {
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "density_constant"), DensityFunction.class,
                json -> { double value=json.isJsonPrimitive()?json.getAsDouble():number(json.getAsJsonObject(),"value"); return (seed,x,y,z)->value; }));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "value_noise"), Noise.class,
                json -> {
                    JsonObject object=json.getAsJsonObject();
                    if(object.has("amplitudes")) {
                        JsonArray amplitudes=object.getAsJsonArray("amplitudes"); if(amplitudes.size()==0) throw new IllegalArgumentException("noise amplitudes must not be empty");
                        int first=object.has("first_octave")?integer(object,"first_octave"):0; double[] scales=new double[amplitudes.size()],weights=new double[amplitudes.size()];
                        for(int i=0;i<amplitudes.size();i++) { scales[i]=Math.pow(2.0D,first+i); weights[i]=amplitudes.get(i).getAsDouble(); }
                        return (seed,x,y,z)->{ double sum=0; for(int i=0;i<scales.length;i++) sum+=valueNoise(seed,x*scales[i],y*scales[i],z*scales[i])*weights[i]; return sum; };
                    }
                    double scale=number(object,"scale"),amplitude=number(object,"amplitude"); if(scale<=0) throw new IllegalArgumentException("noise scale must be positive");
                    return (seed,x,y,z)->valueNoise(seed,x*scale,y*scale,z*scale)*amplitude;
                }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "density_y_gradient"), DensityFunction.class,
                json -> { JsonObject object=json.getAsJsonObject(); int from=integer(object,"from_y"),to=integer(object,"to_y"); double a=number(object,"from_value"),b=number(object,"to_value"); if(to<=from) throw new IllegalArgumentException("to_y must exceed from_y"); return (seed,x,y,z)->a+(b-a)*MathHelper.clamp((double)(y-from)/(to-from),0.0D,1.0D); }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "density_add"), DensityFunction.class,
                json -> { JsonArray array=json.getAsJsonObject().getAsJsonArray("terms"); if(array.size()<2) throw new IllegalArgumentException("density_add requires at least two terms"); List<DensityFunction> terms=decodeArray(manager,array,DensityFunction.class); return (seed,x,y,z)->{ double value=0; for(DensityFunction term:terms) value+=term.sample(seed,x,y,z); return value; }; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "density_multiply"), DensityFunction.class,
                json -> { JsonObject object=json.getAsJsonObject(); DensityFunction left=manager.decode(DensityFunction.class,object.get("left")); DensityFunction right=manager.decode(DensityFunction.class,object.get("right")); return (seed,x,y,z)->left.sample(seed,x,y,z)*right.sample(seed,x,y,z); }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "density_noise"), DensityFunction.class,
                json -> {
                    JsonObject object=json.getAsJsonObject(); double scale=number(object,"scale"),amplitude=object.has("amplitude")?number(object,"amplitude"):1.0D;
                    if(scale<=0) throw new IllegalArgumentException("scale must be positive");
                    Noise noise;
                    if(object.has("noise")&&object.get("noise").isJsonPrimitive()&&object.getAsJsonPrimitive("noise").isString()) {
                        ResourceLocation id=new ResourceLocation(object.get("noise").getAsString()); noise=noiseLookup.apply(id);
                        if(noise==null) throw new IllegalArgumentException("unknown noise " + id);
                    } else noise=object.has("noise")?manager.decode(Noise.class,object.get("noise")):(seed,x,y,z)->valueNoise(seed,x,y,z);
                    Noise source=noise; return (seed,x,y,z)->source.sample(seed,x*scale,y*scale,z*scale)*amplitude;
                }));

        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "simple_state"), BlockStateProvider.class,
                json -> { JsonObject object=json.getAsJsonObject(); JsonElement state=object.has("Name")?object:object.has("state")?object.get("state"):object.has("config")?object.get("config").getAsJsonObject().get("state"):null; if(state==null) throw new IllegalArgumentException("simple_state requires state"); IBlockState compiled=WorldgenModule.resolveBlockState(state); return (random,pos)->compiled; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "weighted_state"), BlockStateProvider.class,
                json -> {
                    java.lang.reflect.Type weightedType = new com.google.gson.reflect.TypeToken<rml.deserializer.value.WeightedList<BlockStateProvider>>() { }.getType();
                    @SuppressWarnings("unchecked") rml.deserializer.value.WeightedList<BlockStateProvider> providers = (rml.deserializer.value.WeightedList<BlockStateProvider>) manager.decode(weightedType, json.getAsJsonObject().get("entries"));
                    return (random,pos) -> providers.sample(random).provide(random,pos);
                }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "condition_y_above"), MaterialCondition.class,
                json -> { JsonObject object=json.getAsJsonObject(); int y=integer(object,"y"); boolean inclusive=!object.has("inclusive")||object.get("inclusive").getAsBoolean(); return context->inclusive?context.y()>=y:context.y()>y; }));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "condition_noise_threshold"), MaterialCondition.class,
                json -> { JsonObject object=json.getAsJsonObject(); double scale=number(object,"scale"),threshold=number(object,"threshold"); if(scale<=0) throw new IllegalArgumentException("scale must be positive"); return context->valueNoise(context.seed(),context.x()*scale,context.y()*scale,context.z()*scale)>threshold; }));
        manager.addDefaultEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "material_state"), MaterialRule.class,
                json -> { BlockStateProvider provider=manager.decode(BlockStateProvider.class,json.getAsJsonObject().get("provider")); return context->provider.provide(context.random(),context.position()); }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "material_condition"), MaterialRule.class,
                json -> { JsonObject object=json.getAsJsonObject(); MaterialCondition condition=manager.decode(MaterialCondition.class,object.get("if")); MaterialRule thenRule=manager.decode(MaterialRule.class,object.get("then")); MaterialRule elseRule=object.has("else")?manager.decode(MaterialRule.class,object.get("else")):context->null; return context->condition.test(context)?thenRule.apply(context):elseRule.apply(context); }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "ignore_block"), TemplateProcessor.class,
                json -> { Block block=block(json.getAsJsonObject(),"block"); return (world,pos,info)->info.blockState.getBlock()==block?null:info; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "replace_block"), TemplateProcessor.class,
                json -> { JsonObject object=json.getAsJsonObject(); Block from=block(object,"from"); IBlockState to=manager.decode(IBlockState.class,object.get("to")); return (world,pos,info)->info.blockState.getBlock()==from?new net.minecraft.world.gen.structure.template.Template.BlockInfo(info.pos,to,info.tileentityData):info; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "protect_block"), TemplateProcessor.class,
                json -> { Block protectedBlock=block(json.getAsJsonObject(),"block"); return (world,pos,info)->world.getBlockState(pos).getBlock()==protectedBlock?null:info; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "position"), TemplateProcessor.class,
                json -> { JsonObject object=json.getAsJsonObject(); int x=integer(object,"x"),y=integer(object,"y"),z=integer(object,"z"); return new TemplateProcessor(){ @Override public net.minecraft.world.gen.structure.template.Template.BlockInfo processBlock(net.minecraft.world.World world,BlockPos position,net.minecraft.world.gen.structure.template.Template.BlockInfo info){return info;} @Override public BlockPos translation(){return new BlockPos(x,y,z);} }; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "rotate"), TemplateProcessor.class,
                json -> { net.minecraft.util.Rotation rotation=net.minecraft.util.Rotation.valueOf(json.getAsJsonObject().get("rotation").getAsString().toUpperCase(java.util.Locale.ROOT)); return new TemplateProcessor(){ @Override public net.minecraft.world.gen.structure.template.Template.BlockInfo processBlock(net.minecraft.world.World world,BlockPos position,net.minecraft.world.gen.structure.template.Template.BlockInfo info){return info;} @Override public net.minecraft.util.Rotation rotation(){return rotation;} }; }));
        manager.addEntry(new AbstractDeserializer<>(new ResourceLocation("rml", "rule"), TemplateProcessor.class,
                json -> { java.util.List<TemplateProcessor> processors=new java.util.ArrayList<>(); for(JsonElement rule:json.getAsJsonObject().getAsJsonArray("rules")){ JsonObject object=rule.getAsJsonObject(); Block matcher=block(object,"if_block"); TemplateProcessor then=manager.decode(TemplateProcessor.class,object.get("then")); processors.add((world,pos,info)->info.blockState.getBlock()==matcher?then.processBlock(world,pos,info):info); } return (world,pos,info)->{ net.minecraft.world.gen.structure.template.Template.BlockInfo current=info; for(TemplateProcessor processor:processors){if(current==null) return null;current=processor.processBlock(world,pos,current);} return current; }; }));
    }

    public static Noise decodeNoise(DeserializerManager manager, JsonElement json) throws rml.deserializer.JsonDeserializeException { return manager.decode(Noise.class,json); }
    public static DensityFunction decodeDensityFunction(DeserializerManager manager, JsonElement json) throws rml.deserializer.JsonDeserializeException { return manager.decode(DensityFunction.class,json); }
    public static BlockStateProvider decodeBlockStateProvider(DeserializerManager manager, JsonElement json) throws rml.deserializer.JsonDeserializeException { return manager.decode(BlockStateProvider.class,json); }
    public static MaterialCondition decodeMaterialCondition(DeserializerManager manager, JsonElement json) throws rml.deserializer.JsonDeserializeException { return manager.decode(MaterialCondition.class,json); }
    public static MaterialRule decodeMaterialRule(DeserializerManager manager, JsonElement json) throws rml.deserializer.JsonDeserializeException { return manager.decode(MaterialRule.class,json); }

    public static TemplateProcessor decodeProcessorList(DeserializerManager manager,JsonElement definition) throws rml.deserializer.JsonDeserializeException {
        java.lang.reflect.Type listType=new com.google.gson.reflect.TypeToken<List<TemplateProcessor>>() { }.getType();
        @SuppressWarnings("unchecked") List<TemplateProcessor> processors=(List<TemplateProcessor>)manager.decode(listType,definition.getAsJsonObject().get("processors"));
        return new TemplateProcessor(){
            @Override public net.minecraft.world.gen.structure.template.Template.BlockInfo processBlock(net.minecraft.world.World world,BlockPos position,net.minecraft.world.gen.structure.template.Template.BlockInfo info){net.minecraft.world.gen.structure.template.Template.BlockInfo current=info;for(TemplateProcessor processor:processors){if(current==null)return null;current=processor.processBlock(world,position,current);}return current;}
            @Override public BlockPos transformOrigin(BlockPos origin){BlockPos offset=BlockPos.ORIGIN;net.minecraft.util.Rotation current=net.minecraft.util.Rotation.NONE;for(TemplateProcessor processor:processors){net.minecraft.util.Rotation next=processor.rotation();if(next!=net.minecraft.util.Rotation.NONE){offset=rotate(offset,next);current=compose(current,next);}offset=offset.add(processor.translation());}return origin.add(offset);}
            @Override public net.minecraft.util.Rotation rotation(){net.minecraft.util.Rotation result=net.minecraft.util.Rotation.NONE;for(TemplateProcessor processor:processors)result=compose(result,processor.rotation());return result;}
        };
    }
    private static net.minecraft.util.Rotation compose(net.minecraft.util.Rotation first,net.minecraft.util.Rotation second){
        int a=first==net.minecraft.util.Rotation.NONE?0:first==net.minecraft.util.Rotation.CLOCKWISE_90?1:first==net.minecraft.util.Rotation.CLOCKWISE_180?2:3;
        int b=second==net.minecraft.util.Rotation.NONE?0:second==net.minecraft.util.Rotation.CLOCKWISE_90?1:second==net.minecraft.util.Rotation.CLOCKWISE_180?2:3;
        switch((a+b)&3){case 1:return net.minecraft.util.Rotation.CLOCKWISE_90;case 2:return net.minecraft.util.Rotation.CLOCKWISE_180;case 3:return net.minecraft.util.Rotation.COUNTERCLOCKWISE_90;default:return net.minecraft.util.Rotation.NONE;}
    }
    private static BlockPos rotate(BlockPos pos,net.minecraft.util.Rotation rotation){switch(rotation){case CLOCKWISE_90:return new BlockPos(-pos.getZ(),pos.getY(),pos.getX());case CLOCKWISE_180:return new BlockPos(-pos.getX(),pos.getY(),-pos.getZ());case COUNTERCLOCKWISE_90:return new BlockPos(pos.getZ(),pos.getY(),-pos.getX());default:return pos;}}
    private static <T> List<T> decodeArray(DeserializerManager manager,JsonArray array,Class<T> type) throws rml.deserializer.JsonDeserializeException {
        List<T> result=new ArrayList<>(array.size()); for(JsonElement element:array) result.add(manager.decode(type,element)); return result;
    }
    private static Block block(JsonObject json,String key) {
        if(!json.has(key)||!json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing block id '"+key+"'");
        ResourceLocation id=new ResourceLocation(json.get(key).getAsString()); Block value=Block.REGISTRY.getObject(id);
        if(value==null) throw new IllegalArgumentException("unknown block "+id);
        return value;
    }
    private static double number(JsonObject json,String key) { if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("missing numeric '"+key+"'"); return json.get(key).getAsDouble(); }
    private static int integer(JsonObject json,String key) { if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("missing integer '"+key+"'"); return json.get(key).getAsInt(); }
    private static double valueNoise(long seed,double x,double y,double z) {
        int ix=floor(x),iy=floor(y),iz=floor(z); double fx=fade(x-ix),fy=fade(y-iy),fz=fade(z-iz);
        double x00=mix(lattice(seed,ix,iy,iz),lattice(seed,ix+1,iy,iz),fx),x10=mix(lattice(seed,ix,iy+1,iz),lattice(seed,ix+1,iy+1,iz),fx);
        double x01=mix(lattice(seed,ix,iy,iz+1),lattice(seed,ix+1,iy,iz+1),fx),x11=mix(lattice(seed,ix,iy+1,iz+1),lattice(seed,ix+1,iy+1,iz+1),fx);
        return mix(mix(x00,x10,fy),mix(x01,x11,fy),fz);
    }
    private static double lattice(long seed,int x,int y,int z) { long h=seed^((long)x*0x9E3779B97F4A7C15L)^((long)y*0xC2B2AE3D27D4EB4FL)^((long)z*0x165667B19E3779F9L);h=(h^(h>>>30))*0xBF58476D1CE4E5B9L;h=(h^(h>>>27))*0x94D049BB133111EBL;h^=h>>>31;return ((h>>>11)*0x1.0p-52)-1.0D; }
    private static int floor(double value) { int i=(int)value; return value<i?i-1:i; }
    private static double fade(double value) { return value*value*(3-2*value); }
    private static double mix(double a,double b,double t) { return a+(b-a)*t; }
}
