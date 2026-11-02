# R8 rules for the release build.
#
# No extra keep rule is needed today:
# - activities, widget providers (AppWidgetProvider), ActionReceiver and the Quick Settings
#   TileServices are declared in AndroidManifest.xml, so AAPT2 generates keep rules for them;
# - widget layouts only call framework methods through RemoteViews (setColorFilter,
#   setBackgroundResource...), which R8 never touches;
# - org.json is part of the Android platform; no reflection or serialization on app classes.
#
# Add rules here only if a release build breaks (check build/outputs/mapping/release/).

# Keep line numbers for readable release stack traces, hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
