# build-scripts/set_version.py

import os
import re
import sys

def set_version(version_file, pom_file, pyproject_toml_file):
    with open(version_file, 'r') as f:
        version = f.read().strip()

    # Update Maven POM
    with open(pom_file, 'r') as f:
        pom_content = f.read()
    pom_content = re.sub(r'<version>.*</version>', f'<version>{version}</version>', pom_content, count=1)
    with open(pom_file, 'w') as f:
        f.write(pom_content)
    print(f"Updated Maven POM version to {version}")

    # Update Python pyproject.toml
    with open(pyproject_toml_file, 'r') as f:
        pyproject_content = f.read()
    pyproject_content = re.sub(r'version = ".*"', f'version = "{version}"', pyproject_content, count=1)
    with open(pyproject_toml_file, 'w') as f:
        f.write(pyproject_content)
    print(f"Updated Python pyproject.toml version to {version}")

if __name__ == "__main__":
    # Paths are relative to the monorepo root
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    
    version_path = os.path.join(root_dir, 'VERSION')
    java_pom_path = os.path.join(root_dir, 'java', 'pom.xml')
    python_pyproject_path = os.path.join(root_dir, 'python', 'packages', 'opendataloader_pdf', 'pyproject.toml')

    if not os.path.exists(version_path):
        print(f"Error: VERSION file not found at {version_path}")
        sys.exit(1)
    if not os.path.exists(java_pom_path):
        print(f"Error: Java pom.xml not found at {java_pom_path}")
        sys.exit(1)
    if not os.path.exists(python_pyproject_path):
        print(f"Error: Python pyproject.toml not found at {python_pyproject_path}")
        sys.exit(1)

    set_version(version_path, java_pom_path, python_pyproject_path)
