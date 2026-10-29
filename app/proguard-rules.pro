# JSch resolves cipher, kex, mac, compression and auth classes from name strings in its config map via Class.forName, so shrinking would remove them. Only constructors are kept.
-keep class com.jcraft.jsch.** { <init>(...); }

# JGit fills message fields of its translation bundles by reflection on field names.
-keep class * extends org.eclipse.jgit.nls.TranslationBundle { *; }

# JSch and JGit reference optional libraries that are not shipped on Android; the code paths guarded by those classes are never taken.
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.bouncycastle.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.**
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn java.awt.**
