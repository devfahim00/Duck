"""
Duck's yt-dlp engine, running inside the Chaquopy CPython 3.13 runtime.

STAGE 2 of the curl_cffi/Chaquopy migration: this module is now the real
engine (Stage 1 was a throwaway diagnostic probe). Kotlin only talks to
yt-dlp through the functions below; nothing else in the app knows about
Chaquopy (see YtDlpEngine.kt).

Why this exists: under youtubedl-android's bundled Python 3.8 yt-dlp cannot
use curl_cffi, so `impersonate=` (TLS/JA3 browser fingerprinting - needed by
Pornhub, Instagram, TikTok and friends) had no target. Here yt-dlp runs on a
real CPython 3.13 with curl_cffi installed, so extractors that ask for
impersonation get a Chrome fingerprint automatically.

Public API (all return JSON strings; none raise for expected failures):

    configure(...)            once per process: ffmpeg / aria2c locations
    use_engine(path)          hot-swap yt-dlp for a downloaded zipapp
    engine_version()          version of the yt-dlp that is currently active
    fetch_formats(url, opts)  metadata + format list, no download
    start_download(...)       blocking download, progress via a Kotlin sink
    cancel(download_id)       cooperative cancel of a running download
    engine_diagnostics()      what actually loaded (curl_cffi, targets, ...)

`opts` is a JSON object built by Kotlin:
    headers          dict of extra HTTP headers (Accept-Language, Referer, UA)
    cookie_file      path to a Netscape cookies.txt, or ""
    cache_dir        yt-dlp cache directory, or ""
    impersonate_all  bool: impersonate Chrome for EVERY request. When False
                     (default) extractors that need impersonation still ask
                     for it themselves, which is what fixes most sites.
"""

import importlib
import json
import os
import re
import subprocess
import sys
import threading
import time

_state_lock = threading.Lock()
_cancelled = set()          # download ids the user asked to cancel
_active = 0                 # fetches/downloads currently inside yt-dlp
_engine_path = None         # zipapp currently first on sys.path, or None
_cfg = {
    "ffmpeg": "",           # .../libffmpeg.so
    "aria2c": "",           # .../libaria2c.so
}
_tls = threading.local()    # current download id, for the aria2c patch

_ANSI = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


class _Cancelled(Exception):
    """Raised inside progress hooks to abort a download cooperatively."""


class _Log:
    """Swallows yt-dlp's console output; keeps nothing, prints nothing."""

    def debug(self, msg):
        pass

    def info(self, msg):
        pass

    def warning(self, msg):
        pass

    def error(self, msg):
        pass


# --------------------------------------------------------------------- setup

def configure(ffmpeg_bin, ffmpeg_lib_dir, aria2c_bin, aria2c_lib_dir):
    """Tell the bridge where the native helpers live. Safe to call twice.

    ffmpeg / aria2c come from the youtubedl-android AARs (still bundled for
    exactly this reason): the binaries are executable libs in the app's
    native library dir, and their shared-library dependencies are unpacked
    into noBackupFilesDir. yt-dlp starts them as child processes, so the
    library path has to be in THIS process's environment.
    """
    _cfg["ffmpeg"] = ffmpeg_bin or ""
    _cfg["aria2c"] = aria2c_bin if aria2c_bin and os.path.exists(aria2c_bin) else ""
    paths = [p for p in (ffmpeg_lib_dir, aria2c_lib_dir) if p]
    if paths:
        existing = os.environ.get("LD_LIBRARY_PATH", "")
        merged = ":".join(paths + ([existing] if existing else []))
        os.environ["LD_LIBRARY_PATH"] = merged
    return json.dumps({"ok": True, "aria2c": bool(_cfg["aria2c"])})


def _purge_yt_dlp():
    for name in list(sys.modules):
        if name == "yt_dlp" or name.startswith("yt_dlp.") or name.startswith("yt_dlp_plugins"):
            del sys.modules[name]


