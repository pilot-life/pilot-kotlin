# Keep kotlinx-serialization annotations and generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep SDK model classes used by the UI components
-keep class life.pilot.partner.sdk.model.** { *; }
-keep class life.pilot.partner.sdk.webhooks.** { *; }
