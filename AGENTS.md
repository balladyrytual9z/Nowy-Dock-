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

## Wydania i numeracja

- `versionCode` = numer runu CI, tag = `v1.0.<versionCode>`. Każdy push na `main`
  tworzy release z assetami: `CarPlay-Launcher-v1.0.<N>.apk` + `SHA256SUMS.txt`
  (konwencja nazw jest używana przez samoaktualizację — nie zmieniać).
- APK dla użytkownika trzymamy też poza repo: `/workspace/project/apk-artifact/<tag>/`
  (użytkownik wgrywa je na tablet; w repo nie ma binariów).

## Samoaktualizacja (`Updater.java`) — jak działa i dlaczego tak

- **Źródło wersji: kanał Atom** `releases.atom` (bez limitu zapytań). GitHub API
  (`api.github.com`) daje **60 zapytań/h na IP** — przy sprawdzaniu co minutę
  padłoby po godzinie (403), dlatego API jest tylko awaryjnym fallbackiem.
  Z taga składamy linki: `.../releases/download/<tag>/CarPlay-Launcher-<tag>.apk`
  i `.../SHA256SUMS.txt`.
- Odstęp sprawdzania: `Prefs.updateCheckMin` (domyślnie **1 min** = tryb testowy
  na życzenie użytkownika; w Ustawieniach: 1 min / 10 min / 1 h / 6 h).
  Pętla: `Updater.startPolling` (Runnable co 60 s, wołane z `LauncherActivity`).
- **Tło jest ciche**: żadnych toastów/powiadomień. Komunikaty tylko w `checkNow`
  (ręczne „Sprawdź aktualizacje teraz”).
- Instalacja: 1) root (`su -c pm install -r`) = jedyna droga bez klikania,
  2) `PackageInstaller` + obsługa `STATUS_PENDING_USER_ACTION` (sami otwieramy
  okno systemu), 3) `ACTION_VIEW` przez `ApkProvider` (jedno dotknięcie).
  Bez roota Android **nie pozwala** zwykłej aplikacji instalować po cichu.
- **PUŁAPKA:** `PackageInstaller.SessionParams.setRequiresUserAction()` **nie
  istnieje** w publicznym SDK (compileSdk 35 → błąd kompilacji). Nie ma też
  publicznego API „zainstaluj bez okna” — decyduje platforma/OEM.
- Suma SHA256 z `SHA256SUMS.txt` jest weryfikowana **przed** instalacją; przy
  bezobsługowej instalacji to jedyna bariera przed śmieciem.

## Mapy: postój vs. ruch (`HeadingSmoother`)

GPS na postoju „szumi” o kilka metrów, a kurs jest wtedy losowy. Fixy są ważone
**zaufaniem**: ≥ 10 km/h → 1; 3.6–10 km/h → proporcjonalnie (min. 0.25);
< 3.6 km/h → postój: dopóki fix jest bliżej niż `12 m` (albo niż dokładność GPS),
znacznik **nie rusza się wcale**, licznik pokazuje 0, kurs trzymany; dalej —
dochodzenie po 0.25 na fix (bez skoku). Fix z dokładnością > 100 m odrzucany.
Kurs brany z fixa dopiero od 10 km/h (przy 1.8–10 km/h to był szum i mapa
kręciła się na boki). Progi to stałe w `HeadingSmoother`.

## Wygląd kafelków na ekranie głównym

- Karta rysuje się sama: `Ui.ringCard(cv, rect, 30f*d, 1.5f*d, night, ring)`
  (promień 30 dp, obwódka 1.5 dp). Puste miejsce rysuje `HomeSections.Slot`.
- Gdy widget ma **dziecko** (np. `MapView` w `MapCard`), trzeba: 1) narysować
  wypełnienie karty **pod** dzieckiem, 2) przyciąć dzieci do kształtu karty
  (`clipPath`, kształt liczony raz na zmianę rozmiaru), 3) narysować obwódkę
  **na wierzchu** (`Ui.ringStroke` — bez tego mapa zasłania obwódkę).

## Weryfikacja bez JDK — co działa

- `tools/check_apk.py <apk>` — manifest (versionCode), liczba wpisów.
- Dex: szukanie **nazw klas/metod** (`Lpl/dock/widget/X;`, `nazwaMetody`)
  potwierdza, że kod jest w APK. **Uwaga:** stałe `static final int` z frameworka
  są **wstawiane w miejscu użycia** przy kompilacji, więc np. `LAYER_TYPE_SOFTWARE`
  **nie występuje** w dexie i nie da się tego tak sprawdzić — do takich rzeczy jest
  test-alarm czytający źródło (`ConnectivityPanelGuardTest`).
- XML → `xml.etree.ElementTree`, bilans nawiasów w Javie → licznik w Pythonie,
  suma APK → `sha256sum -c SHA256SUMS.txt`.
- Manifest binarny: łańcuchy szukać w UTF-8 **i** UTF-16LE.

## Warsztat agenta w tym sandboxie (pułapki narzędziowe)

- Katalog roboczy bywa **pusty** (nie ma checkoutu) → commity budujemy przez
  tymczasowy index: `GIT_INDEX_FILE=... git read-tree origin/main` +
  `git hash-object -w` + `git update-index --cacheinfo` → `git write-tree` →
  `git commit-tree` → push. Nie zostawia śmieci w drzewie roboczym.
- **Długie heredoki bywają zniekształcane** przy transporcie (kotwica przestaje
  pasować) → w skryptach edycyjnych używać krótkich markerów i cięcia po indeksach
  (`s.find(start)`, `s.find(end)`), sprawdzać `count(...) == 1`.
- `gh pr create --body "..."` psuje się, gdy w treści jest `"` → pisać treść do
  pliku i używać `--body-file`.
- Testy JVM w CI: `gradle --no-daemon test` (debug + release). Pisać testy na
  **deterministycznych danych** (tablice), nie na `Random` — mniej kaprysów.

## Historia decyzji (kontekst dla przyszłych zmian)

- **Organic Maps usunięte z projektu** (2026-11-05): kafelek „NAWIGACJA” i całe
  zaplecze (`NavCard`, `NavChromeView`, `NavData`, `NavOverlayService`,
  `OmAccessibilityService`, `res/xml/om_accessibility.xml`, wpisy w manifeście)
  skasowane. Mapa to **nasz** osmdroid: `MapCard`, `NavigatorActivity`,
  `MapDownloadActivity`, `MapConfig`, `MapState`, `SmoothLocationOverlay`.
  Nie ma usługi dostępności czytającej ekran innej aplikacji.
- Pusty panel łączności (Mali-T720/API 28) naprawiony przez
  `setLayerType(LAYER_TYPE_SOFTWARE, null)`; pilnuje tego
  `ConnectivityPanelTest`/`ConnectivityPanelGuardTest` — **nie usuwać** tej warstwy.
