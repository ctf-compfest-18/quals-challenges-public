-injars  dist/burhanquest.jar
-outjars dist/burhanquest-obf.jar
-libraryjars "<java.home>/jmods/java.base.jmod"(!**.jar;!module-info.class)

-dontoptimize
-dontusemixedcaseclassnames
-repackageclasses ''
-allowaccessmodification
-dontwarn **

-keep public class Main { public static void main(java.lang.String[]); }

-keepnames class entities.Wanderer
-keepnames class entities.roles.**

-keepclassmembers class exception.** { *** getUsername(); }
-keepnames class exception.**

-keepclassmembers class entities.Wanderer { public *** get*(); }
-keepclassmembers class quests.**        { public *** get*(); }

-keepclassmembers enum * {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-obfuscate-strings class ** { *; }

-obfuscate-constants        class seal.** { *; }
-obfuscate-arithmetic,medium  class seal.** { *; }

-obfuscate-control-flow class seal.** { *; }
-obfuscate-control-flow class Main    { *; }