def use_engine(path):
    """Activate the yt-dlp zipapp at `path` (or the pip-bundled one for "").

    A yt-dlp release is a zipapp: a `#!` line followed by a ZIP archive.
    Python's zipimport accepts such prefixed archives, so putting the file on
    sys.path is enough to import the newer yt_dlp from it.

    Refuses while a fetch/download is running: swapping modules under a live
    yt-dlp would mix two versions. Kotlin retries before the next request.
    """
    global _engine_path
    with _state_lock:
        wanted = path or None
        if wanted == _engine_path and "yt_dlp" in sys.modules:
            # Already active: nothing to swap, so this is fine mid-download.
            return json.dumps({"ok": True, "version": _safe_version()})
        if _active:
            return json.dumps({"ok": False, "busy": True})
        previous = _engine_path
        try:
            if previous and previous in sys.path:
                sys.path.remove(previous)
            _purge_yt_dlp()
            if wanted:
                sys.path.insert(0, wanted)
            importlib.invalidate_caches()
            import yt_dlp  # noqa: F401
            version = _version()
        except Exception as e:  # broken download: fall back to the bundled one
            if wanted and wanted in sys.path:
                sys.path.remove(wanted)
            _purge_yt_dlp()
            importlib.invalidate_caches()
            _engine_path = None
            try:
                import yt_dlp  # noqa: F401,F811
            except Exception:
                pass
            return json.dumps({"ok": False, "busy": False,
                               "error": _clean(repr(e)), "version": _safe_version()})
        _engine_path = wanted
        return json.dumps({"ok": True, "version": version})


def _version():
    import yt_dlp
    return yt_dlp.version.__version__


def _safe_version():
    try:
        return _version()
    except Exception:
        return None


def engine_version():
    return _safe_version() or ""


def _clean(message):
    message = _ANSI.sub("", str(message)).strip()
    if message.startswith("ERROR:"):
        message = message[len("ERROR:"):].strip()
    return message


def _err_text(e):
    return _clean(getattr(e, "msg", None) or str(e) or repr(e))


class _Active:
    def __enter__(self):
        global _active
        with _state_lock:
            _active += 1

    def __exit__(self, *exc):
        global _active
        with _state_lock:
            _active -= 1
        return False


# ------------------------------------------------------------------- params

def _base_params(opts):
    params = {
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "no_color": True,
        "logger": _Log(),
        "noplaylist": True,
        "socket_timeout": 20,
        "retries": 3,
        "extractor_retries": 2,
        # Same as before: some servers / device CA stores fail verification.
        "nocheckcertificate": True,
    }
    headers = opts.get("headers") or {}
    if headers:
        params["http_headers"] = dict(headers)
    if opts.get("cookie_file") and os.path.exists(opts["cookie_file"]):
        params["cookiefile"] = opts["cookie_file"]
    if opts.get("cache_dir"):
        params["cachedir"] = opts["cache_dir"]
    if _cfg["ffmpeg"]:
        params["ffmpeg_location"] = _cfg["ffmpeg"]
    return params


def _make_ydl(yt_dlp, params, opts):
    """Create YoutubeDL, applying `impersonate_all` only if a target exists."""
    if opts.get("impersonate_all"):
        try:
            from yt_dlp.networking.impersonate import ImpersonateTarget
            with_imp = dict(params)
            with_imp["impersonate"] = ImpersonateTarget.from_str("chrome")
            return yt_dlp.YoutubeDL(with_imp)
        except Exception:
            # curl_cffi missing / no chrome target: plain requests still work
            # and extractors can still request impersonation themselves.
            pass
    return yt_dlp.YoutubeDL(params)


# -------------------------------------------------------------------- fetch

def _slim_format(f):
    return {
        "format_id": f.get("format_id"),
        "height": f.get("height") or 0,
        "fps": f.get("fps") or 0,
        "vcodec": f.get("vcodec"),
        "acodec": f.get("acodec"),
        "filesize": f.get("filesize") or 0,
        "filesize_approx": f.get("filesize_approx") or 0,
        "ext": f.get("ext"),
    }


def fetch_formats(url, opts_json):
    """Extract metadata and formats without downloading anything."""
    try:
        opts = json.loads(opts_json or "{}")
        import yt_dlp
        params = _base_params(opts)
        params["skip_download"] = True
        with _Active():
            with _make_ydl(yt_dlp, params, opts) as ydl:
                info = ydl.extract_info(url, download=False)
                info = ydl.sanitize_info(info)
        if info is None:
            return json.dumps({"error": "No information could be extracted."})
        if info.get("_type") == "playlist" and info.get("entries"):
            entries = [e for e in info["entries"] if e]
            if not entries:
                return json.dumps({"error": "Unable to parse video information"})
            info = entries[0]
        return json.dumps({
            "title": info.get("title"),
            "fulltitle": info.get("fulltitle"),
            "webpage_url": info.get("webpage_url"),
            "thumbnail": info.get("thumbnail"),
            "duration": info.get("duration") or 0,
            "uploader": info.get("uploader") or info.get("channel"),
            "formats": [_slim_format(f) for f in (info.get("formats") or [])],
        })
    except Exception as e:
        return json.dumps({"error": _err_text(e)})


