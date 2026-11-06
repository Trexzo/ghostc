#!/usr/bin/env python3
"""Local F2 baseline/experimental screenshot review. No game access needed.

Embeds two validated same-dimension Minecraft PNGs in one offline HTML page.
No external network, file uploads, image manipulation or FPS claims.
"""
import argparse
import base64
import hashlib
import html
from pathlib import Path
import struct
import zlib

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
MAX_FILE_BYTES = 50 * 1024 * 1024
MAX_DIMENSION = 16384


def validate_png(data):
    if len(data) > MAX_FILE_BYTES:
        raise ValueError("PNG exceeds 50 MiB review limit")
    if not data.startswith(PNG_SIGNATURE):
        raise ValueError("not a PNG screenshot")
    offset = len(PNG_SIGNATURE)
    size = None
    seen_idat = False
    ended = False
    while offset + 12 <= len(data):
        count = struct.unpack_from(">I", data, offset)[0]
        if count > len(data) - offset - 12:
            raise ValueError("truncated PNG chunk")
        name = data[offset + 4:offset + 8]
        content = data[offset + 8:offset + 8 + count]
        stored = struct.unpack_from(">I", data, offset + 8 + count)[0]
        actual = zlib.crc32(name + content) & 0xffffffff
        if actual != stored:
            raise ValueError("invalid PNG chunk CRC")
        if offset == 8:
            if name != b"IHDR" or count != 13:
                raise ValueError("missing PNG IHDR")
            width, height, depth, color, compression, filtering, interlace = (
                struct.unpack(">IIBBBBB", content)
            )
            if not (1 <= width <= MAX_DIMENSION and
                    1 <= height <= MAX_DIMENSION):
                raise ValueError("invalid PNG dimensions")
            if (depth, color, compression, filtering, interlace) not in (
                (8, 2, 0, 0, 0),
                (8, 6, 0, 0, 0),
            ):
                raise ValueError("expected Minecraft F2 8-bit RGB/RGBA PNG")
            size = (width, height)
        elif name == b"IHDR":
            raise ValueError("duplicate PNG IHDR")
        elif name == b"IDAT":
            seen_idat = True
        elif name == b"IEND":
            if count or not seen_idat:
                raise ValueError("invalid PNG end")
            ended = True
            offset += 12
            break
        offset += count + 12
    if not ended or offset != len(data) or size is None:
        raise ValueError("incomplete PNG image")
    return size


HTML = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>GhostC Slate — native screenshot A/B</title>
<style>
:root{color-scheme:dark}
body{font:14px system-ui,sans-serif;background:#11191f;color:#e8eef2;
margin:0;padding:24px}
main{max-width:1360px;margin:auto}
h1{font-size:24px;font-weight:650}p{line-height:1.55;color:#bfcbd2}
fieldset{border:1px solid #3a4e5b;border-radius:10px;padding:14px}
label{display:block;margin:6px 0 10px}
input{width:100%;accent-color:#a9d7e4}
.stack{position:relative;width:100%;margin-top:18px;border:1px solid #3a4e5b}
.stack img{display:block;width:100%;height:auto}
#experiment{position:absolute;inset:0;opacity:0.5}
.meta{overflow-wrap:anywhere;font-size:12px;color:#bfcbd2}
strong{color:#e8eef2}
</style></head>
<body><main><h1>GhostC Slate — native screenshot A/B</h1>
<p>Use two F2 screenshots from the <strong>same resolution and GUI scale</strong>.
Slide to fade between the default Minecraft font and the optional experimental
font. World movement, cursor position and FPS can change independently; this
is <strong>visual evidence only</strong>, not performance or release certification.</p>
<fieldset><label for="blend">Experimental screenshot opacity: <output id="pct">50%</output></label>
<input type="range" min="0" max="100" value="50" id="blend"></fieldset>
<p class="meta">Baseline: __BASE_NAME__ (SHA-256 __BASE_HASH__)<br>
Experimental: __EXP_NAME__ (SHA-256 __EXP_HASH__)<br>
Resolution: __DIMS__ · Both files are embedded; no internet connection required.</p>
<div class="stack">
<img id="baseline" alt="Default Minecraft font" src="data:image/png;base64,__BASE_DATA__">
<img id="experiment" alt="Experimental Slate font" src="data:image/png;base64,__EXP_DATA__">
</div>
<p>Review clipped labels, baselines, descenders, selected/hover states and
F7 HUD previews at Auto, 2 and 3 GUI scales. Run each pair separately.
For FPS and GL/resource behavior, use the native acceptance checklist.</p>
</main><script>
const slider=document.getElementById("blend");
slider.addEventListener("input",function(){
document.getElementById("experiment").style.opacity=String(Number(this.value)/100);
document.getElementById("pct").textContent=this.value+"%";
});
</script></body></html>
"""


def make_review(baseline, experimental, output):
    baseline = Path(baseline)
    experimental = Path(experimental)
    output = Path(output)
    if output.resolve() in (baseline.resolve(), experimental.resolve()):
        raise ValueError("output must not replace an original screenshot")
    base_data = baseline.read_bytes()
    exp_data = experimental.read_bytes()
    base_size = validate_png(base_data)
    exp_size = validate_png(exp_data)
    if base_size != exp_size:
        raise ValueError("screenshots have different pixel dimensions")
    substitutions = {
        "__BASE_NAME__": html.escape(baseline.name),
        "__EXP_NAME__": html.escape(experimental.name),
        "__BASE_HASH__": hashlib.sha256(base_data).hexdigest(),
        "__EXP_HASH__": hashlib.sha256(exp_data).hexdigest(),
        "__DIMS__": "%d × %d" % base_size,
        "__BASE_DATA__": base64.b64encode(base_data).decode("ascii"),
        "__EXP_DATA__": base64.b64encode(exp_data).decode("ascii"),
    }
    page = HTML
    for key, value in substitutions.items():
        page = page.replace(key, value)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(page, encoding="utf-8")
    return base_size


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True, type=Path,
                        help="default-font Minecraft F2 PNG")
    parser.add_argument("--experimental", required=True, type=Path,
                        help="experimental-font Minecraft F2 PNG")
    parser.add_argument("--output", required=True, type=Path,
                        help="self-contained local HTML review page")
    args = parser.parse_args()
    try:
        size = make_review(args.baseline, args.experimental, args.output)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print("SLATE_VISUAL_REVIEW_READY %dx%d output=%s" %
          (size[0], size[1], args.output))


if __name__ == "__main__":
    main()
