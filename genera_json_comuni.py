import json
import os

def genera():
    # Dizionario capoluoghi per coordinate di base
    coords_base = {
        'PD': (45.4064, 11.8768),
        'VI': (45.5479, 11.5446),
        'VE': (45.4408, 12.3155),
        'VR': (45.4384, 10.9916),
        'TV': (45.6669, 12.2431),
        'BL': (46.1425, 12.2167),
        'RO': (45.0711, 11.7907),
        'MO': (44.6471, 10.9252),
        'MI': (45.4642, 9.1900),
        'RM': (41.9028, 12.4964)
    }

    input_path = 'comuni_db_extracted/FREE/italy_cities.json'
    output_path = 'app/src/main/assets/comuni_italiani.json'

    if not os.path.exists(input_path):
        print(f"Errore: {input_path} non trovato")
        return

    with open(input_path, encoding='utf-8') as f:
        cities = json.load(f)['Foglio1']

    data = {}
    for c in cities:
        nome = str(c['comune']).lower().strip()
        prov = str(c['provincia']).upper().strip()

        # Default coords based on province capoluogo
        lat, lon = coords_base.get(prov, (45.5479, 11.5446)) # Fallback Vicenza

        # Se abbiamo match esatto per il capoluogo, usiamo le sue coordinate
        if nome in ['vicenza', 'padova', 'verona', 'venezia', 'treviso', 'belluno', 'rovigo', 'modena', 'milano', 'roma']:
            lat, lon = coords_base[prov]

        data[nome] = {
            'prov': prov,
            'res': c.get('num_residenti', 0),
            'sup': c.get('superficie', 0),
            'lat': lat,
            'lon': lon
        }

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"✅ Generato {output_path} con {len(data)} comuni.")

if __name__ == "__main__":
    genera()
