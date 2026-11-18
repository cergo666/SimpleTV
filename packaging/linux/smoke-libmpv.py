"""Render a frame from a synthetic encrypted DASH stream using bundled libmpv."""

import ctypes as c
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import sys
from threading import Thread
import time


class Param(c.Structure):
    _fields_ = [("type", c.c_int), ("data", c.c_void_p)]


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_):
        pass


def render(mpv, handle, url):
    api = c.c_char_p(b"sw")
    context = c.c_void_p()
    assert mpv.mpv_render_context_create(
        c.byref(context), handle, (Param * 2)(Param(1, c.cast(api, c.c_void_p)), Param())
    ) >= 0
    try:
        command = (c.c_char_p * 3)(b"loadfile", url.encode(), None)
        assert mpv.mpv_command(handle, command) >= 0
        size = (c.c_int * 2)(64, 48)
        format_name = c.c_char_p(b"rgb0")
        stride = c.c_size_t(64 * 4)
        pixels = c.create_string_buffer(stride.value * 48 + 63)
        aligned = (c.addressof(pixels) + 63) & ~63
        blocking = c.c_int(0)
        params = (Param * 6)(
            Param(17, c.cast(size, c.c_void_p)),
            Param(18, c.cast(format_name, c.c_void_p)),
            Param(19, c.cast(c.byref(stride), c.c_void_p)),
            Param(20, aligned),
            Param(12, c.cast(c.byref(blocking), c.c_void_p)),
            Param(),
        )
        end = time.monotonic() + 10
        while time.monotonic() < end:
            if mpv.mpv_render_context_update(context) & 1:
                assert mpv.mpv_render_context_render(context, params) >= 0
                center = aligned + 24 * stride.value + 32 * 4
                red, green, blue = c.string_at(center, 3)
                if red > 200 and green < 40 and blue < 40:
                    print("Bundled libmpv rendered the expected red frame from CENC DASH")
                    return
            time.sleep(0.02)
        raise RuntimeError("Bundled libmpv did not render the expected decrypted frame")
    finally:
        mpv.mpv_render_context_free(context)


def main():
    mpv = c.CDLL(str(Path(sys.argv[1]).resolve()))
    mpv.mpv_create.restype = c.c_void_p
    mpv.mpv_initialize.argtypes = [c.c_void_p]
    mpv.mpv_set_option_string.argtypes = [c.c_void_p, c.c_char_p, c.c_char_p]
    mpv.mpv_command.argtypes = [c.c_void_p, c.POINTER(c.c_char_p)]
    mpv.mpv_render_context_create.argtypes = [c.POINTER(c.c_void_p), c.c_void_p, c.POINTER(Param)]
    mpv.mpv_render_context_update.argtypes = [c.c_void_p]
    mpv.mpv_render_context_update.restype = c.c_uint64
    mpv.mpv_render_context_render.argtypes = [c.c_void_p, c.POINTER(Param)]
    mpv.mpv_render_context_free.argtypes = [c.c_void_p]
    mpv.mpv_terminate_destroy.argtypes = [c.c_void_p]

    fixture = Path(__file__).resolve().parent / "fixtures" / "cenc"
    with ThreadingHTTPServer(("127.0.0.1", 0), partial(QuietHandler, directory=str(fixture))) as server:
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        handle = mpv.mpv_create()
        assert handle
        try:
            for option, value in [
                (b"vo", b"libmpv"),
                (b"ao", b"null"),
                (b"config", b"no"),
                (b"demuxer-lavf-format", b"dash"),
                (b"demuxer-lavf-o",
                 b"seg_max_retry=5,strict=experimental,allowed_extensions=ALL,"
                 b"protocol_whitelist=[udp,rtp,tcp,tls,data,file,http,https,crypto,httpproxy],"
                 b"decryption_key=00112233445566778899aabbccddeeff,"
                 b"cenc_decryption_key=00112233445566778899aabbccddeeff"),
            ]:
                assert mpv.mpv_set_option_string(handle, option, value) >= 0
            assert mpv.mpv_initialize(handle) >= 0
            render(mpv, handle, f"http://127.0.0.1:{server.server_port}/probe.mpd")
        finally:
            mpv.mpv_terminate_destroy(handle)
            server.shutdown()


if __name__ == "__main__":
    main()
