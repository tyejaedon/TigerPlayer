#!/usr/bin/env python3
"""
Sync and compress AutoEq headphone measurement models for TigerPlayer.

Can be run via:
    python3 tools/sync_autoeq_index.py

Connects to Jaakko Pasanen's AutoEq database (https://github.com/jaakkopasanen/AutoEq),
parses available models across major measurement databases (Oratory1990, Crinacle, Rtings, etc.),
and packages a compressed models_index.json.gz directly into app/src/main/assets/autoeq/.
"""

import gzip
import json
import os
import re
import sys
import urllib.request

AUTOEQ_INDEX_URL = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md"
OUTPUT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app", "src", "main", "assets", "autoeq")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "models_index.json.gz")

# Curated fallback models to guarantee instant offline availability
CURATED_MODELS = [
    # Sony
    {"id": "sony_wh_1000xm4_oratory", "name": "Sony WH-1000XM4", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sony WH-1000XM4"},
    {"id": "sony_wh_1000xm5_oratory", "name": "Sony WH-1000XM5", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sony WH-1000XM5"},
    {"id": "sony_wf_1000xm4_crinacle", "name": "Sony WF-1000XM4", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Sony WF-1000XM4"},
    {"id": "sony_wf_1000xm5_crinacle", "name": "Sony WF-1000XM5", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Sony WF-1000XM5"},
    {"id": "sony_mdr_7506_oratory", "name": "Sony MDR-7506", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sony MDR-7506"},
    
    # Apple
    {"id": "apple_airpods_pro_2_crinacle", "name": "Apple AirPods Pro 2", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Apple AirPods Pro 2"},
    {"id": "apple_airpods_pro_crinacle", "name": "Apple AirPods Pro", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Apple AirPods Pro"},
    {"id": "apple_airpods_max_oratory", "name": "Apple AirPods Max", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Apple AirPods Max"},
    {"id": "apple_airpods_3_rtings", "name": "Apple AirPods 3", "source": "rtings", "form": "Earbud", "target": "Harman Over-Ear 2018", "path": "rtings/harman_over-ear_2018/Apple AirPods 3"},

    # Sennheiser
    {"id": "sennheiser_hd_600_oratory", "name": "Sennheiser HD 600", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sennheiser HD 600"},
    {"id": "sennheiser_hd_650_oratory", "name": "Sennheiser HD 650", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sennheiser HD 650"},
    {"id": "sennheiser_hd_560s_oratory", "name": "Sennheiser HD 560S", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sennheiser HD 560S"},
    {"id": "sennheiser_hd_800s_oratory", "name": "Sennheiser HD 800 S", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sennheiser HD 800 S"},
    {"id": "sennheiser_momentum_4_oratory", "name": "Sennheiser Momentum 4 Wireless", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Sennheiser Momentum 4 Wireless"},

    # Bose
    {"id": "bose_qc45_oratory", "name": "Bose QuietComfort 45", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Bose QuietComfort 45"},
    {"id": "bose_700_oratory", "name": "Bose Noise Cancelling Headphones 700", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Bose Noise Cancelling Headphones 700"},
    {"id": "bose_qc_ultra_rtings", "name": "Bose QuietComfort Ultra", "source": "rtings", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "rtings/harman_over-ear_2018/Bose QuietComfort Ultra"},

    # Beyerdynamic
    {"id": "beyer_dt_770_pro_80_oratory", "name": "Beyerdynamic DT 770 Pro 80 Ohm", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Beyerdynamic DT 770 Pro 80 Ohm"},
    {"id": "beyer_dt_990_pro_250_oratory", "name": "Beyerdynamic DT 990 Pro 250 Ohm", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Beyerdynamic DT 990 Pro 250 Ohm"},
    {"id": "beyer_dt_1990_pro_oratory", "name": "Beyerdynamic DT 1990 Pro (Balanced Pads)", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Beyerdynamic DT 1990 Pro (Balanced Pads)"},

    # Audio-Technica
    {"id": "ath_m50x_oratory", "name": "Audio-Technica ATH-M50x", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Audio-Technica ATH-M50x"},
    {"id": "ath_m50xbt_oratory", "name": "Audio-Technica ATH-M50xBT", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Audio-Technica ATH-M50xBT"},
    {"id": "ath_m40x_oratory", "name": "Audio-Technica ATH-M40x", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Audio-Technica ATH-M40x"},

    # Samsung
    {"id": "samsung_galaxy_buds2_pro_crinacle", "name": "Samsung Galaxy Buds2 Pro", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Samsung Galaxy Buds2 Pro"},
    {"id": "samsung_galaxy_buds_pro_crinacle", "name": "Samsung Galaxy Buds Pro", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Samsung Galaxy Buds Pro"},
    {"id": "samsung_galaxy_buds_fe_crinacle", "name": "Samsung Galaxy Buds FE", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Samsung Galaxy Buds FE"},

    # Moondrop
    {"id": "moondrop_blessing_2_crinacle", "name": "Moondrop Blessing 2", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Moondrop Blessing 2"},
    {"id": "moondrop_blessing_3_crinacle", "name": "Moondrop Blessing 3", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Moondrop Blessing 3"},
    {"id": "moondrop_aria_crinacle", "name": "Moondrop Aria", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Moondrop Aria"},
    {"id": "moondrop_chu_2_crinacle", "name": "Moondrop Chu II", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Moondrop Chu II"},
    {"id": "moondrop_kato_crinacle", "name": "Moondrop Kato", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Moondrop Kato"},

    # HiFiMAN
    {"id": "hifiman_sundara_oratory", "name": "HiFiMAN Sundara (2020 revision)", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/HiFiMAN Sundara (2020 revision)"},
    {"id": "hifiman_edition_xs_oratory", "name": "HiFiMAN Edition XS", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/HiFiMAN Edition XS"},
    {"id": "hifiman_ananda_oratory", "name": "HiFiMAN Ananda", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/HiFiMAN Ananda"},

    # AKG
    {"id": "akg_k371_oratory", "name": "AKG K371", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/AKG K371"},
    {"id": "akg_k702_oratory", "name": "AKG K702", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/AKG K702"},

    # Anker Soundcore
    {"id": "anker_soundcore_q30_rtings", "name": "Anker Soundcore Life Q30", "source": "rtings", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "rtings/harman_over-ear_2018/Anker Soundcore Life Q30"},
    {"id": "anker_soundcore_space_q45_rtings", "name": "Anker Soundcore Space Q45", "source": "rtings", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "rtings/harman_over-ear_2018/Anker Soundcore Space Q45"},
    {"id": "anker_soundcore_liberty_4_nc_rtings", "name": "Anker Soundcore Liberty 4 NC", "source": "rtings", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "rtings/harman_in-ear_2019v2/Anker Soundcore Liberty 4 NC"},

    # Shure
    {"id": "shure_se215_crinacle", "name": "Shure SE215", "source": "crinacle", "form": "In-Ear", "target": "Harman In-Ear 2019v2", "path": "crinacle/harman_in-ear_2019v2/Shure SE215"},
    {"id": "shure_srh840_oratory", "name": "Shure SRH840", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Shure SRH840"},

    # Focal & Audeze
    {"id": "focal_bathys_oratory", "name": "Focal Bathys", "source": "oratory1990", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "oratory1990/harman_over-ear_2018/Focal Bathys"},
    {"id": "audeze_maxwell_crinacle", "name": "Audeze Maxwell", "source": "crinacle", "form": "Over-Ear", "target": "Harman Over-Ear 2018", "path": "crinacle/harman_over-ear_2018/Audeze Maxwell"}
]

def fetch_online_index():
    try:
        print(f"Attempting to fetch remote AutoEq index from {AUTOEQ_INDEX_URL}...")
        req = urllib.request.Request(AUTOEQ_INDEX_URL, headers={'User-Agent': 'TigerPlayer-AutoEq-Sync/1.0'})
        with urllib.request.urlopen(req, timeout=10) as response:
            content = response.read().decode('utf-8')
            models = []
            # Line format in INDEX.md: - [Model Name](./results/source/target/Model Name)
            pattern = re.compile(r'-\s+\[(.*?)\]\(\./results/(.*?)/(.*?)/(.*?)\)')
            for match in pattern.finditer(content):
                name, source, target, model_dir = match.groups()
                form = "In-Ear" if "in-ear" in target.lower() or "iem" in name.lower() else "Over-Ear"
                model_id = re.sub(r'[^a-zA-Z0-9]+', '_', f"{name}_{source}").strip('_').lower()
                models.append({
                    "id": model_id,
                    "name": name,
                    "source": source,
                    "form": form,
                    "target": target.replace('_', ' ').title(),
                    "path": f"{source}/{target}/{model_dir}"
                })
            if models:
                print(f"Successfully parsed {len(models)} models from remote AutoEq repository.")
                return models
    except Exception as e:
        print(f"Notice: Remote AutoEq index fetch skipped ({e}). Falling back to curated base index.")
    return CURATED_MODELS

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    models = fetch_online_index()
    if not models:
        models = CURATED_MODELS

    # Deduplicate by id
    seen = set()
    deduped = []
    for m in models:
        if m["id"] not in seen:
            seen.add(m["id"])
            deduped.append(m)

    payload = json.dumps(deduped, separators=(',', ':')).encode('utf-8')
    with gzip.open(OUTPUT_FILE, 'wb') as f:
        f.write(payload)

    print(f"Wrote {len(deduped)} compressed AutoEq headphone models to {OUTPUT_FILE} ({len(payload)} raw bytes -> {os.path.getsize(OUTPUT_FILE)} gz bytes)")

if __name__ == "__main__":
    main()
