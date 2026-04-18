#!/usr/bin/env python3
import subprocess, sys
try:
    import bcrypt
except ImportError:
    subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'bcrypt', '-q'])
    import bcrypt

hash_val = b"$2a$10$7EqJtq98hPqEX7fNZaFWoOHi5uH8Q2K7S6x9Ejo4kKDAdAm8N1sC."
passwords = [b"manager123", b"password", b"Password123", b"admin", b"123456", b"Manager123", b"secret"]

for pwd in passwords:
    result = bcrypt.checkpw(pwd, hash_val)
    print(f"{pwd.decode()}: {result}")
    if result:
        print(f"\nFOUND: password is '{pwd.decode()}'")
        break
else:
    print("\nNone of the common passwords matched.")
    new_hash = bcrypt.hashpw(b"manager123", bcrypt.gensalt(10))
    print(f"New bcrypt hash for 'manager123': {new_hash.decode()}")
