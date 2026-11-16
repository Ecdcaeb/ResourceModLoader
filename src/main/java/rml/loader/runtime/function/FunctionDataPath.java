package rml.loader.runtime.function;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/** Cached-free parser for native NBT path syntax used by a single data command. */
public final class FunctionDataPath {
    private FunctionDataPath() { }

    public static NBTBase get(NBTTagCompound root, String path) {
        List<Object> parts = parse(path);
        NBTBase current = root;
        for (Object part : parts) {
            if (part instanceof String) {
                if (!(current instanceof NBTTagCompound)) return null;
                NBTTagCompound compound = (NBTTagCompound) current;
                if (!compound.hasKey((String) part)) return null;
                current = compound.getTag((String) part);
            } else if (part instanceof Integer) {
                if (!(current instanceof NBTTagList)) return null;
                NBTTagList list = (NBTTagList) current;
                int index = (Integer) part;
                if (index < 0 || index >= list.tagCount()) return null;
                current = list.get(index);
            } else {
                if (!(current instanceof NBTTagList)) return null;
                current = firstMatch((NBTTagList) current, ((MatchPart) part).compound);
                if (current == null) return null;
            }
        }
        return current;
    }

    public static void set(NBTTagCompound root, String path, NBTBase value) {
        List<Object> parts = parse(path);
        NBTBase parent = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            Object part = parts.get(i);
            Object next = parts.get(i + 1);
            if (part instanceof String) {
                NBTTagCompound compound = requireCompound(parent, path);
                String key = (String) part;
                NBTBase child = compound.getTag(key);
                if (child == null || !(child instanceof NBTTagCompound || child instanceof NBTTagList)) {
                    child = next instanceof String ? new NBTTagCompound() : new NBTTagList();
                    compound.setTag(key, child);
                }
                parent = child;
            } else if (part instanceof Integer) {
                NBTTagList list = requireList(parent, path);
                int index = (Integer) part;
                if (index < 0 || index > list.tagCount()) throw invalid(path);
                if (index == list.tagCount()) list.appendTag(next instanceof String ? new NBTTagCompound() : new NBTTagList());
                parent = list.get(index);
            } else {
                NBTTagList list = requireList(parent, path);
                NBTTagCompound match = ((MatchPart) part).compound;
                NBTBase selected = firstMatch(list, match);
                if (selected == null) {
                    if (list.tagCount() != 0 && list.getTagType() != match.getId()) throw invalid(path);
                    selected = match.copy();
                    list.appendTag(selected);
                }
                parent = selected;
            }
        }
        Object last = parts.get(parts.size() - 1);
        if (last instanceof String) requireCompound(parent, path).setTag((String) last, value);
        else if (last instanceof Integer) {
            NBTTagList list = requireList(parent, path);
            int index = (Integer) last;
            if (index < 0 || index > list.tagCount()) throw invalid(path);
            if (index == list.tagCount()) list.appendTag(value);
            else list.set(index, value);
        } else {
            NBTTagList list = requireList(parent, path);
            NBTTagCompound match = ((MatchPart) last).compound;
            boolean changed = false;
            if (list.tagCount() != 0 && list.getTagType() != value.getId()) throw invalid(path);
            for (int i = 0; i < list.tagCount(); i++) if (matches(match, list.get(i))) { list.set(i, value.copy()); changed = true; }
            if (!changed) throw invalid(path);
        }
    }

    public static boolean remove(NBTTagCompound root, String path) {
        List<Object> parts = parse(path);
        NBTBase parent = root;
        for (int i = 0; i < parts.size() - 1; i++) {
            Object part = parts.get(i);
            if (part instanceof String) {
                if (!(parent instanceof NBTTagCompound)) return false;
                parent = ((NBTTagCompound) parent).getTag((String) part);
            } else if (part instanceof Integer) {
                if (!(parent instanceof NBTTagList)) return false;
                NBTTagList list = (NBTTagList) parent;
                int index = (Integer) part;
                if (index < 0 || index >= list.tagCount()) return false;
                parent = list.get(index);
            } else {
                if (!(parent instanceof NBTTagList)) return false;
                parent = firstMatch((NBTTagList) parent, ((MatchPart) part).compound);
            }
            if (parent == null) return false;
        }
        Object last = parts.get(parts.size() - 1);
        if (last instanceof String) {
            if (!(parent instanceof NBTTagCompound)) return false;
            NBTTagCompound compound = (NBTTagCompound) parent;
            if (!compound.hasKey((String) last)) return false;
            compound.removeTag((String) last);
            return true;
        }
        if (!(parent instanceof NBTTagList)) return false;
        NBTTagList list = (NBTTagList) parent;
        if (last instanceof Integer) {
            int index = (Integer) last;
            if (index < 0 || index >= list.tagCount()) return false;
            list.removeTag(index);
            return true;
        }
        NBTTagCompound match = ((MatchPart) last).compound;
        boolean changed = false;
        for (int i = list.tagCount() - 1; i >= 0; i--) if (matches(match, list.get(i))) { list.removeTag(i); changed = true; }
        return changed;
    }

    private static List<Object> parse(String path) {
        if (path == null || path.isEmpty()) throw invalid(path);
        List<Object> result = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean afterDot = false;
        for (int i = 0; i < path.length();) {
            char c = path.charAt(i);
            if (c == '.') {
                if (key.length() != 0) addKey(result, key, path);
                else if (result.isEmpty() || afterDot) throw invalid(path);
                afterDot = true;
                i++;
            } else if (c == '[') {
                if (key.length() != 0) addKey(result, key, path);
                afterDot = false;
                if (i + 1 < path.length() && path.charAt(i + 1) == '{') {
                    int close = compoundEnd(path, i + 1);
                    if (close + 1 >= path.length() || path.charAt(close + 1) != ']') throw invalid(path);
                    result.add(new MatchPart(parseCompound(path.substring(i + 1, close + 1), path)));
                    i = close + 2;
                } else {
                    int close = path.indexOf(']', i + 1);
                    if (close < 0) throw invalid(path);
                    try { result.add(Integer.parseInt(path.substring(i + 1, close))); }
                    catch (NumberFormatException e) { throw invalid(path); }
                    i = close + 1;
                }
                if (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[' && path.charAt(i) != '{') throw invalid(path);
            } else if (c == '{') {
                if (key.length() != 0) addKey(result, key, path);
                int close = compoundEnd(path, i);
                result.add(new MatchPart(parseCompound(path.substring(i, close + 1), path)));
                i = close + 1;
                afterDot = false;
                if (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[' && path.charAt(i) != '{') throw invalid(path);
            } else if (c == '"' || c == '\'') {
                if (key.length() != 0) throw invalid(path);
                int[] end = new int[1];
                result.add(parseQuotedKey(path, i, end));
                i = end[0];
                afterDot = false;
                if (i < path.length() && path.charAt(i) != '.' && path.charAt(i) != '[' && path.charAt(i) != '{') throw invalid(path);
            } else if (c == ']') throw invalid(path);
            else { key.append(c); afterDot = false; i++; }
        }
        if (key.length() != 0) addKey(result, key, path);
        if (result.isEmpty() || afterDot) throw invalid(path);
        return result;
    }

    private static String parseQuotedKey(String path, int start, int[] end) {
        char quote = path.charAt(start);
        StringBuilder key = new StringBuilder();
        for (int i = start + 1; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\') {
                if (++i >= path.length()) throw invalid(path);
                key.append(path.charAt(i));
            } else if (c == quote) { end[0] = i + 1; return key.toString(); }
            else key.append(c);
        }
        throw invalid(path);
    }

    private static int compoundEnd(String path, int start) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = start; i < path.length(); i++) {
            char c = path.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        throw invalid(path);
    }

    private static NBTTagCompound parseCompound(String text, String path) {
        try { return net.minecraft.nbt.JsonToNBT.getTagFromJson(text); }
        catch (Exception e) { throw invalid(path); }
    }

    private static NBTBase firstMatch(NBTTagList list, NBTTagCompound selector) { return firstMatch(list, (NBTBase) selector); }

    private static NBTBase firstMatch(NBTTagList list, NBTBase selector) {
        for (int i = 0; i < list.tagCount(); i++) if (matches(selector, list.get(i))) return list.get(i);
        return null;
    }

    private static boolean matches(NBTBase selector, NBTBase candidate) {
        if (selector instanceof NBTTagCompound) {
            if (!(candidate instanceof NBTTagCompound)) return false;
            NBTTagCompound wanted = (NBTTagCompound) selector;
            NBTTagCompound actual = (NBTTagCompound) candidate;
            for (String key : wanted.getKeySet()) {
                if (!actual.hasKey(key) || !matches(wanted.getTag(key), actual.getTag(key))) return false;
            }
            return true;
        }
        if (selector instanceof NBTTagList) {
            if (!(candidate instanceof NBTTagList)) return false;
            NBTTagList wanted = (NBTTagList) selector;
            NBTTagList actual = (NBTTagList) candidate;
            for (int i = 0; i < wanted.tagCount(); i++) if (firstMatch(actual, wanted.get(i)) == null) return false;
            return true;
        }
        return selector.equals(candidate);
    }

    private static final class MatchPart {
        final NBTTagCompound compound;
        MatchPart(NBTTagCompound compound) { this.compound = compound; }
    }

    private static void addKey(List<Object> parts, StringBuilder key, String path) {
        if (key.length() == 0) throw invalid(path);
        parts.add(key.toString());
        key.setLength(0);
    }

    private static NBTTagCompound requireCompound(NBTBase value, String path) {
        if (value instanceof NBTTagCompound) return (NBTTagCompound) value;
        throw invalid(path);
    }

    private static NBTTagList requireList(NBTBase value, String path) {
        if (value instanceof NBTTagList) return (NBTTagList) value;
        throw invalid(path);
    }

    private static IllegalArgumentException invalid(String path) { return new IllegalArgumentException("Invalid NBT path: " + path); }
}