# ----------------------------------------------------------------- download

def _notify(sink, percent, speed, eta, merging):
    try:
        sink.onProgress(float(percent), float(speed), float(eta), bool(merging))
    except Exception:
        pass  # a broken UI callback must never kill the download


_SIZE_UNITS = {"": 1, "K": 1024, "M": 1024 ** 2, "G": 1024 ** 3, "T": 1024 ** 4}
_ARIA_PERCENT = re.compile(r"\((\d+(?:\.\d+)?)%\)")
_ARIA_SPEED = re.compile(r"DL:([\d.]+)([KMGT]?)i?B")
_ARIA_ETA = re.compile(r"ETA:(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?")
_ARIA_SIZES = re.compile(r"([\d.]+)([KMGT]?)i?B/([\d.]+)([KMGT]?)i?B")


def _patch_aria2c():
    """Make aria2c downloads report progress through yt-dlp's hooks.

    yt-dlp's Aria2cFD lets aria2c write its console readout straight to the
    process stdout (which on Android goes nowhere), so no progress hook ever
    fires and the UI would sit at 0% until the file is done. This replaces
    the process runner with one that reads the readout line
    `[#a1b2c3 10MiB/20MiB(50%) CN:8 DL:2.3MiB ETA:8s]` and forwards it, and
    that can be killed when the user cancels. Re-applied after every engine
    swap because use_engine() reloads the yt_dlp modules.
    """
    from yt_dlp.downloader import external
    cls = external.Aria2cFD
    if getattr(cls, "_duck_patched", False):
        return

    def _call_process(self, cmd, info_dict):
        download_id = getattr(_tls, "download_id", None)
        proc = subprocess.Popen(
            cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, errors="replace",  # text mode: '\r' also ends a line
        )
        stop = threading.Event()

        def watcher():
            while not stop.is_set() and proc.poll() is None:
                if download_id in _cancelled:
                    try:
                        proc.terminate()
                    except Exception:
                        pass
                    return
                time.sleep(0.4)

        threading.Thread(target=watcher, daemon=True).start()
        extra = []
        try:
            for raw in proc.stdout:
                line = raw.strip()
                if not line:
                    continue
                percent = _ARIA_PERCENT.search(line)
                if not line.startswith("[#") and not percent:
                    extra.append(line)
                    continue
                status = {"status": "downloading",
                          "filename": info_dict.get("_filename") or ""}
                sizes = _ARIA_SIZES.search(line)
                if sizes:
                    done = float(sizes.group(1)) * _SIZE_UNITS[sizes.group(2)]
                    total = float(sizes.group(3)) * _SIZE_UNITS[sizes.group(4)]
                    status["downloaded_bytes"] = int(done)
                    status["total_bytes"] = int(total)
                speed = _ARIA_SPEED.search(line)
                if speed:
                    status["speed"] = float(speed.group(1)) * _SIZE_UNITS[speed.group(2)]
                eta = _ARIA_ETA.search(line)
                if eta and any(eta.groups()):
                    h, m, s = (int(g) if g else 0 for g in eta.groups())
                    status["eta"] = h * 3600 + m * 60 + s
                if percent:
                    status["_duck_percent"] = float(percent.group(1))
                self._hook_progress(status, info_dict)
            returncode = proc.wait()
        finally:
            stop.set()
            if proc.poll() is None:
                proc.kill()
        if download_id in _cancelled:
            raise _Cancelled()
        return "", "\n".join(extra), returncode

    cls._call_process = _call_process
    cls._duck_patched = True


