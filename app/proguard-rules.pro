# R8 / ProGuard rules — app is tiny and reflection-free, so defaults suffice.
# Keep line numbers for readable crash traces, hide original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose + Kotlin metadata are handled by the AGP-bundled consumer rules
# shipped inside each AndroidX artifact, so nothing app-specific is needed.
