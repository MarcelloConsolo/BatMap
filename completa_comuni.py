import pandas as pd
from geopy.geocoders import Nominatim
from geopy.extra.rate_limiter import RateLimiter
import time
import os

# --- CONFIGURAZIONE ---
# Inserisci qui il nome del tuo file Excel
NOME_FILE_INPUT = "web/Pipistrelli 2025 new M v5a_CM.xlsx"
NOME_FILE_OUTPUT = "web/Pipistrelli_Completati.xlsx"

def completa_comuni():
    if not os.path.exists(NOME_FILE_INPUT):
        print(f"Errore: Il file {NOME_FILE_INPUT} non è stato trovato!")
        return

    print(f"Caricamento file: {NOME_FILE_INPUT}...")
    df = pd.read_excel(NOME_FILE_INPUT)

    # Inizializziamo il geocodificatore
    geolocator = Nominatim(user_agent="batmaps_geocoder_italy")
    # Limite di 1.2 secondi tra le richieste per non essere bloccati
    geocode = RateLimiter(geolocator.geocode, min_delay_seconds=1.2)

    print("Inizio ricerca comuni basata sulla località...")
    print("Nota: Il processo richiede circa 1.5 secondi per ogni riga vuota.")

    for index, row in df.iterrows():
        # Controlliamo i nomi delle colonne in italiano
        # Usiamo .get() o cerchiamo la colonna con nome simile (minuscolo/spazi)
        col_localita = next((c for c in df.columns if c.lower().strip() == 'località'), None)
        col_comune = next((c for c in df.columns if c.lower().strip() == 'comune'), None)

        if not col_localita or not col_comune:
            print("Errore: Non trovo le colonne 'località' o 'comune' nell'Excel.")
            return

        localita = str(row[col_localita]).strip() if pd.notna(row[col_localita]) else ""
        comune_attuale = str(row[col_comune]).strip() if pd.notna(row[col_comune]) else ""

        # Se il comune è vuoto e abbiamo una località, proviamo a cercarlo
        if (comune_attuale == "" or comune_attuale.lower() == "nan") and localita != "":
            try:
                print(f"Riga {index+1}: Cerco comune per '{localita}'...", end=" ", flush=True)

                # Cerchiamo la località specificando "Italy" per precisione
                location = geolocator.geocode(f"{localita}, Italy", addressdetails=True, language="it")

                if location and 'address' in location.raw:
                    address = location.raw['address']
                    # Estraiamo il comune dai vari campi possibili di OpenStreetMap
                    nuovo_comune = (address.get('city') or
                                    address.get('town') or
                                    address.get('village') or
                                    address.get('municipality') or
                                    address.get('hamlet'))

                    if nuovo_comune:
                        df.at[index, col_comune] = nuovo_comune
                        print(f"✅ Trovato: {nuovo_comune}")
                    else:
                        print("❓ Località trovata ma comune non identificato.")
                else:
                    print("❌ Non trovato.")
            except Exception as e:
                print(f"⚠️ Errore durante la ricerca: {e}")
                time.sleep(2) # Pausa di sicurezza in caso di errore di rete

    # Salvataggio finale
    print(f"\nSalvataggio file in corso: {NOME_FILE_OUTPUT}...")
    df.to_excel(NOME_FILE_OUTPUT, index=False)
    print("Operazione completata con successo!")

if __name__ == "__main__":
    completa_comuni()
