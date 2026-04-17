import os
import base64
import json
import urllib.request

# === CONFIGURAZIONE ===
TOKEN = "INCOLLA_QUI_IL_TUO_TOKEN"
OWNER = "IL_TUO_NOME_UTENTE_GITHUB"
REPO = "BatMaps"
# ======================

def upload_file(file_path, github_path):
    try:
        with open(file_path, "rb") as f:
            content = base64.b64encode(f.read()).decode("utf-8")

        url = f"https://api.github.com/repos/{OWNER}/{REPO}/contents/{github_path}"

        # Controlla se il file esiste già per ottenere lo SHA
        sha = None
        try:
            req = urllib.request.Request(url, headers={"Authorization": f"token {TOKEN}"})
            with urllib.request.urlopen(req) as response:
                sha = json.loads(response.read().decode())["sha"]
        except: pass

        payload = {"message": f"Update {github_path}", "content": content}
        if sha: payload["sha"] = sha

        req = urllib.request.Request(url, data=json.dumps(payload).encode(), method="PUT",
                                    headers={"Authorization": f"token {TOKEN}", "Content-Type": "application/json"})
        with urllib.request.urlopen(req) as response:
            print(f"✅ Caricato: {github_path}")
    except Exception as e:
        print(f"❌ Errore su {github_path}: {e}")

# Cartelle da caricare
target_folders = ["web", "app/src/main/assets"]

print(f"🚀 Inizio upload su GitHub ({OWNER}/{REPO})...")
for folder in target_folders:
    for root, dirs, files in os.walk(folder):
        for name in files:
            full_path = os.path.join(root, name)
            # Carica i file di 'web' nella radice del sito per far funzionare la pagina
            github_path = os.path.relpath(full_path, folder) if folder == "web" else full_path
            upload_file(full_path, github_path)

print("\n🏁 Finito! Ora vai nelle Settings del tuo repo su GitHub > Pages e attiva il sito.")
