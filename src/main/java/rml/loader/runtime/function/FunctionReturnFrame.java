package rml.loader.runtime.function;

/** Per-invocation return state inherited by nested function frames. */
public final class FunctionReturnFrame {
    private final FunctionReturnFrame parent;
    private int result;
    private int commandCount;
    private boolean succeeded;
    private boolean returned;

    public FunctionReturnFrame() { this(null); }
    public FunctionReturnFrame(FunctionReturnFrame parent) { this.parent = parent; }

    public void countCommand() {
        commandCount++;
        if (parent != null) parent.countCommand();
    }

    public void complete(int value) { complete(value, true); }
    public void complete(int value, boolean succeeded) { this.result = value; this.succeeded = succeeded; this.returned = true; }
    public int getResult() { return result; }
    public int getCommandCount() { return commandCount; }
    public boolean hasReturned() { return returned; }
    public boolean succeeded() { return returned ? succeeded : commandCount > 0; }
}
