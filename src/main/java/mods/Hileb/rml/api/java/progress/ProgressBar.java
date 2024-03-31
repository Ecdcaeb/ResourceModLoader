package mods.Hileb.rml.api.java.progress;

import it.unimi.dsi.fastutil.objects.Object2BooleanArrayMap;

import java.util.Map;

/**
 * @Project ResourceModLoader
 * @Author Hileb
 * @Date 2024/3/31 9:24
 **/
public class ProgressBar {
    public Object2BooleanArrayMap<String> progress;
    public ProgressBar(String[] progress){
        this.progress = new Object2BooleanArrayMap<>(progress, new boolean[progress.length]);
    }

    public void complete(String s){
        if (progress.containsKey(s)){
            this.progress.put(s, true);
        }else throw new IllegalArgumentException("progress must be defined ahead!");
    }

    public boolean isCompleted(){
        for (boolean b : this.progress.values()){
            if (!b) return false;
        }
        return true;
    }

    public String[] getCompleted(){
        return this.progress.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).distinct().toArray(String[]::new);
    }

    public String[] getFails(){
        return this.progress.entrySet().stream().filter((entry)->!entry.getValue()).map(Map.Entry::getKey).distinct().toArray(String[]::new);
    }

    public String getCompletedString(){
        return combineStringArray(this.getCompleted());
    }

    public String getFailsString(){
        return combineStringArray(this.getFails());
    }

    public static String combineStringArray(String[] strings){
        StringBuilder builder = new StringBuilder();
        for(int i = 0, size = strings.length; i< size; i++){
            builder.append(strings[i]).append(", ");
        }
        return builder.toString();
    }
}
