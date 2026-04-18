#!/usr/bin/env python3
import subprocess, sys
try:
    import bcrypt
except ImportError:
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'bcrypt', '-q'])
    import bcrypt

new_hash = bcrypt.hashpw(b"manager123", bcrypt.gensalt(10)).decode()
new_hash_2a = new_hash.replace("$2b$", "$2a$")
print(f"Hash: {new_hash_2a}")

subprocess.run([
    "docker", "exec", "retailhub-postgres-1", "psql", "-U", "retailhub", "-d", "auth_db",
    "-c", f"UPDATE credentials SET password_hash = '{new_hash_2a}' WHERE phone_number = '+70001111111';"
])

subprocess.run([
    "docker", "exec", "retailhub-postgres-1", "psql", "-U", "retailhub", "-d", "user_db",
    "-c", f"UPDATE users SET password_hash = '{new_hash_2a}' WHERE phone_number = '+70001111111';"
])

print("Password updated to 'manager123'")
