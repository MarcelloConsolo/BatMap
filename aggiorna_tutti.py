import pandas as pd
import os

paths = [
    'Pipistrelli 2025.xlsx',
    'web/Pipistrelli 2025.xlsx',
    'app/src/main/assets/Pipistrelli 2025.xlsx'
]

mapping = {
    'castelgomberto': 'VI',
    'san vito': 'VI',
    'arcugnano': 'VI',
    'marano': 'VI',
    'breganze': 'VI',
    'costabissara': 'VI',
    'cappella maggiore': 'TV',
    'modena': 'MO'
}

def fill_prov(row):
    prov = str(row['provincia']).strip().upper()
    if prov == 'NAN' or prov == '' or prov == '-' or len(prov) != 2:
        comune = str(row['comune']).lower()
        for k, v in mapping.items():
            if k in comune:
                return v
    return row['provincia']

for p in paths:
    if os.path.exists(p):
        print(f"Aggiornamento di: {p}...")
        df = pd.read_excel(p)
        df['provincia'] = df.apply(fill_prov, axis=1)
        df.to_excel(p, index=False)
        print(f"✅ {p} aggiornato con successo.")
    else:
        print(f"⚠️ File non trovato: {p}")
