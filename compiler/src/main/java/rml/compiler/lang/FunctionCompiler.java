package rml.compiler.lang;

import rml.compiler.CompileException;
import rml.compiler.Ids;
import rml.compiler.SourceResource;
import rml.compiler.source.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Expands source directives and lowers explicit compiler commands to the RML backend. */
public final class FunctionCompiler {
    private FunctionCompiler() { }

    public static Map<String, List<String>> compile(Project project, Map<String, List<String>> functionTags) {
        return new Compiler(project, functionTags).compile();
    }

    private static final class Compiler {
        private static final Pattern TOKEN = Pattern.compile("\\S+");
        private static final Pattern TIME = Pattern.compile("[0-9]+(?:\\.[0-9]+)?[tsd]?");
        private final Project project;
        private final Map<String, SourceResource> sources = new TreeMap<>();
        private final Map<String, List<String>> tags = new TreeMap<>();
        private final Map<String, String> wrappers = new TreeMap<>();
        private final Map<String, List<String>> compiled = new TreeMap<>();
        private final Set<String> including = new LinkedHashSet<>();
        private final Set<String> external = new LinkedHashSet<>();

        private Compiler(Project project, Map<String, List<String>> functionTags) {
            this.project = project;
            for (SourceResource source : project.resources("function")) sources.put(source.getId(), source);
            for (String id : project.getExternalFunctions()) external.add(Ids.parse(id));
            for (Map.Entry<String, List<String>> tag : functionTags.entrySet()) {
                String id = identifier(tag.getKey(), "pack.mcmeta", 0);
                if (tags.put(id, tag.getValue()) != null) {
                    throw error("invalid_function_tag", "pack.mcmeta", 0, "Duplicate normalized function tag: " + id);
                }
                String wrapper = project.getId() + ":__rml/tags/" + Ids.namespace(id) + "/" + Ids.path(id);
                if (sources.containsKey(wrapper) || external.contains(wrapper)) {
                    throw error("wrapper_collision", tagSource(id), 0, "Generated tag wrapper collides with function: " + wrapper);
                }
                wrappers.put(id, wrapper);
            }
        }

        private Map<String, List<String>> compile() {
            for (String id : sources.keySet()) compileSource(id);
            for (Map.Entry<String, List<String>> tag : tags.entrySet()) {
                List<String> commands = new ArrayList<>();
                for (String member : tag.getValue()) {
                    commands.add("function " + function(member, tagSource(tag.getKey()), 0));
                }
                compiled.put(wrappers.get(tag.getKey()), Collections.unmodifiableList(commands));
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(compiled));
        }

        private List<String> compileSource(String id) {
            List<String> cached = compiled.get(id);
            if (cached != null) return cached;
            including.add(id);
            SourceResource source = sources.get(id);
            List<String> commands = new ArrayList<>();
            String[] physical = source.getText().split("\\r\\n|\\r|\\n", -1);
            StringBuilder logical = new StringBuilder();
            int start = 0;
            boolean continuation = false;
            for (int index = 0; index < physical.length; index++) {
                // split's final empty element is an EOF marker, not another physical line.
                if (index == physical.length - 1 && physical[index].isEmpty()) break;
                String line = physical[index].trim();
                if (!continuation) {
                    if (line.isEmpty() || isComment(line)) continue;
                    start = index + 1;
                }
                boolean more = line.endsWith("\\");
                if (more) line = line.substring(0, line.length() - 1).trim();
                if (logical.length() > 0) logical.append(' ');
                logical.append(line);
                continuation = more;
                if (more) continue;
                String command = logical.toString().trim();
                logical.setLength(0);
                if (command.startsWith("$")) command = substitute(command.substring(1), source, start).trim();
                List<Token> tokens = tokens(command);
                if ("#include".equals(tokens.get(0).value)) {
                    require(tokens.size() == 2, source.getPath(), start, "invalid_include", "Expected #include <function>");
                    String target = identifier(tokens.get(1).value, source.getPath(), start);
                    if (!sources.containsKey(target)) {
                        throw error("missing_include", source.getPath(), start, "Included function must be local source: " + target);
                    }
                    if (including.contains(target)) {
                        throw error("include_cycle", source.getPath(), start, "Include cycle: " + including + " -> " + target);
                    }
                    commands.addAll(compileSource(target));
                } else {
                    commands.add(lower(command, source.getPath(), start));
                }
            }
            if (continuation) throw error("dangling_continuation", source.getPath(), start, "Continuation has no following line");
            including.remove(id);
            List<String> result = Collections.unmodifiableList(commands);
            compiled.put(id, result);
            return result;
        }

