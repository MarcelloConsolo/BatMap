import pandas as pd
import os
import shutil

def processa_excel(path):
    if not os.path.exists(path):
        print(f"File non trovato: {path}")
        return

    print(f"Elaborazione di: {path}...")
    df = pd.read_excel(path)

    # 1. Normalizzazione Colonne (ignora maiuscole/minuscole)
    mapping = {}
    for c in df.columns:
        l = str(c).lower().strip()
        if 'comune' in l: mapping[c] = 'Comune'
        elif 'prov' in l or 'sigla' in l: mapping[c] = 'Provincia'
        elif 'lat' in l: mapping[c] = 'Latitudine'
        elif 'lon' in l: mapping[c] = 'Longitudine'
        elif 'localit' in l: mapping[c] = 'Località'
        elif 'specie' in l: mapping[c] = 'Specie'
        elif 'data' in l: mapping[c] = 'Data'
        elif 'ora' in l: mapping[c] = 'Ora'
        elif 'stato' in l: mapping[c] = 'Stato'
        elif 'note' in l or 'condizioni' in l: mapping[c] = 'Note'
    df.rename(columns=mapping, inplace=True)

    # 2. Correzioni Specifiche
    for i in df.index:
        com = str(df.at[i, 'Comune']).lower().strip()
        loc = str(df.at[i, 'Località']).lower().strip()
        sp = str(df.at[i, 'Specie']).lower().strip()

        # Bassano del Grappa
        if 'bassano' in com or 'bassano' in loc:
            df.at[i, 'Comune'] = 'Bassano del Grappa'
            df.at[i, 'Provincia'] = 'VI'
            df.at[i, 'Latitudine'] = 45.7667
            df.at[i, 'Longitudine'] = 11.7333

        # Modena
        elif 'modena' in com:
            df.at[i, 'Comune'] = 'Modena'
            df.at[i, 'Provincia'] = 'MO'
            df.at[i, 'Latitudine'] = 44.6471
            df.at[i, 'Longitudine'] = 10.9252

        # Eptesicus nilssonii (Cornigian, Belluno)
        elif 'nilson' in sp or 'nilsson' in sp:
            df.at[i, 'Comune'] = 'Belluno'
            df.at[i, 'Località'] = 'Cornigian'
            df.at[i, 'Provincia'] = 'BL'
            df.at[i, 'Latitudine'] = 46.3488
            df.at[i, 'Longitudine'] = 12.1833

        # Pulizia Provincia
        p = str(df.at[i, 'Provincia']).replace('(', '').replace(')', '').upper().strip()
        if p == 'NAN' or not p: p = 'VI' # Default Vicenza
        df.at[i, 'Provincia'] = p

    # Salva il file aggiornato
    df.to_excel(path, index=False)
    print(f"OK: {path} aggiornato con successo.")

# Percorsi dei file
file_web = 'web/Pipistrelli 2025.xlsx'
file_app = 'app/src/main/assets/Pipistrelli 2025.xlsx'

# Elabora quello del web
processa_excel(file_web)

# Copia il file aggiornato dal web all'app per tenerli sincronizzati
if os.path.exists(file_web):
    shutil.copy2(file_web, file_app)
    print(f"Sincronizzato: {file_app} è ora uguale a {file_web}")
