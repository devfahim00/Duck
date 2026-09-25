"""
STAGE 1 diagnostic bridge for the curl_cffi/Chaquopy migration.

Not the real engine yet. This module's only job is to prove - from actual
Kotlin code, exercised through Chaquopy's Python() call path, not just a
Gradle-time pip resolve - that:

  1. yt-dlp is importable inside the bundled CPython 3.13 runtime,
  2. curl_cffi is importable and yt-dlp's own impersonation machinery can
     see it as a usable target (this is the actual thing that was missing
     under youtubedl-android's bundled Python 3.8),
  3. the pornhub extractor module itself loads cleanly under this runtime.

None of this proves Pornhub/Instagram/TikTok actually download on a real
device - only a physical-device run of the real engine (later stage) can
show that. This just proves the runtime is real and not silently broken.

Once ChaquopyDiagnostics.runDiagnostics() comes back green from a real
device, the next stage replaces YtDlpEngine's YoutubeDLRequest-based calls
with real calls into a fuller version of this module (fetch_formats /
start_download using yt_dlp.YoutubeDL directly, with impersonate targets
set), and DownloadManager's direct YoutubeDL.getInstance() calls get
rerouted through YtDlpEngine so this bridge stays the only place that
knows about Chaquopy.
"""

import json


def engine_diagnostics():
    """Returns a JSON string describing what actually loaded. Never raises -
    every failure is captured so a single missing piece doesn't hide the
    result of the others."""
    info = {}

    try:
        import curl_cffi
        info["curl_cffi_version"] = getattr(curl_cffi, "__version__", "unknown")
        info["curl_cffi_import_ok"] = True
    except Exception as e:
        info["curl_cffi_import_ok"] = False
        info["curl_cffi_error"] = repr(e)

    try:
        from curl_cffi.requests import Session
        # A real impersonate target is the entire point of this migration -
        # if this list is empty, curl_cffi loaded but has nothing yt-dlp can
        # actually impersonate with.
        Session(impersonate="chrome")
        info["curl_cffi_chrome_impersonate_ok"] = True
    except Exception as e:
        info["curl_cffi_chrome_impersonate_ok"] = False
        info["curl_cffi_impersonate_error"] = repr(e)

    try:
        import yt_dlp
        info["yt_dlp_version"] = yt_dlp.version.__version__
        info["yt_dlp_import_ok"] = True
    except Exception as e:
        info["yt_dlp_import_ok"] = False
        info["yt_dlp_error"] = repr(e)

    try:
        # yt-dlp only reports a target as usable once it can see curl_cffi
        # AND a matching impersonate profile at import time.
        from yt_dlp.networking.impersonate import ImpersonateTarget
        from yt_dlp.networking._curlcffi import CurlCFFIRH  # noqa: F401
        info["yt_dlp_curl_cffi_handler_ok"] = True
    except Exception as e:
        info["yt_dlp_curl_cffi_handler_ok"] = False
        info["yt_dlp_curl_cffi_handler_error"] = repr(e)

    try:
        from yt_dlp.extractor.pornhub import PornHubIE  # noqa: F401
        info["pornhub_extractor_ok"] = True
    except Exception as e:
        info["pornhub_extractor_ok"] = False
        info["pornhub_extractor_error"] = repr(e)

    return json.dumps(info)
