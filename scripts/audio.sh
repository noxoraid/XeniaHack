#!/usr/bin/env bash
set -euo pipefail

out=public/audio
cache=.cache/piper
name=en_US-lessac-medium
base=https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium

mkdir -p "$out" "$cache"

for f in "$name.onnx" "$name.onnx.json"; do
  [ -s "$cache/$f" ] || curl -fsSL --retry 3 "$base/$f" -o "$cache/$f"
done

i=0
while IFS= read -r line || [ -n "$line" ]; do
  [ -n "$line" ] || continue
  i=$((i + 1))
  printf '%s' "$line" | piper --model "$cache/$name.onnx" --length_scale 1.05 --sentence_silence 0.2 --output_file "$out/vo-$i.raw.wav"
  ffmpeg -y -loglevel error -i "$out/vo-$i.raw.wav" -af "loudnorm=I=-16:TP=-2:LRA=7" -ar 44100 -ac 2 -c:a pcm_s16le "$out/vo-$i.wav"
  rm "$out/vo-$i.raw.wav"
done < scripts/voiceover.txt

expr=""
for n in "3 659.25" "5.2 587.33" "7.5 659.25" "10 880" "13.5 783.99" "16 659.25" "19 587.33" "21.5 659.25" "24.5 880" "27 659.25"; do
  set -- $n
  expr="$expr+gte(t,$1)*exp(-2.2*(t-$1))*sin(2*PI*$2*t)"
done

ffmpeg -y -loglevel error \
  -f lavfi -i "sine=frequency=110:sample_rate=44100:duration=30" \
  -f lavfi -i "sine=frequency=164.81:sample_rate=44100:duration=30" \
  -f lavfi -i "sine=frequency=220:sample_rate=44100:duration=30" \
  -f lavfi -i "sine=frequency=261.63:sample_rate=44100:duration=30" \
  -f lavfi -i "sine=frequency=329.63:sample_rate=44100:duration=30" \
  -f lavfi -i "aevalsrc='0.2*(0$expr)':s=44100:d=30" \
  -filter_complex "[0][1][2][3][4]amix=inputs=5:normalize=0,tremolo=f=0.2:d=0.35,lowpass=f=1200[pad];[pad][5]amix=inputs=2:normalize=0,aecho=0.8:0.6:90|180:0.35|0.2,volume=0.7,afade=t=in:d=3,afade=t=out:st=26:d=4,aformat=sample_fmts=s16:channel_layouts=stereo[out]" \
  -map "[out]" -t 30 "$out/music.wav"

ls -l "$out"
