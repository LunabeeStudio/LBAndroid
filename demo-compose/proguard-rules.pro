# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in $ANDROID_HOME/tools/proguard/proguard-android-optimize.txt referenced by the
# release build type. The library consumer ProGuard rules of the dependencies are
# merged in automatically, so this file only needs project-specific keeps.
#
# For more details, see https://developer.android.com/studio/build/shrink-code

# Keep line numbers for readable release stack traces, hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
