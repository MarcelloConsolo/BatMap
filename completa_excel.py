import pandas as pd
import json
import os
import shutil
import re

def pulisci_excel():
    excel_path = 'Pipistrelli 2025.xlsx'
    json_path = 'app/src/main/assets/comuni_italiani.json'

    if not os.path.exists(excel_path):
        print(f"Errore: {excel_path} non trovato.")
        return

    # Backup di sicurezza
    shutil.copy(excel_path, 'Pipistrelli 2025_BACKUP.xlsx')

    # Carica database comuni
    with open(json_path, encoding='utf-8') as f:
        db_comuni = json.load(f)

    # Carica Excel
    df = pd.read_excel(excel_path)
    header_row_idx = 0
    for i, row in df.iterrows():
        row_str = str(row.values).lower()
        if 'specie' in row_str or 'comune' in row_str:
            header_row_idx = i
            break
    df = pd.read_excel(excel_path, header=header_row_idx)

    def correggi_riga(row):
        comune_attuale = str(row.get('Comune', '')).strip().lower()
        localita = str(row.get('Località', '')).strip()
        localita_low = localita.lower()
        provincia = str(row.get('Provincia', '')).strip().upper()

        comune_trovato = None

        # Cerca il comune nel testo della località
        for nome_comune in db_comuni.keys():
            if len(nome_comune) > 3 and nome_comune in localita_low:
                comune_trovato = nome_comune
                break

        if comune_trovato:
            # 1. Imposta il Comune (se mancante o se lo abbiamo trovato ora)
            if comune_attuale in ['', 'nan', '-', 'none']:
                row['Comune'] = comune_trovato.capitalize()
                if provincia in ['', 'NAN', '-', 'NONE']:
                    row['Provincia'] = db_comuni[comune_trovato]['prov']

            # 2. PULIZIA LOCALITÀ: Rimuove il nome del comune dalla stringa della località
            # Esempio: "Via raccola 17, galzignano terme" -> "Via raccola 17"
            nuova_loc = re.sub(re.escape(comune_trovato), '', localita, flags=re.IGNORECASE)
            # Pulisce virgole finali, spazi e trattini
            nuova_loc = nuova_loc.strip().rstrip(',').rstrip(';').strip()
            row['Località'] = nuova_loc

        # Se il comune c'è ma manca la provincia
        elif comune_attuale in db_comuni and (provincia in ['', 'NAN', '-', 'NONE']):
            row['Provincia'] = db_comuni[comune_attuale]['prov']

        return row

    print("Pulizia profonda in corso (rimozione comuni dalla colonna Località)...")
    df = df.apply(correggi_riga, axis=1)

    output_paths = [
        'Pipistrelli 2025.xlsx',
        'web/Pipistrelli 2025.xlsx',
        'app/src/main/assets/Pipistrelli 2025.xlsx'
    ]

    for out in output_paths:
        if os.path.exists(os.path.dirname(out) or '.'):
            df.to_excel(out, index=False)
            print(f"✅ Sincronizzato: {out}")

if __name__ == "__main__":
    pulisci_excel()
