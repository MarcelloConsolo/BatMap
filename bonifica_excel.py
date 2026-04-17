import pandas as pd
import os

path = 'app/src/main/assets/Pipistrelli 2025.xlsx'

if os.path.exists(path):
    print(f"Sto analizzando: {path}")
    # Carica l'excel
    df = pd.read_excel(path)

    # Mappatura comuni -> province
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
        # Se la provincia è vuota o non valida
        if prov == 'NAN' or prov == '' or prov == '-' or len(prov) != 2:
            comune = str(row['comune']).lower()
            for k, v in mapping.items():
                if k in comune:
                    return v
        return row['provincia']

    # Applica la bonifica
    df['provincia'] = df.apply(fill_prov, axis=1)

    # Salva il file sovrascrivendolo
    df.to_excel(path, index=False)
    print("✅ Excel Bonificato con successo! Le province mancanti sono state inserite.")
else:
    print("❌ File non trovato!")
