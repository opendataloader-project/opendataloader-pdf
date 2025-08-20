# python/packages/opendataloader_pdf/metadata_hook.py

from hatchling.metadata.plugin.interface import MetadataHookInterface

class CustomMetadataHook(MetadataHookInterface):
    def initialize(self, version: str, build_data: dict) -> None:
        # This hook runs before metadata generation.
        # You can modify project metadata here if needed.
        pass

    def update(self, metadata):
        # Currently, this hook does not modify metadata.
        # Returning metadata as-is for clarity.
        return metadata
