#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
output_dir="${PALM_AC_TEST_BUILD:-/tmp/palm-ac-engine-tests}"
mkdir -p "$output_dir"
src="$repo_root/third_party/IRremoteESP8266/src"
adapter="$repo_root/app/src/main/cpp"
flags=(-std=c++11 -O1 -g -DUNIT_TEST -D_IR_LOCALE_=en-AU -I"$src" -I"$adapter/capture" -I"$adapter")
export src adapter output_dir
printf '%s\0' "$src"/ir_*.cpp "$src"/IRac.cpp "$src"/IRsend.cpp "$src"/IRrecv.cpp "$src"/IRtimer.cpp "$src"/IRtext.cpp "$src"/IRutils.cpp "$adapter"/ac_engine.cpp |
  xargs -0 -P "${PALM_AC_TEST_JOBS:-4}" -I '{}' bash -c '
    input="$1"; object="$output_dir/$(basename "$input" .cpp).o"
    if [[ ! -e "$object" || "$input" -nt "$object" || "$adapter/capture/IRsend_test.h" -nt "$object" ]]; then
      g++ -std=c++11 -O1 -g -DUNIT_TEST -D_IR_LOCALE_=en-AU -I"$src" -I"$adapter/capture" -I"$adapter" -c "$input" -o "$object"
    fi
  ' _ '{}'
g++ "${flags[@]}" "$repo_root/native_ir/tests/ac/ac_engine_test.cpp" "$output_dir"/*.o -pthread -o "$output_dir/ac_engine_test"
javac -d "$output_dir/classes" "$repo_root"/app/src/main/java/com/universalremote/app/ac/*.java "$repo_root/native_ir/tests/ac/AcCatalogDump.java"
java -cp "$output_dir/classes" AcCatalogDump > "$output_dir/catalog.tsv"
"$output_dir/ac_engine_test" "$output_dir/catalog.tsv"
