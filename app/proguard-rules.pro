-dontobfuscate
-keepattributes SourceFile,LineNumberTable

##########################################################################
# Rules for keeping JSON Serialization code
# Ref: https://github.com/Kotlin/kotlinx.serialization#android
##########################################################################

-keepattributes *Annotation*, InnerClasses

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.tcs.raat.**$$serializer { *; }
-keepclassmembers class com.tcs.raat.** {
    *** Companion;
}
-keepclasseswithmembers class com.tcs.raat.** {
    kotlinx.serialization.KSerializer serializer(...);
}