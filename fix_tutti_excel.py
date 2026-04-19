import pandas as pd
import urllib.request
import urllib.parse
import json
import time
import os
import numpy as np

USER_AGENT = "BatMapsFixer/3.0"
EMAIL = "marcello.consolo@gmail.com"

# Mappa comuni -> Provincia (Sigla)
PROV_MAP = {
    'vicenza': 'VI', 'bassano': 'VI', 'thiene': 'VI', 'schio': 'VI', 'montecchio': 'VI', 'arzignano': 'VI', 'valdagno': 'VI',
    'marostica': 'VI', 'recoaro': 'VI', 'santorso': 'VI', 'caltrano': 'VI', 'altavilla': 'VI', 'creazzo': 'VI', 'torri di quartesolo': 'VI',
    'barbarano': 'VI', 'barbarno': 'VI', 'mossano': 'VI', 'quinto vicentino': 'VI', 'bolzano vicentino': 'VI',
    'padova': 'PD', 'abano': 'PD', 'selvazzano': 'PD', 'vigonza': 'PD', 'cittadella': 'PD', 'monselice': 'PD', 'este': 'PD',
    'albignasego': 'PD', 'rubano': 'PD', 'ponte san nicolo': 'PD', 'san martino di lupari': 'PD',
    'venezia': 'VE', 'mestre': 'VE', 'chioggia': 'VE', 'san dona': 'VE', 'jesolo': 'VE', 'portogruaro': 'VE', 'mirano': 'VE', 'spinea': 'VE',
    'verona': 'VR', 'villafranca': 'VR', 'san giovanni lupatoto': 'VR', 'san bonifacio': 'VR', 'bussolengo': 'VR',
    'treviso': 'TV', 'conegliano': 'TV', 'castelfranco': 'TV', 'montebelluna': 'TV', 'vittorio veneto': 'TV', 'mogliano': 'TV',
    'rovigo': 'RO', 'adria': 'RO', 'porto viro': 'RO',
    'belluno': 'BL', 'feltre': 'BL'
}

def get_prov_sigla(comune):
    c = str(comune).lower()
    for key, sigla in PROV_MAP.items():
        if key in c: return sigla
    return ""

def get_coords(loc, com):
    try:
        com_clean = com.replace("Barbarno", "Barbarano")
        query = f"{loc}, {com_clean}" if loc and str(loc).lower() != str(com_clean).lower() else com_clean
        url = f"https://nominatim.openstreetmap.org/search?q={urllib.parse.quote(query)}&format=json&limit=1&email={EMAIL}"
        req = urllib.request.Request(url, headers={'User-Agent': USER_AGENT})
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            if data: return float(data[0]['lat']), float(data[0]['lon'])
    except: pass
    return None, None

def fix_excel(filename):
    if not os.path.exists(filename): return
    print(f"\n--- Analisi: {filename} ---")
    df = pd.read_excel(filename)

    # Normalizzazione nomi colonne
    cols_orig = {str(c).lower(): c for c in df.columns}
    c_com = next((cols_orig[k] for k in cols_orig if 'comune' in k), 'Comune')
    c_loc = next((cols_orig[k] for k in cols_orig if 'localit' in k), 'Località')
    c_prov = next((cols_orig[k] for k in cols_orig if 'provin' in k), 'Provincia')
    c_lat = next((cols_orig[k] for k in cols_orig if 'lat' in k), 'Latitudine')
    c_lon = next((cols_orig[k] for k in cols_orig if 'lon' in k), 'Longitudine')

    # Forza tipi di dato per evitare errori
    if c_prov not in df.columns: df[c_prov] = ""
    df[c_prov] = df[c_prov].astype(str).replace('nan', '')
    if c_lat not in df.columns: df[c_lat] = np.nan
    if c_lon not in df.columns: df[c_lon] = np.nan

    count_p = 0
    count_c = 0

    for idx, row in df.iterrows():
        com = str(row.get(c_com, '')).strip()
        loc = str(row.get(c_loc, '')).strip()
        if not com or com == 'nan' or com == "": continue

        # 1. Sigla Provincia
        sigla_attuale = str(row.get(c_prov, '')).strip()
        if len(sigla_attuale) != 2:
            sigla_nuova = get_prov_sigla(com)
            if sigla_nuova:
                df.at[idx, c_prov] = sigla_nuova
                count_p += 1

        # 2. Coordinate
        cur_lat = row.get(c_lat)
        if pd.isna(cur_lat) or cur_lat == 0:
            print(f"  [{idx+1}] Cerco GPS per: {com} {loc}...", end="\r")
            lat, lon = get_coords(loc, com)
            if not lat and loc: lat, lon = get_coords("", com)

            if lat:
                df.at[idx, c_lat] = lat
                df.at[idx, c_lon] = lon
                count_c += 1
                time.sleep(1.2)

    df.to_excel(filename, index=False)
    print(f"  Fatto! {filename}: Province aggiornate: {count_p}, Coordinate trovate: {count_c}")

for y in [2022, 2023, 2024, 2025]:
    fix_excel(f"Pipistrelli {y}.xlsx")

# Sync finale
print("\nSincronizzazione file...")
for y in [2022, 2023, 2024, 2025]:
    f = f"Pipistrelli {y}.xlsx"
    if os.path.exists(f):
        import shutil
        os.makedirs("app/src/main/assets", exist_ok=True)
        os.makedirs("web", exist_ok=True)
        shutil.copy2(f, f"app/src/main/assets/{f}")
        shutil.copy2(f, f"web/{f}")
        print(f"  OK: {f}")
