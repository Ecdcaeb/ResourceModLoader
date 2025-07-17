package rml.loader.api.config.v2.config.elements;

public class ConfigElement {
    protected ConfigGroup parent;
    protected final String name;
    protected final ConfigDescription description;

    public ConfigElement(String nameIn, ConfigDescription description) {
        this.parent = null;
        this.name = nameIn;
        this.description = description;
    }

    public void bind(ConfigGroup group) {
        this.parent = group;
    }

    public ConfigGroup getParent() {
        return parent;
    }

    public boolean isRequiresMcRestart() {
        return description.requiresMcRestart;
    }

    public boolean isRequiresWorldRestart() {
        return description.requiresWorldRestart;
    }

    public boolean isSlidingOption() {
        return description.slidingOption;
    }

    public String getDisplayName() {
        return description.displayName;
    }

    public String getLangKey() {
        return description.langKey;
    }

    public String getName() {
        return name;
    }

    public String[] getComment() {
        return description.comment;
    }

    public String getAbsoluteName(){
        return this.parent == null ? this.name : this.parent.name + "." + this.name;
    }
}
