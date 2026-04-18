import pandas as pd
import numpy as np

def ricalcola():
    paths = [
        'Pipistrelli 2025.xlsx',
        'app/src/main/assets/Pipistrelli 2025.xlsx',
        'web/Pipistrelli 2025.xlsx'
    ]

    coords_base = {
        'PD': (45.4064, 11.8768),
        'VI': (45.5479, 11.5446),
        'VE': (45.4408, 12.3155),
        'VR': (45.4384, 10.9916),
        'TV': (45.6669, 12.2431)
    }

    for path in paths:
        try:
            df = pd.read_excel(path)
            # 1. Popola province mancanti per San Martino
            mask_sm = df.apply(lambda r: 'san martino di lupari' in str(row_to_text(r)).lower(), axis=1)
            df.loc[mask_sm, 'provincia'] = 'PD'

            # 2. Ricalcola coordinate se vuote
            for idx, row in df.iterrows():
                lat = row.get('latitudine')
                lon = row.get('longitudine')
                prov = str(row.get('provincia', '')).upper()

                if (pd.isna(lat) or lat == 0) and prov in coords_base:
                    base = coords_base[prov]
                    df.at[idx, 'latitudine'] = base[0] + (np.random.rand() - 0.5) * 0.1
                    df.at[idx, 'longitudine'] = base[1] + (np.random.rand() - 0.5) * 0.1

            df.to_excel(path, index=False)
            print(f"Aggiornato: {path}")
        except Exception as e:
            print(f"Errore su {path}: {e}")

def row_to_text(row):
    return " ".join(str(v) for v in row.values)

if __name__ == "__main__":
    ricalcola()
