import socket
import threading
import os

UPLOAD_DIR = "storage"

def get_remote_path(conn_file):
    try:
        remote_path = conn_file.readline().decode('utf-8').strip()
        if not remote_path:
            raise ValueError("Ruta remota vacía")
        abs_path = os.path.join(UPLOAD_DIR, remote_path.strip("/"))
        os.makedirs(os.path.dirname(abs_path), exist_ok=True)
        return abs_path
    except UnicodeDecodeError:
        raise ValueError("Ruta mal codificada")

def handle_existing_file(conn, conn_file, abs_path):
    conn.sendall(b"EXISTS\n")
    try:
        decision = conn_file.readline().decode('utf-8').strip()
    except UnicodeDecodeError:
        conn.sendall("Error de codificación al leer la decisión.\n".encode('utf-8'))
        return None

    print(f"[DEBUG] Decisión recibida: {repr(decision)}")

    if decision == "OVERWRITE":
        conn.sendall(b"OK\n")
        return abs_path
    elif decision.startswith("RENAME:"):
        new_name = decision.split(":", 1)[1].strip()
        if not new_name:
            conn.sendall("Nombre nuevo inválido.\n".encode('utf-8'))
            return None
        new_path = os.path.join(os.path.dirname(abs_path), new_name)
        conn.sendall(b"OK\n")
        return new_path
    elif decision == "CANCEL":
        conn.sendall("Cancelado por el cliente.\n".encode('utf-8'))
        return None
    else:
        conn.sendall("Decisión inválida del cliente.\n".encode('utf-8'))
        return None

def receive_file(conn_file, abs_path):
    size_bytes = conn_file.read(8)
    if len(size_bytes) != 8:
        raise ValueError("No se pudo leer el tamaño del archivo")
    file_size = int.from_bytes(size_bytes, byteorder='big')

    with open(abs_path, "wb") as f:
        remaining = file_size
        while remaining > 0:
            chunk = conn_file.read(min(4096, remaining))
            if not chunk:
                raise ConnectionError("Archivo incompleto o conexión interrumpida")
            f.write(chunk)
            remaining -= len(chunk)

def handle_client(conn, addr):
    print(f"[+] Conectado: {addr}")
    try:
        conn_file = conn.makefile("rb")
        abs_path = get_remote_path(conn_file)

        if os.path.exists(abs_path):
            abs_path = handle_existing_file(conn, conn_file, abs_path)
            if not abs_path:
                return
        else:
            conn.sendall(b"OK\n")

        receive_file(conn_file, abs_path)
        print(f"[✔] Archivo guardado en: {abs_path}")
        conn.sendall("Archivo recibido correctamente.\n".encode('utf-8'))

    except (ValueError, OSError) as e:
        print("[ERROR]", str(e))
        try:
            conn.sendall(f"Error: {str(e)}\n".encode('utf-8'))
        except OSError:
            pass
    finally:
        conn.close()

def start_server():
    os.makedirs(UPLOAD_DIR, exist_ok=True)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind(('161.132.51.124', 5000))
    server.listen(5)
    print("Servidor escuchando en puerto 5000...")
    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()

if __name__ == "__main__":
    try:
        start_server()
    except KeyboardInterrupt:
        print("\n[!] Servidor detenido por el usuario.")
