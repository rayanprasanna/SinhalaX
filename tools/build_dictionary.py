#!/usr/bin/env python3
"""
Builds app/src/main/assets/dict/si_words.txt from a "word frequency" list.

Source used during development: verified_word_list_200K.si from
https://github.com/nlpcuom/Word-Frequency-List-for-Sinhala (Fernando & Dias, ICON 2021).
That repository states no licence - get written permission before publishing, or swap in another source.

Output: one "word<TAB>frequency" per line, sorted by lookup key (word without ZWJ),
duplicates merged. Usage:
    python3 tools/build_dictionary.py verified_word_list_200K.si 100000
"""
import sys

ZWJ = "\u200d"

def is_sinhala_word(w: str) -> bool:
    return len(w) >= 1 and all("\u0d80" <= c <= "\u0dff" or c == ZWJ for c in w)

def main(src: str, limit: int, out: str) -> None:
    merged = {}  # key -> [display, freq, best_display_freq]
    with open(src, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) < 2 or not parts[-1].isdigit():
                continue
            word, freq = parts[0], int(parts[-1])
            if not is_sinhala_word(word):
                continue
            key = word.replace(ZWJ, "")
            entry = merged.get(key)
            if entry is None:
                merged[key] = [word, freq, freq]
            else:
                entry[1] += freq
                if freq > entry[2]:
                    entry[0], entry[2] = word, freq
    top = sorted(merged.items(), key=lambda kv: -kv[1][1])[:limit]
    top.sort(key=lambda kv: kv[0])  # sort by key (code point order == Kotlin String order for BMP)
    with open(out, "w", encoding="utf-8") as f:
        for key, (display, freq, _) in top:
            f.write(f"{display}\t{freq}\n")
    print(f"wrote {len(top)} words to {out}")

if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 100_000,
         sys.argv[3] if len(sys.argv) > 3 else "app/src/main/assets/dict/si_words.txt")
