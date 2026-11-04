#!/bin/bash

# Przejdź do głównego katalogu projektu
cd "${OPENHANDS_PROJECT_DIR:-$PWD}"

# Wybierz odpowiednie polecenie Gradle (zależnie od tego, co jest w repo)
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

echo "=== URUCHAMIANIE TESTÓW JEDNOSTKOWYCH ==="
if ! $GRADLE_CMD test; then
    echo '{"decision": "deny", "reason": "Testy jednostkowe nie przeszły. Przeanalizuj błędy w logach powyżej i popraw kod."}'
    exit 2
fi

echo "=== TESTY PRZESZŁY. BUDOWANIE APK ==="
if ! $GRADLE_CMD assembleDebug; then
    echo '{"decision": "deny", "reason": "Kompilacja APK nie powiodła się. Sprawdź błędy kompilacji i napraw kod."}'
    exit 2
fi

echo "=== WSZYSTKO DZIAŁA. MOŻESZ ZAKOŃCZYĆ ==="
exit 0
