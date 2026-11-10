# Go mobile bindings (Xray core) are called through JNI
-keep class libv2ray.** { *; }
-keep class go.** { *; }
-dontwarn libv2ray.**
-dontwarn go.**
