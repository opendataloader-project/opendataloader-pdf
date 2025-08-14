# build-scripts/fetch_shaded_jar.py

import os
import shutil
import sys

def fetch_shaded_jar(java_runtime_target_dir, python_jars_dir):
    # Assuming the Java runtime module's shaded JAR is named 'runtime.jar'
    # and is located in its target directory after a Maven build.
    
    # Adjust this path if your Maven build outputs the JAR elsewhere or with a different name
    # For example: java/runtime/target/runtime-0.1.0-SNAPSHOT.jar
    # We'll use a glob to find it more robustly.
    
    # Find the latest shaded JAR in the java/runtime/target directory
    java_runtime_target_path = os.path.join(java_runtime_target_dir, 'target')
    
    shaded_jar_name = None
    for f in os.listdir(java_runtime_target_path):
        if f.startswith('opendataloader-pdf-runtime') and f.endswith('.jar') and 'original' not in f:
            shaded_jar_name = f
            break

    if not shaded_jar_name:
        print(f"Error: Shaded JAR not found in {java_runtime_target_path}")
        sys.exit(1)

    source_jar_path = os.path.join(java_runtime_target_path, shaded_jar_name)
    destination_jar_path = os.path.join(python_jars_dir, 'runtime.jar') # Standardize name for Python

    shutil.copy(source_jar_path, destination_jar_path)
    print(f"Copied {source_jar_path} to {destination_jar_path}")

if __name__ == "__main__":
    # Paths are relative to the monorepo root
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

    java_runtime_path = os.path.join(root_dir, 'java', 'runtime')
    python_jars_path = os.path.join(root_dir, 'python', 'packages', 'opendataloader_pdf', 'src', 'opendataloader_pdf', '_jars')

    if not os.path.exists(java_runtime_path):
        print(f"Error: Java runtime module not found at {java_runtime_path}")
        sys.exit(1)
    if not os.path.exists(python_jars_path):
        print(f"Error: Python _jars directory not found at {python_jars_path}")
        sys.exit(1)

    fetch_shaded_jar(java_runtime_path, python_jars_path)
