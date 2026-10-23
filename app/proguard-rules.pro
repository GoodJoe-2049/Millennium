-keep,allowoptimization class com.millennium.app.ModuleMain {
    public <init>();
    public <methods>;
}

# Dhizuku's unused no-argument init() references this hidden platform class.
# Millennium always passes Steam's application context to init(Context).
-dontwarn android.app.ActivityThread

# Rhino 1.9.1 resolves these types by name in ScriptRuntime.
-keepnames class org.mozilla.javascript.Context
-keepnames class org.mozilla.javascript.ContextFactory
-keepnames interface org.mozilla.javascript.Function
-keepnames class org.mozilla.javascript.ScriptableObject

# Context constructs the interpreter reflectively; RhinoException matches this stack frame.
-keep class org.mozilla.javascript.Interpreter {
    public <init>();
    private static java.lang.Object interpretLoop(...);
}

# This safe-scope built-in still uses the legacy reflective initializer.
-keep,allowoptimization class org.mozilla.javascript.NativeContinuation {
    public static void init(org.mozilla.javascript.Context, org.mozilla.javascript.Scriptable, boolean);
}

# Keep the service descriptor's interface/provider names and the provider constructor.
-keepnames interface org.mozilla.javascript.RegExpLoader
-keep,allowoptimization class org.mozilla.javascript.regexp.RegExpLoaderImpl {
    public <init>();
}
