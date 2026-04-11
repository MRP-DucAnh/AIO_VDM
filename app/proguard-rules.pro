-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-optimizationpasses 5
-allowaccessmodification

-keep class net.jpountz.lz4.LZ4Factory { *; }
-keep class net.jpountz.lz4.LZ4*Safe* { *; }
-keep class net.jpountz.lz4.LZ4*JNI* { *; }
-keep class net.jpountz.xxhash.XXHashFactory { *; }
-keep class net.jpountz.xxhash.XXHash*Safe* { *; }
-keep class net.jpountz.xxhash.XXHash*JNI* { *; }

-dontwarn net.jpountz.lz4.**
-dontwarn net.jpountz.xxhash.**
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

-keep class io.objectbox.BoxStore { *; }
-keep class io.objectbox.Box { *; }
-keep class io.objectbox.Query { *; }
-keep class io.objectbox.Cursor { *; }
-keep class io.objectbox.Transaction { *; }

-keep @io.objectbox.annotation.Entity class * { *; }
-keep interface io.objectbox.annotation.Entity { *; }
-keep class **.MyObjectBox { *; }
-keep class **.__EntityDescriptor { *; }
-keep class **._ { *; }
-keep class **Cursor { *; }

-keep class app.core.engines.settings.AIOSettings { *; }
-keepclassmembers class app.core.engines.settings.AIOSettings {
    <fields>;
}

-keep class * implements io.objectbox.converter.PropertyConverter {
    <init>(...);
}

-dontwarn io.objectbox.**

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class de.ruedigermoeller.serialization.FSTConfiguration { *; }
-keep class de.ruedigermoeller.serialization.FSTObjectInput { *; }
-keep class de.ruedigermoeller.serialization.FSTObjectOutput { *; }
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-dontwarn de.ruedigermoeller.**

-keep @com.dslplatform.json.CompiledJson class * { *; }
-keep class * implements com.dslplatform.json.Configuration
-keepclassmembers class * {
    @com.dslplatform.json.JsonAttribute <fields>;
    @com.dslplatform.json.JsonAttribute <methods>;
}

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.fasterxml.jackson.databind.ObjectMapper { *; }
-keep @com.fasterxml.jackson.annotation.* class * { *; }
-dontwarn com.fasterxml.jackson.**

-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn io.ktor.**

-keep public class * extends com.github.bumptech.glide.module.AppGlideModule
-keep public class * extends com.github.bumptech.glide.module.LibraryGlideModule
-keep class com.github.bumptech.glide.GeneratedAppGlideModuleImpl

-keep class com.airbnb.lottie.LottieAnimationView { *; }
-keep class com.airbnb.lottie.model.** { *; }

-keep class org.mozilla.javascript.Context { *; }
-keep class org.mozilla.javascript.Scriptable { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.thoughtworks.paranamer.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.json.spi.JsonProvider
-dontwarn sun.reflect.ReflectionFactory

-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    <init>();
}
-keep class org.apache.commons.compress.archivers.zip.ExtraFieldUtils { *; }
-dontwarn org.apache.commons.compress.**