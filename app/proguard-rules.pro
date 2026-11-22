# ProGuard / R8 rules for TinyHands

# Flatten classes into default root package
-repackageclasses ''
-allowaccessmodification

# Strip debug info, line numbers, local variable tables, and source file attributes
-renamesourcefileattribute ''
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!MethodParameters

# Strip all android.util.Log invocations and strings
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
