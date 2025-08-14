# python/packages/opendataloader_pdf/src/opendataloader_pdf/__init__.py

from .api import process_pdf

__all__ = ["process_pdf"]

# You can add package-level initialization here if needed
# For example, to ensure JVM is initialized on first import
# from .jvm import init_jvm
# init_jvm() # Consider lazy initialization for better performance