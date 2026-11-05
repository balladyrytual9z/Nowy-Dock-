# AGENTS.md — pamięć projektu Nowy-Dock

Plik trwałej wiedzy o repozytorium. Czytaj przed każdym zadaniem.
Aktualizuj, gdy odkryjesz coś, co oszczędzi czas następnej sesji.

## 1. Co to jest

Launcher Android (`pl.dock.widget`) dla tabletu samochodowego Honda,
zainstalowany na head unicie **ESSGOO 9"** (SoC MT8163). Cechy: dock
z ikonami, panel łączności, OBD przez SPP/BLE (ELM327), nakładka
CarPlay nad Organic Maps.

> ### 🔴 TWARDY WYMÓG: ma działać na **Androidzie 9 = API 28**
>
> Urządzenie: **ESSGOO 9"**, **32-bitowe ARMv7**, **bez roota**.
> Pełna specyfikacja: [`docs/urzadzenie-essgoo-9.md`](docs/urzadzenie-essgoo-9.md).
> Każda zmiana musi to zachować. **NIE podnosić `minSdk` powyżej 28 ani
> `targetSdk` powyżej 28.** Pułapki i checklista → sekcja 2a.

- `namespace` / `applicationId`: `pl.dock.widget`
- `minSdk 26`, `targetSdk 28` (celowo stary — zachowanie nakładek),
  `compileSdk 35`
- `versionCode` z CI: `-Pvc=<run_number>` → instalacja APK to UPDATE,
  nie reinstall (stabilny keystore `app/dock-debug.p12`)
- UI celowo na czystym Android frameworku, **bez AndroidX dla widoków**
  (choć `android.useAndroidX=true` jest ustawione)

## 2. Budowanie i CI — JEDYNE ŹRÓDŁO PRAWDY

Lokalnie **nie ma** JDK, Android SDK ani Gradle i nie wolno ich
instalować (sandbox Railway = OOM). Kompilujemy **wyłącznie** przez
GitHub Actions.

`.github/workflows/build-release.yml` (push do `main` = wydanie):

| Element | Wartość |
| --- | --- |
| Runner | `ubuntu-latest`, timeout 30 min |
| JDK | **temurin 17** |
| Gradle | **8.9** (przez `gradle/actions/setup-gradle`) |
| Android SDK | instalowane **tylko**: `platforms;android-35`, `build-tools;35.0.0` |
| AGP | **8.5.2**, Kotlin **1.9.24** |
| Kroki | `gradle test` → `gradle clean assembleDebug -Pvc=<run>` |

> **Skutek:** `compileSdk 36` ani `37` **nie zadziała** bez równoczesnej
> zmiany workflow (instalacja nowej platformy) i podbicia AGP.
> AGP 8.5.2 obsługuje maks. API 35.

Root `build.gradle` trzyma wersje wtyczek; `settings.gradle` ma
`google()` + `mavenCentral()` (kolejność: `google()` pierwsze).

`.openhands/hooks/quality_gate.sh` uruchamia `test` + `assembleDebug`
przy zatrzymaniu agenta — lokalnie padnie, bo brak Gradle. Nie jest to
błąd kodu.

### Weryfikacja gotowego APK bez Android SDK

Skoro lokalnie nie ma aapt/apkanalyzer, służy do tego
`tools/check_apk.py` (tylko biblioteka standardowa Pythona). Czyta
**binarny** `AndroidManifest.xml` i listę `.so` wprost z APK:

```
python3 tools/check_apk.py <plik.apk>
```

Sprawdzone tym narzędziem na APK z CI (v1.0.177): `minSdk 26`,
`targetSdk 28`, `compileSdk 35`, `extractNativeLibs=false`, zero
bibliotek natywnych. W **Fazie 1 tym samym skryptem potwierdzać, że APK
zawiera `lib/armeabi-v7a/`** — to warunek działania na tym tablecie.

## 2a. Android 9 (API 28) — potwierdzony sprzęt i konsekwencje

