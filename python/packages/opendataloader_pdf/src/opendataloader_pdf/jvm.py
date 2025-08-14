# python/packages/opendataloader_pdf/src/opendataloader_pdf/jvm.py

import jpype
import jpype.imports
from jpype.types import JString, JArray, JByte
import os
import platform
import logging

logger = logging.getLogger(__name__)

_jvm_started = False

def _find_bundled_jre():
    """Finds the bundled JRE path based on the current platform."""
    current_dir = os.path.dirname(os.path.abspath(__file__))
    jre_base_path = os.path.join(current_dir, "_jars", "jre")

    system = platform.system()
    if system == "Linux":
        # Assuming a structure like _jars/jre/linux-x64/jdk-XYZ
        jre_path = os.path.join(jre_base_path, "linux-x64") # Adjust based on actual bundled JRE name
    elif system == "Darwin": # macOS
        # Assuming a structure like _jars/jre/macos-x64/jdk-XYZ/Contents/Home
        jre_path = os.path.join(jre_base_path, "macos-x64", "Contents", "Home") # Adjust based on actual bundled JRE name
    elif system == "Windows":
        # Assuming a structure like _jars/jre/windows-x64/jdk-XYZ
        jre_path = os.path.join(jre_base_path, "windows-x64") # Adjust based on actual bundled JRE name
    else:
        raise OSError(f"Unsupported operating system: {system}")

    # Find the actual JDK/JRE directory within the platform-specific folder
    # This assumes there's only one JDK/JRE folder directly under the platform folder
    for entry in os.listdir(jre_path):
        full_path = os.path.join(jre_path, entry)
        if os.path.isdir(full_path) and ("jdk" in entry or "jre" in entry):
            return full_path
    
    raise FileNotFoundError(f"Bundled JRE not found in {jre_path}. Please ensure it's correctly packaged.")


def init_jvm():
    global _jvm_started
    if _jvm_started:
        logger.debug("JVM already started.")
        return

    try:
        # Find the bundled JRE
        jre_path = _find_bundled_jre()
        logger.info(f"Using JRE from: {jre_path}")

        # Find the shaded JAR
        current_dir = os.path.dirname(os.path.abspath(__file__))
        jar_path = os.path.join(current_dir, "_jars", "runtime.jar") # Name from runtime/pom.xml <finalName>
        
        if not os.path.exists(jar_path):
            raise FileNotFoundError(f"Java runtime JAR not found at {jar_path}. Please ensure it's correctly copied.")

        # Start the JVM
        jpype.startJVM(jpype.getDefaultJVMPath(jre_path=jre_path), '-ea', '-Djava.class.path=%s' % jar_path)
        _jvm_started = True
        logger.info("JVM started successfully.")

    except Exception as e:
        logger.error(f"Failed to start JVM: {e}")
        raise

def shutdown_jvm():
    global _jvm_started
    if _jvm_started:
        jpype.shutdownJVM()
        _jvm_started = False
        logger.info("JVM shut down.")


# Example usage (for testing or direct calls)
if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    try:
        init_jvm()
        # Example: Call a static method from your Java CLIMain if it exists
        # from com.hancom.opendataloader.pdf.cli import CLIMain
        # CLIMain.someStaticMethod("test")
        print("JVM initialized. You can now interact with Java classes.")
    except Exception as e:
        print(f"Error: {e}")
    finally:
        if _jvm_started:
            shutdown_jvm()
