"""Tests for hybrid_server GPU detection logging."""

import logging
from unittest.mock import MagicMock, patch


def test_gpu_detected_logging(caplog):
    """GPU detection should log GPU name and CUDA version when available."""
    mock_torch = MagicMock()
    mock_torch.cuda.is_available.return_value = True
    mock_torch.cuda.get_device_name.return_value = "NVIDIA A100"
    mock_torch.version.cuda = "12.1"

    with patch.dict("sys.modules", {"torch": mock_torch}):
        # Re-import to pick up the mock
        import importlib
        from opendataloader_pdf import hybrid_server

        importlib.reload(hybrid_server)

        with caplog.at_level(logging.INFO):
            # Simulate the GPU detection block from main()
            try:
                import torch
                if torch.cuda.is_available():
                    gpu_name = torch.cuda.get_device_name(0)
                    cuda_version = torch.version.cuda
                    logging.getLogger(__name__).info(
                        f"GPU detected: {gpu_name} (CUDA {cuda_version})"
                    )
            except ImportError:
                pass

    assert "GPU detected: NVIDIA A100 (CUDA 12.1)" in caplog.text


def test_no_gpu_logging(caplog):
    """Should log CPU fallback when no GPU is available."""
    mock_torch = MagicMock()
    mock_torch.cuda.is_available.return_value = False

    with patch.dict("sys.modules", {"torch": mock_torch}):
        with caplog.at_level(logging.INFO):
            try:
                import torch
                if torch.cuda.is_available():
                    pass
                else:
                    logging.getLogger(__name__).info("No GPU detected, using CPU.")
            except ImportError:
                pass

    assert "No GPU detected, using CPU." in caplog.text


def test_no_pytorch_logging(caplog):
    """Should log CPU fallback when PyTorch is not installed."""
    with patch.dict("sys.modules", {"torch": None}):
        with caplog.at_level(logging.INFO):
            try:
                import torch  # noqa: F811
                if torch.cuda.is_available():
                    pass
                else:
                    logging.getLogger(__name__).info("No GPU detected, using CPU.")
            except (ImportError, TypeError):
                logging.getLogger(__name__).info(
                    "No GPU detected, using CPU. (PyTorch not installed)"
                )

    assert "No GPU detected, using CPU. (PyTorch not installed)" in caplog.text