Urządzenie: **ESSGOO 9" head unit** (Honda). Pełna specyfikacja:
[`docs/urzadzenie-essgoo-9.md`](docs/urzadzenie-essgoo-9.md).

| Fakt | Wartość |
| --- | --- |
| System | Android 9 Pie, API 28, build `PPR1.180610.011` |
| Board | `Essgoop3_bsp` (alps), kernel `4.9.117+ armv7l` |
| **ABI** | **`armeabi-v7a`, 32-bit (ARMv7)** — tylko to działa |
| CPU / GPU | MT8163, 4× Cortex-A53 · Mali-T720, **OpenGL ES 3.2** |
| RAM / heap | 4 GB fizycznie, ale **heap Javy 128 MB** |
| Ekran | 9", 1280×720, IPS |
| Czujniki | **tylko akcelerometr** |
| Root | **BRAK** |
| Play Services | obecne (Play Store działa) |

### 🔴 Konsekwencje — to nie ciekawostki, to wytyczne projektowe

- **ABI: TYLKO `armeabi-v7a`.** Urządzenie jest 32-bitowe (`armv7l`,
  ABI `armeabi-v7a`/`armeabi`). Biblioteki natywne **muszą** być dla
  ARMv7. Lokalne buildy kontrolne SDK robi się z `-Parm64`, co daje AAR
  **bez** ARMv7, czyli **martwą apkę na tym tablecie**. Dlatego
  `abiFilters` = `armeabi-v7a` i trzeba potwierdzić, że AAR SDK zawiera
  `lib/armeabi-v7a/`.
- **Wersja Androida jest SFALSZOWANA.** System wyświetla „Android 15.0",
  a naprawdę jest Android 9 / API 28. **Nie wierzyć
  `Build.VERSION.RELEASE`** i nie opierać na nim żadnej logiki.
- **Brak czujników kierunku** — nie ma magnetometru ani żyroskopu, jest
  tylko akcelerometr. Obrotu mapy „wg kursu" **nie da się** oprzeć na
  kompasie; jedyne źródło to kurs GPS nad ziemią (działa w ruchu, nie
  działa w miejscu). Nie planować funkcji zależnych od kompasu.
- **Brak roota** → odpada iniekcja zdarzeń dotyku i sztuczki przez
  `injectInputEvent`. Dlatego wbudowanie przez SDK to jedyna sensowna
  droga (nie ma jak sterować obcą aplikacją w oknie).
- **Vulkan odpada.** Mali-T720 go nie ma, ale jest **OpenGL ES 3.2** —
  renderer OM musi iść ścieżką GLES. Nie włączać
  `enableVulkanDiagnostics`.
- **Brak czujnika światła** → auto tryb ciemny/jasny tylko przez
  wschód/zachód słońca (algorytm NOAA), nie przez otoczenie.
- **NIE używać API 29+.** Ani `ACCESS_BACKGROUND_LOCATION`, ani
  `android:foregroundServiceType` (oba API 29) tu nie istnieją.
  Nawigacja w tle = własny foreground service + notyfikacja
  (README SDK: to obowiązek hosta).
- **GPS jest sprzętowy, Play Services są** → **nie dodawać**
  `location-google` ani `location-foss`. Wystarczy wbudowany provider
  Androida. Mniej zależności, mniejszy APK.
- **Cleartext HTTP** blokowany dla `targetSdk >= 28`. osmdroid i OM
  używają HTTPS — nie otwierać `usesCleartextTraffic`.
- **Pamięć.** Heap 128 MB — kafle i tekstury OM renderuje natywnie; nie
  alokować ich w Javie. Mali-T720 → minimalne animacje.
- **Zasilanie.** Stałe 12 V + osobny ACC. Po 12 h bez ACC pełne
  wyłączenie, a zimny boot trwa **15–30 s** — nie zakładać natychmiastowej
  gotowości po zapłonie.
- **Zlink** (`com.zjinnova.zlink`, CarPlay, targetSdk 21) wyłącza
  Bluetooth przy starcie → potrzebny BT Recovery (już jest w projekcie).

### Sprawdzone — jest OK

