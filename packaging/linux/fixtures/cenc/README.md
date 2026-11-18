This synthetic fixture is a one-second red H.264 video. It contains no provider material. The following FFmpeg 9.0.2 command generated the manifest and two CENC segments.

```sh
ffmpeg -f lavfi -i 'color=c=red:size=64x48:rate=25' -t 1 -an \
  -c:v libx264 -pix_fmt yuv420p -g 25 -f dash -seg_duration 1 \
  -format_options 'encryption_scheme=cenc-aes-ctr:encryption_key=00112233445566778899aabbccddeeff:encryption_kid=00000000000000000000000000000001' \
  probe.mpd
```
