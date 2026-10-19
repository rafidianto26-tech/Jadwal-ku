import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

handler_code = """
        // Global Error Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            android.util.Log.e("GlobalErrorHandler", "Uncaught Exception in thread ${thread.name}: ${exception.message}", exception)
        }
"""

content = re.sub(r'(override fun onCreate\(savedInstanceState: Bundle\?\) \{[^}]*super\.onCreate\(savedInstanceState\))', r'\1\n' + handler_code, content)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
