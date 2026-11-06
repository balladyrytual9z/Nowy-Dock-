#!/bin/bash
# Skrypt konfiguracji środowiska po restarcie
echo "Rozpoczynam konfigurację Nowy-Dock..."
git clone https://github.com/balladyrytual9z/Nowy-Dock-.git /workspace/project/Nowy-Dock- 2>/dev/null || echo "Repo już istnieje."
cd /workspace/project/Nowy-Dock-
echo "Środowisko gotowe do pracy."