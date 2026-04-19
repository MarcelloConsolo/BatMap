import pandas as pd
import os

def verify_file(filename):
    if not os.path.exists(filename):
        print(f"--- {filename}: NON TROVATO ---")
        return

    try:
        df = pd.read_excel(filename)
        total = len(df)

        # Mappa colonne
        cols = {str(c).lower(): c for c in df.columns}
        c_lat = next((cols[k] for k in cols if 'lat' in k), None)
        c_lon = next((cols[k] for k in cols if 'lon' in k), None)
        c_prov = next((cols[k] for k in cols if 'provin' in k), None)
        c_com = next((cols[k] for k in cols if 'comune' in k), None)
        c_loc = next((cols[k] for k in cols if 'localit' in k), None)

        has_coords = df[c_lat].notna().sum() if c_lat else 0
        has_prov_sigla = df[c_prov].apply(lambda x: len(str(x)) == 2).sum() if c_prov else 0

        missing_coords = df[df[c_lat].isna() | (df[c_lat] == 0)] if c_lat else df

        print(f"--- Report: {filename} ---")
        print(f"Totale righe: {total}")
        print(f"Righe con coordinate: {has_coords} / {total}")
        print(f"Righe con provincia (sigla): {has_prov_sigla} / {total}")

        if len(missing_coords) > 0 and c_com:
            print("Esempi di fallimenti geocodifica (Comune - Località):")
            for _, row in missing_coords.head(5).iterrows():
                print(f"  - {row.get(c_com, 'N/D')} - {row.get(c_loc, 'N/D')}")
        print("-" * 30)

    except Exception as e:
        print(f"Errore su {filename}: {e}")

if __name__ == "__main__":
    for year in [2022, 2023, 2024, 2025]:
        verify_file(f"Pipistrelli {year}.xlsx")