- SDK OM ma `minSdk = 21` (`propMinSdkVersion`), więc API 28 jest w zakresie
- `FOREGROUND_SERVICE` **jest** zadeklarowany (wymagany od Android 9)
- `SYSTEM_ALERT_WINDOW` (nakładki) — jest
- Java 17 + desugaring nie zależą od wersji Androida: D8 desugaruje
  bajtkod do DEX pod `minSdk 26`
- `compileSdk` jest **tylko kompilacyjny** i nie wpływa na API 28

## 3. Stos mapowy (stan obecny)

Silnik kafli: **osmdroid** `org.osmdroid:osmdroid-android:6.1.18`,
pakiet `pl.dock.widget.maps`.

- Źródła kafli: `TileSourceFactory.MAPNIK` (tile.openstreetmap.org)
  i `TileSourceFactory.OpenTopo` (opentopomap.org) — wybór w `Prefs.mapStyle()`
- `MapConfig.init()` — jednorazowa konfiguracja, cache w
  `filesDir/osmdroid/tiles`
- `BulkGuard` — **krytyczne**: Mapnik ma flagę `FLAG_NO_BULK`, a
  `CacheManager` rzuca `TileSourcePolicyException` dopiero w wątku tła
  (nie da się złapać) i zabija proces. Dlatego sprawdzamy
  `acceptsBulkDownload()` **przed** startem pobierania.
- `OmAccessibilityService` — czyta manewry/ETA/limit prędkości
  z ekranu Organic Maps (pakiet `app.organicmaps`). To obejście.
- `NavOverlayService` — dwa okna `SYSTEM_ALERT_WINDOW`: nieklikalny
  chrome (mapa dostaje dotknięcia) + klikalny dock boczny.

### ⚠️ osmdroid jest ZARCHIWIZOWANE

`github.com/osmdroid/osmdroid` — Apache-2.0, 3 tys. gwiazdek, ale
**„archived, will no longer receive updates or new releases"**
(ostatni push 2024-11-20, ostatnie wydanie **6.1.20** z 2024-08-18;
planowane 6.1.21 nigdy nie wyszło). Zero poprawek bezpieczeństwa.

## 4. Cel: Organic Maps SDK (zamiast przepisywania OM)

**Nie da się „przepisać" Organic Maps**: 17,2 mln linii C++, repo
10,1 GB. **Nie trzeba** — istnieje oficjalne SDK.

- Repo: `github.com/organicmaps/organicmaps`, moduł `android/sdk`
- Przykłady: `github.com/organicmaps/sdk-android` (warianty `basic`,
  `offline`, `routing`)
- Grupa Maven: `app.organicmaps.sdk` — artefakty `sdk`, `maps-world`,
  `location-core`, `location-google`/`location-foss` (wybierz **jeden**),
  `car`, `widgets-lanes`, `widgets-speedlimit`
- Licencje: Apache-2.0 + **`DATA_LICENSE.txt`** (dane binarne)
- SDK daje **silnik**: renderer, offline search, pobieranie regionów,
  bookmarks, **routing**. **Nie daje UI** — ekrany piszemy sami.

### 🔴 Blokada: SDK nie jest jeszcze opublikowane

Stan na 2026-11-04 — zweryfikowane:
- `repo1.maven.org/maven2/app/organicmaps/` → **404**; wyszukiwarka
  Central → `numFound: 0`
- wersja w przykładach: `organicmaps-sdk = "0.0.0-sdk-check"` (build lokalny)
- jedyny tag OM z „sdk" to `sdk-test-android` (test, nie wydanie)
- repo `sdk-android` utworzone **2026-10-28**; wydanie jest **manualne**
  (`.github/workflows/android-sdk.yaml`, `workflow_dispatch` →
  `publishAggregationToCentralPortal`)

**Zanim wydanie się pojawi**, jedyne drogi to: czekać, albo zbudować SDK
ze źródeł (`git submodule update --init --recursive`, CMake, **NDK 29**,
`./gradlew -Parm64 publishSdkForChecks`) — w tym sandboxie niemożliwe.

