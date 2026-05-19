# Consumer R8/ProGuard rules for life.pilot:pilot-partner-sdk.
# Bundled inside the JAR; Android Gradle Plugin reads them automatically
# when the SDK is on the classpath and minification runs.

# kotlinx-serialization needs the @Serializable classes + their generated
# $Companion / Companion / $$serializer helpers to survive R8.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class life.pilot.partner.sdk.model.** { *; }
-keep,includedescriptorclasses class life.pilot.partner.sdk.webhooks.** { *; }
-keepclassmembers class life.pilot.partner.sdk.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class life.pilot.partner.sdk.webhooks.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class life.pilot.partner.sdk.model.**$$serializer { *; }
-keepclasseswithmembers class life.pilot.partner.sdk.webhooks.**$$serializer { *; }

# Retrofit reflectively reads parameter / return-type annotations.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface life.pilot.partner.sdk.api.**

# kotlinx-coroutines keeps DebugProbesKt entry point for stack-trace
# recovery — required for SDK suspend functions to surface cleanly.
-dontwarn kotlinx.coroutines.debug.AgentPremain