        private String substitute(String command, SourceResource source, int line) {
            StringBuilder result = new StringBuilder();
            int offset = 0;
            boolean runtime = false;
            int opening;
            while ((opening = command.indexOf("$(", offset)) >= 0) {
                result.append(command, offset, opening);
                int closing = command.indexOf(')', opening + 2);
                if (closing < 0) throw error("invalid_macro", source.getPath(), line, "Unclosed parameter substitution");
                String name = command.substring(opening + 2, closing);
                if (name.isEmpty()) throw error("invalid_macro", source.getPath(), line, "Parameter name must not be empty");
                String value = project.getConstants().get(name);
                if (value == null) {
                    runtime = true;
                    result.append(command, opening, closing + 1);
                    offset = closing + 1;
                    continue;
                }
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\u0085') >= 0
                        || value.indexOf('\u2028') >= 0 || value.indexOf('\u2029') >= 0) {
                    throw error("invalid_constant", source.getPath(), line, "Constant must occupy one line: " + name);
                }
                result.append(value);
                offset = closing + 1;
            }
            String expanded = result.append(command, offset, command.length()).toString();
            return runtime ? "$" + expanded : expanded;
        }

        private String lower(String command, String source, int line) {
            if (command.startsWith("$") && command.indexOf("$(") >= 0) {
                return "$" + lower(command.substring(1), source, line);
            }
            if (command.startsWith("/")) throw error("leading_slash", source, line, "Function commands must omit the leading slash");
            List<Token> words = tokens(command);
            String verb = words.get(0).value;
            if ("function".equals(verb)) {
                require(words.size() == 2 || words.size() == 4 && ("if".equals(words.get(2).value) || "unless".equals(words.get(2).value))
                                || words.size() == 6 && "with".equals(words.get(2).value) && "storage".equals(words.get(3).value),
                        source, line, "unsupported_command", "Expected function <id|#tag> [if|unless <selector>] or [with storage <id> <path>]");
                String target = function(words.get(1).value, source, line);
                if (words.size() == 6) identifier(words.get(4).value, source, line);
                return "function " + target + (words.size() > 2 ? " " + command.substring(words.get(2).start) : "");
            }
            if ("execute".equals(verb) && words.size() > 1) {
                String first = words.get(1).value;
                if (("if".equals(first) || "unless".equals(first)) && words.size() > 2 && "predicate".equals(words.get(2).value)) {
                    require(words.size() >= 6 && "run".equals(words.get(4).value), source, line, "unsupported_command",
                            "Expected execute if|unless predicate <id> run <command>");
                    String predicate = localResource("predicate", words.get(3).value, source, line);
                    return "rml execute " + first + " predicate " + predicate + " run "
                            + lower(command.substring(words.get(5).start), source, line);
                }
                if ("if".equals(first) || "unless".equals(first) || "as".equals(first) || "at".equals(first)
                        || "in".equals(first) || "positioned".equals(first) || "store".equals(first) || "align".equals(first)
                        || "anchored".equals(first) || "facing".equals(first) || "rotated".equals(first) || "on".equals(first)) {
                    int run = -1;
                    for (int i = 2; i < words.size(); i++) if ("run".equals(words.get(i).value)) { run = i; break; }
                    require(run >= 0 && run + 1 < words.size(), source, line, "unsupported_command", "Expected execute clauses run <command>");
                    return "rml " + command.substring(0, words.get(run + 1).start) + lower(command.substring(words.get(run + 1).start), source, line);
                }
                // Native 1.12 execute nests a command after its position (or its detect clause).
                int nested = words.size() > 5 && "detect".equals(words.get(5).value) ? 11 : 5;
                if (words.size() > nested) {
                    return command.substring(0, words.get(nested).start) + lower(command.substring(words.get(nested).start), source, line);
                }
            }
            if ("schedule".equals(verb)) {
                require(words.size() >= 3, source, line, "invalid_schedule", "Expected schedule function <id|#tag> <time> [append|replace] or schedule clear <id|#tag>");
                if ("clear".equals(words.get(1).value)) {
                    require(words.size() == 3, source, line, "invalid_schedule", "Expected schedule clear <id|#tag>");
                    return "rml schedule clear " + function(words.get(2).value, source, line);
                }
                require("function".equals(words.get(1).value) && (words.size() == 4 || words.size() == 5),
                        source, line, "invalid_schedule", "Expected schedule function <id|#tag> <time> [append|replace]");
                String time = words.get(3).value;
                require(TIME.matcher(time).matches(), source, line, "invalid_schedule", "Invalid schedule time: " + time);
                if (words.size() == 5) require("append".equals(words.get(4).value) || "replace".equals(words.get(4).value),
                        source, line, "invalid_schedule", "Schedule mode must be append or replace");
                return "rml schedule function " + function(words.get(2).value, source, line) + " " + time
                        + (words.size() == 5 ? " " + words.get(4).value : "");
            }
            if ("item".equals(verb) && words.size() > 1 && "modify".equals(words.get(1).value)) {
                if (words.size() == 6 && "entity".equals(words.get(2).value)) {
                    String modifier = localResource("item_modifier", words.get(5).value, source, line);
                    return "rml item modify entity " + words.get(3).value + " " + words.get(4).value + " " + modifier;
                }
                if (words.size() == 8 && "block".equals(words.get(2).value)) {
                    String modifier = localResource("item_modifier", words.get(7).value, source, line);
                    return "rml item modify block " + words.get(3).value + " " + words.get(4).value + " " + words.get(5).value
                            + " " + words.get(6).value + " " + modifier;
                }
                throw error("unsupported_command", source, line, "Expected item modify entity <selector> <slot> <modifier> or block <x> <y> <z> <slot> <modifier>");
            }
            return command;
        }

