import http.server
import socketserver
import socket

PORT = 8000

# Trova l'indirizzo IP locale del tuo computer
def get_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # non ha bisogno di connettersi davvero
        s.connect(('8.8.8.8', 1))
        IP = s.getsockname()[0]
    except Exception:
        IP = '127.0.0.1'
    finally:
        s.close()
    return IP

Handler = http.server.SimpleHTTPRequestHandler
my_ip = get_ip()

print("\n" + "="*50)
print(f"🚀 SERVER BATMAP AVVIATO!")
print(f"Lo puoi vedere sul tuo PC qui: http://localhost:{PORT}")
print(f"Lo puoi vedere dagli altri dispositivi qui: http://{my_ip}:{PORT}")
print("="*50)
print("Premi CTRL+C per fermare il server.\n")

with socketserver.TCPServer(("", PORT), Handler) as httpd:
    httpd.serve_forever()
