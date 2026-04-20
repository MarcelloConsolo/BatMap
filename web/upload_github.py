import os
import subprocess

def find_git():
    # Percorsi comuni di Git su Windows
    paths = [
        r"C:\Program Files\Git\bin\git.exe",
        r"C:\Program Files (x86)\Git\bin\git.exe",
        os.path.expanduser(r"~\AppData\Local\Programs\Git\bin\git.exe")
    ]
    for path in paths:
        if os.path.exists(path):
            return path
    return "git" # Fallback

def upload_to_github():
    git_path = find_git()
    print(f"--- USANDO GIT DA: {git_path} ---")

    # Ci spostiamo nella root del progetto (una cartella sopra 'web')
    # così il comando 'git add .' prende tutto, incluso index.html
    os.chdir(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    print(f"--- LAVORO NELLA DIRECTORY: {os.getcwd()} ---")

    try:
        # Sincronizza prima di caricare per evitare errori di "rejected"
        print("Sincronizzazione (pull)...")
        subprocess.run([git_path, "pull", "origin", "main", "--rebase"], check=False)

        # Aggiunge tutto
        print("Aggiunta file...")
        subprocess.run([git_path, "add", "."], check=True)

        # Commit
        print("Commit...")
        subprocess.run([git_path, "commit", "-m", "Aggiornamento automatico BatMap"], check=False)

        # Push esplicito su origin main
        print("Caricamento (push)...")
        subprocess.run([git_path, "push", "origin", "main"], check=True)

        print("\n✅ UPLOAD COMPLETATO CON SUCCESSO!")

    except Exception as e:
        print(f"\n❌ ERRORE: {e}")

if __name__ == "__main__":
    upload_to_github()