        private String function(String reference, String source, int line) {
            if (reference.startsWith("#")) {
                String tag = identifier(reference.substring(1), source, line);
                String wrapper = wrappers.get(tag);
                if (wrapper == null) throw error("missing_function_tag", source, line, "Unknown function tag: " + tag);
                return wrapper;
            }
            String id = identifier(reference, source, line);
            if (!sources.containsKey(id) && !external.contains(id)) {
                throw error("missing_function", source, line, "Unknown function (declare external_functions for external calls): " + id);
            }
            return id;
        }

        private String localResource(String type, String reference, String source, int line) {
            String id = identifier(reference, source, line);
            if (project.getResource(type, id) == null) throw error("missing_" + type, source, line, "Unknown local " + type + ": " + id);
            return id;
        }

        private String tagSource(String id) {
            SourceResource source = project.getResource("tags/function", id);
            return source == null ? "pack.mcmeta" : source.getPath();
        }

        private static boolean isComment(String line) {
            return line.startsWith("#") && !(line.equals("#include") || line.startsWith("#include ") || line.startsWith("#include\t"));
        }

        private static List<Token> tokens(String command) {
            List<Token> result = new ArrayList<>();
            Matcher matcher = TOKEN.matcher(command);
            while (matcher.find()) result.add(new Token(matcher.group(), matcher.start()));
            return result;
        }
    }

    private static final class Token {
        private final String value;
        private final int start;
        private Token(String value, int start) { this.value = value; this.start = start; }
    }

    private static String identifier(String value, String source, int line) {
        try {
            return Ids.parse(value);
        } catch (IllegalArgumentException e) {
            throw new CompileException("invalid_identifier", source, line, "Invalid identifier: " + value, e);
        }
    }

    private static void require(boolean condition, String source, int line, String code, String message) {
        if (!condition) throw error(code, source, line, message);
    }

    private static CompileException error(String code, String source, int line, String message) {
        return new CompileException(code, source, line, message);
    }
}
