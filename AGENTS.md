# AGENTS.md — pamięć repozytorium Nowy-Dock

Aplikacja Android (Java/Kotlin): CarPlay, OBD, Bluetooth.

## Środowisko pracy agenta

- **ZAKAZ lokalnego builda.** W sandboxie NIE ma JDK, Android SDK ani Gradle
  (`java`, `gradle`, `sdkmanager` nie istnieją) i nie wolno ich instalować (OOM).
- **Jedyna weryfikacja = GitHub Actions.** `./gradlew` w repo to jedynie stub
  (`exec gradle "$@"`), więc hook `.openhands/hooks/quality_gate.sh` nie ma szans
  przejść lokalnie — to oczekiwane, nie jest objawem błędu w kodzie.
  Zamiast tego: push gałęzi → PR → `gh run watch`.

## CI

- Workflow: **`.github/workflows/build-release.yml`** — to jedyny plik workflow
  w repo. **Nie ma** pliku `build-apk.yml` (częsta pomyłka).
- Wyzwalacze: `push` do `main` (tworzy release), `pull_request` do `main`,
  `workflow_dispatch`.
- Stack w CI: Java 17 (temurin), Android SDK `platforms;android-35`,
  `build-tools;35.0.0`, Gradle 8.9 przez `gradle/actions/setup-gradle@v4`.
  Build odpala się komendą `gradle --no-daemon` (NIE przez wrapper).
- Cache Gradle:
  - `cache: 'gradle'` w `actions/setup-java@v4`,
  - `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` w `setup-gradle`
    → PR-y tylko czytają cache, `main` zapisuje.
  - Uwaga: oba mechanizmy cache'ują `~/.gradle/caches` i częściowo się dublują
    (setup-java zapisuje ~267 MB). Cache `setup-gradle` jest granularny i to on
    jest głównym źródłem zależności.
- Krok „Ensure stable signing keystore” może **zacommitować i wypchnąć na `main`**
  (`git push origin HEAD:main`), jeśli brakuje `app/dock-debug.p12`. Keystore jest
  celowo trzymany w repo (stabilny podpis = aktualizacja APK bez odinstalowania).
- Artefakt: `CarPlay-Launcher-v1.0.<run_number>.apk` w `$RUNNER_TEMP/release-assets`.

## Zasady pracy z tym repo

- Nigdy nie commituj/pushuj bezpośrednio na `main` — nowa gałąź + Pull Request.
- Najpierw plan (jakie pliki), potem pytania `[tak/nie]`, potem kod.
- `AGENTS.md` nie jest częścią źródeł aplikacji — nie wpływa na build APK.
