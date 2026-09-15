# The login slice has no app-specific shrinking rules yet.

# VLC native JNI resolves these classes and members by their original names.
# libvlc-all does not ship consumer keep rules.
-keep class org.videolan.libvlc.** { *; }
