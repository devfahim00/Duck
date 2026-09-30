# Duck keeps default rules; release build is not minified.

# Called from Python by name (Chaquopy reflection) - never rename/strip.
-keep class com.devfahim00.duck.ytdlp.ProgressSink { public *; }