### Wymagania SDK dla hosta

| Wymóg | Powód / stan |
| --- | --- |
| Java **17** source+target | natywny rdzeń, zgodność AAR |
| `coreLibraryDesugaringEnabled true` + `com.android.tools:desugar_jdk_libs:2.1.5` | artefakt w **repozytorium Google**, nie na Central |
| `compileSdk` = `propCompileSdkVersion` = **37** | wymusza `aarMetadata.minCompileSdk` |
| `androidResources.noCompress += ["png","mwm","ttf","packed_polygons.bin"]` | natywny silnik czyta mapy **losowym dostępem**; spakowane = crash |
| AndroidX `lifecycle`/`recyclerview`/`fragment` (jako `api`) | wchodzą tranzytywnie |
| minSdk 21 | masz 26 ✔ — **API 28 (Android 9) jest w zakresie** |
| **ABI `armeabi-v7a` w AAR** | tablet jest 32-bit (`armv7l`) — AAR bez ARMv7 = martwa apka |
| NIE dodawać `location-google`/`location-foss` | GPS jest sprzętowy; wbudowany provider wystarcza |
| NDK 29 / JDK 21 | tylko przy budowaniu SDK ze źródeł |

Kontrakt integracji (z README SDK — trzymaj się go):
- `OrganicMaps.init(callback)` na wątku głównym; gotowość trzymaj
  **w `Application`**, nie w Activity (obsłuży obrót ekranu)
- `MapController` **przed** dodaniem widoku do okna; `getLifecycle().addObserver(controller)`
- `MapStyle.mark()` przed utworzeniem renderera, `MapStyle.set()` przy zmianie
- uprawnienia do lokalizacji: **host musi poprosić runtime** (manifest
  SDK tylko się scala)
- `SearchEngine.searchMaps()` → region ID → `MapManager.startDownload()`;
  subskrybuj **przed** pobraniem, zwalniaj slot
- `RoutingController.Container` + `restore()`; `showRoutePlan(show, completion)`
  **musi** zawołać `completion`
- **Zachowaj widoczną atrybucję mapy** i dołącz licencje kodu + danych

## 5. Plan (Fazy)

- **Faza 0 (ten PR)** — przygotowanie pod SDK: Java 17, desugaring,
  `noCompress`. Bez dodawania zależności SDK (jeszcze jej nie ma).
- **Faza 1** — podbić AGP + `compileSdk 37` + doinstalować
  `platforms;android-37` w CI, dodać `app.organicmaps.sdk:sdk` i
  `:maps-world` (**bez** `location-google`/`location-foss`).
  Warunek wstępny: **potwierdzić, że AAR SDK zawiera
  `lib/armeabi-v7a/`** — bez tego apka nie wstanie na tym tablecie.
  `minSdk`/`targetSdk` **bez zmian** (26/28).
- **Faza 2** — `OrganicMaps.init()` + `MapView`/`MapController` w launcherze.
- **Faza 3** — offline search, pobieranie regionów, routing.
- **Faza 4** — `NavData`/`NavOverlayService` zasilane **z SDK**;
  `OmAccessibilityService` do usunięcia.
- **Faza 5** — osmdroid out (jeden silnik = mniej RAM, mniejszy APK,
  jedna pamięć kafli), `LICENSE` + `DATA_LICENSE.txt` + atrybucja
  „© OpenStreetMap contributors".

Decyzja architektoniczna: **jeden silnik map docelowo** (OM SDK), nie dwa.

## 6. Konwencje

- Komentarze i komunikaty w kodzie: **polski**, bez polskich znaków
  w komentarzach (ASCII) — tak trzyma całe repo
- Gałęzie: `feature/...`, `fix/...`; **nigdy** bezpośrednio do `main`
- Testy JVM w `app/src/test/java/pl/dock/widget/`
  (`returnDefaultValues = true` — stuby Android API)
- Nie dodawaj zależności bez sprawdzenia, że są w `google()` lub
  `mavenCentral()` — CI padnie na rozwiązywaniu zależności
