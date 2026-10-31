# Add project specific ProGuard rules here.

# ML Kit discovers its components reflectively via MlKitInitProvider; without the no-arg
# constructors, language ID silently never registers in minified builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
