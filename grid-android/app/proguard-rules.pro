# Godot native bridge
-keep class org.godotengine.godot.** { *; }
-keep class ai.grid.bridge.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
