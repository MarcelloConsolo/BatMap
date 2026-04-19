import pandas as pd
import urllib.request
import urllib.parse
import json
import time
import os
import numpy as np

# Configurazione Nominatim
USER_AGENT = "BatMapsDataCompleter/1.0"
EMAIL = "marcello.consolo@gmail.com"

def get_coords(address, city):
    try:
        query = f"{address}, {city}" if address and str(address).lower() != str(city).lower() else city
        encoded_query = urllib.parse.quote(query)
        url = f"https://nominatim.openstreetmap.org/search?q={encoded_query}&format=json&limit=1&email={EMAIL}"

        req = urllib.request.Request(url)
        req.add_header('User-Agent', USER_AGENT)

        with urllib.request.urlopen(req, timeout=10) as response:
            data = json.loads(response.read().decode())
            if data:
                return float(data[0]['lat']), float(data[0]['lon'])
    except Exception as e:
        print(f"  ! Errore Nominatim per {query}: {e}")
    return None, None

def process_file(filename):
    if not os.path.exists(filename):
        print(f"File {filename} non trovato, salto.")
        return False

    print(f"\n>>> Elaborazione: {filename}")
    try:
        # Legge il file Excel
        df = pd.read_excel(filename)

        # Mappa colonne (case-insensitive e senza accenti)
        cols_map = {}
        for c in df.columns:
            c_low = str(c).lower().replace('à','a').replace('ì','i')
            if 'specie' in c_low: cols_map['specie'] = c
            elif 'localit' in c_low: cols_map['loc'] = c
            elif 'comune' in c_low: cols_map['comune'] = c
            elif 'provin' in c_low: cols_map['prov'] = c
            elif 'lat' in c_low: cols_map['lat'] = c
            elif 'lon' in c_low: cols_map['lon'] = c

        # Crea colonne mancanti
        if 'lat' not in cols_map: df['Latitudine'] = np.nan; cols_map['lat'] = 'Latitudine'
        if 'lon' not in cols_map: df['Longitudine'] = np.nan; cols_map['lon'] = 'Longitudine'
        if 'prov' not in cols_map: df['Provincia'] = ''; cols_map['prov'] = 'Provincia'

        updated_count = 0
        prov_map = {'PADOVA': 'PD', 'VICENZA': 'VI', 'VENEZIA': 'VE', 'VERONA': 'VR', 'TREVISO': 'TV', 'ROVIGO': 'RO', 'BELLUNO': 'BL'}

        for idx, row in df.iterrows():
            loc = str(row.get(cols_map.get('loc', ''), '')).strip()
            com = str(row.get(cols_map.get('comune', ''), '')).strip()
            if loc == 'nan': loc = ''
            if not com or com == 'nan': continue

            # 1. Sigla Provincia
            p_val = str(row.get(cols_map.get('prov', ''), '')).strip().upper()
            if len(p_val) > 2:
                for name, sigla in prov_map.items():
                    if name in p_val:
                        df.at[idx, cols_map['prov']] = sigla
                        break
            elif not p_val: # Tenta di dedurla se vuota (opzionale)
                pass

            # 2. Geocodifica
            print(f"  [{idx+1}/{len(df)}] Geocodifica: {loc} {com}...", end="\r")
            lat, lon = get_coords(loc, com)

            # Fallback solo comune se via fallisce
            if not lat and loc:
                lat, lon = get_coords("", com)

            if lat:
                df.at[idx, cols_map['lat']] = lat
                df.at[idx, cols_map['lon']] = lon
                updated_count += 1

            time.sleep(1.2) # Rispetto policy Nominatim

        # Salvataggio
        df.to_excel(filename, index=False)
        print(f"\n  ✅ Completato: {updated_count} coordinate aggiornate.")
        return True
    except Exception as e:
        print(f"  ❌ Errore critico su {filename}: {e}")
        import traceback
        traceback.print_exc()
        return False

def sync_files(files):
    dest_app = "app/src/main/assets/"
    dest_web = "web/"

    if not os.path.exists(dest_app): os.makedirs(dest_app)
    if not os.path.exists(dest_web): os.makedirs(dest_web)

    for f in files:
        if os.path.exists(f):
            # Copia pulita
            import shutil
            shutil.copy2(f, os.path.join(dest_app, f))
            shutil.copy2(f, os.path.join(dest_web, f))
            print(f"  Sync: {f} -> App & Web")

if __name__ == "__main__":
    target_files = [
        "Pipistrelli 2022.xlsx",
        "Pipistrelli 2023.xlsx",
        "Pipistrelli 2024.xlsx",
        "Pipistrelli 2025.xlsx"
    ]

    for f in target_files:
        process_file(f)

    print("\n--- Sincronizzazione ---")
    sync_files(target_files)
    print("\nFine processo.")
