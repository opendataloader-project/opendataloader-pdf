# build-scripts/set_version.py

import os
import re
import sys

def set_version(version_file, pom_file, setup_py_file):
    with open(version_file, 'r') as f:
        version = f.read().strip()

    # Update Maven POM
    with open(pom_file, 'r') as f:
        pom_content = f.read()
    pom_content = re.sub(r'<version>.*</version>', f'<version>{version}</version>', pom_content, count=1)
    with open(pom_file, 'w') as f:
        f.write(pom_content)
    print(f"Updated Maven POM version to {version}")

    # Update Python setup.py
    with open(setup_py_file, 'r') as f:
        setup_content = f.read()
    setup_content = re.sub(r'version=\".*\"', f'version=\"{version}\"', setup_content, count=1)
    with open(setup_py_file, 'w') as f:
        f.write(setup_content)
    print(f"Updated Python setup.py version to {version}")

if __name__ == "__main__":
    # Paths are relative to the monorepo root
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    
    version_path = os.path.join(root_dir, 'VERSION')
    java_pom_path = os.path.join(root_dir, 'java', 'pom.xml')
    python_setup_path = os.path.join(root_dir, 'python', 'opendataloader-pdf', 'setup.py')

    if not os.path.exists(version_path):
        print(f"Error: VERSION file not found at {version_path}")
        sys.exit(1)
    if not os.path.exists(java_pom_path):
        print(f"Error: Java pom.xml not found at {java_pom_path}")
        sys.exit(1)
    if not os.path.exists(python_setup_path):
        print(f"Error: Python setup.py not found at {python_setup_path}")
        sys.exit(1)

    set_version(version_path, java_pom_path, python_setup_path)