def start_download(download_id, url, format_spec, out_dir, audio_only, needs_merge,
                   threads, turbo, opts_json, sink):
    """Blocking download. Returns {"status": "completed"|"cancelled"|"error", ...}."""
    _tls.download_id = download_id
    try:
        opts = json.loads(opts_json or "{}")
        import yt_dlp
        params = _base_params(opts)
        result = {"filepath": None}

        def progress_hook(d):
            if download_id in _cancelled:
                raise _Cancelled()
            status = d.get("status")
            if status == "downloading":
                percent = d.get("_duck_percent")
                if percent is None:
                    total = d.get("total_bytes") or d.get("total_bytes_estimate")
                    done = d.get("downloaded_bytes") or 0
                    percent = (done * 100.0 / total) if total else -1
                speed = d.get("speed")
                eta = d.get("eta")
                _notify(sink, percent, speed if speed else -1,
                        eta if eta is not None else -1, False)
            elif status == "finished":
                _notify(sink, 100, -1, -1, False)

        def pp_hook(d):
            if download_id in _cancelled:
                raise _Cancelled()
            name = d.get("postprocessor") or ""
            if d.get("status") == "started" and name in (
                    "Merger", "FFmpegMerger", "FFmpegVideoRemuxer",
                    "FFmpegVideoConvertor", "FFmpegExtractAudio"):
                _notify(sink, 100, -1, -1, True)
            if name == "MoveFiles" and d.get("status") == "finished":
                path = (d.get("info_dict") or {}).get("filepath")
                if path:
                    result["filepath"] = path

        params.update({
            "format": format_spec,
            "outtmpl": os.path.join(out_dir, "%(title)s [%(id)s].%(ext)s"),
            "updatetime": False,        # --no-mtime
            "progress_hooks": [progress_hook],
            "postprocessor_hooks": [pp_hook],
            "noprogress": True,
        })
        if needs_merge and not audio_only:
            params["merge_output_format"] = "mkv"

        threads = max(1, int(threads))
        if turbo and _cfg["aria2c"] and not opts.get("impersonate_all"):
            # yt-dlp skips external downloaders for impersonated requests, so
            # turbo and "impersonate everything" are mutually exclusive.
            _patch_aria2c()
            params["external_downloader"] = _cfg["aria2c"]
            params["external_downloader_args"] = {
                # yt-dlp turns the readout OFF when noprogress is set (it is);
                # our _call_process needs it to report progress, and args from
                # here are appended after yt-dlp's own, so ours win.
                "aria2c": ["-x", str(threads), "-s", str(threads), "-k", "1M",
                           "--show-console-readout=true"],
            }
        else:
            params["concurrent_fragment_downloads"] = threads

        path = None
        with _Active():
            with _make_ydl(yt_dlp, params, opts) as ydl:
                info = ydl.extract_info(url, download=True)
                if download_id in _cancelled:
                    raise _Cancelled()
                path = result["filepath"]
                if not path and info:
                    downloads = info.get("requested_downloads") or []
                    if downloads:
                        path = downloads[0].get("filepath")
                    path = path or info.get("filepath") or info.get("_filename")
        return json.dumps({"status": "completed", "filepath": path})
    except _Cancelled:
        return json.dumps({"status": "cancelled"})
    except Exception as e:
        if download_id in _cancelled:  # yt-dlp may wrap the hook's exception
            return json.dumps({"status": "cancelled"})
        return json.dumps({"status": "error", "message": _err_text(e)})
    finally:
        _cancelled.discard(download_id)
        _tls.download_id = None


def cancel(download_id):
    _cancelled.add(download_id)
    return json.dumps({"ok": True})


# -------------------------------------------------------------- diagnostics

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
        Session(impersonate="chrome")
        info["curl_cffi_chrome_impersonate_ok"] = True
    except Exception as e:
        info["curl_cffi_chrome_impersonate_ok"] = False
        info["curl_cffi_impersonate_error"] = repr(e)

    try:
        import yt_dlp
        info["yt_dlp_version"] = yt_dlp.version.__version__
        info["yt_dlp_import_ok"] = True
        info["yt_dlp_engine"] = _engine_path or "bundled"
    except Exception as e:
        info["yt_dlp_import_ok"] = False
        info["yt_dlp_error"] = repr(e)

    try:
        import yt_dlp
        with yt_dlp.YoutubeDL({"quiet": True, "logger": _Log()}) as ydl:
            targets = ydl._get_available_impersonate_targets()
        info["impersonate_targets"] = sorted({str(t) for t, _ in targets})[:12]
        info["yt_dlp_curl_cffi_handler_ok"] = bool(targets)
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
