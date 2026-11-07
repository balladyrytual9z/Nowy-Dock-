# AGENTS.md — pamięć repozytorium Nowy-Dock

## 🛠️ Wymagane umiejętności (Skills)

Agent powinien korzystać z następujących umiejętności. Jeśli któraś nie jest jeszcze zainstalowana, należy ją dodać za pomocą komendy `/add-skill` z adresem URL wskazanym w nawiasie.

### Podstawowe
*   `agent-canvas-environment` – poprawne działanie w środowisku Agent Canvas.
*   `agent-memory` – zapamiętywanie kontekstu projektu.
*   `add-skill` – dynamiczne dodawanie nowych umiejętności.

### GitHub
*   `github` – podstawowa obsługa repozytorium.
*   `github-pr-reviewer` – recenzowanie Pull Requestów.
*   `github-issue-to-pr` – zamiana Issues na PR-y.
*   `release-notes` – generowanie notatek o wydaniu.

### Jakość kodu
*   `code-review` – przegląd kodu.
*   `qa-changes` – weryfikacja zmian.
*   `add-javadoc` – dokumentacja JavaDoc.
*   `security` – audyt bezpieczeństwa.

### UI i dokumentacja
*   `frontend-design` – projektowanie interfejsu.
*   `prd` – dokument wymagań produktowych.

**Uwaga:** Przed instalacją jakiejkolwiek umiejętności sprawdź, czy nie jest już aktywna w panelu Customize → Skills. Unikaj duplikatów.

Aplikacja Android (Java/Kotlin): CarPlay, OBD, Bluetooth.

# 🧠 Pamięć Projektu "Nowy-Dock"

## Kontekst techniczny
- Aplikacja Android napisana w Java/Kotlin.
- Obsługa CarPlay, OBD, Bluetooth BLE/SPP.
- Budowanie APK odbywa się wyłącznie przez GitHub Actions (NIE instaluj Androida lokalnie).

## Zasady pracy
- ZAWSZE używaj języka polskiego w komunikacji z użytkownikiem.
- ZAWSZE najpierw przedstaw plan i zadawaj pytania (tak/nie), zanim napiszesz kod.
- Zmiany w kodzie pushuj bezpośrednio na `main`, bez branchy i Pull Requestów (reguła zmieniona 2026-11-06 na życzenie użytkownika).
- Nie instaluj ciężkich narzędzi (Docker, pełne SDK), pracujemy na lekkim środowisku.

## Komunikacja z użytkownikiem (na stałe)
- Jeśli coś jest niejasne — NAJPIERW zapytaj, nie zgaduj.
- Bądź ciekawy, o co chodzi użytkownikowi i dopytuj, gdy brakuje kontekstu.
- Pytania formułuj maksymalnie prosto — odpowiedź to zawsze TAK/NIE.
- Wszystkie pytania dawaj NA SAMYM KOŃCU wiadomości, w jednym zdaniu, żeby wyświetlały Ci się jako jeden łatwy do przeczytania kawałek tekstu.

## Struktura projektu
- Główny kod: `app/src/main/java/pl/dock/`
- Zasoby UI: `app/src/main/res/`

## Zarządzanie pamięcią agenta

Od teraz, Twoim głównym plikiem pamięci projektu jest plik `AGENTS.md` w głównym katalogu repozytorium.

Zasady zarządzania pamięcią:
1. Za każdym razem, gdy ustalimy nową zasadę, podejmiesz ważną decyzję architektoniczną lub dowiesz się czegoś kluczowego o projekcie "Nowy-Dock", MUSISZ natychmiast dopisać tę informację do pliku `AGENTS.md`.
2. Po zaktualizowaniu pliku `AGENTS.md`, MUSISZ zrobić commit i wypchnąć zmiany bezpośrednio na gałąź `main` (użyj `git add AGENTS.md`, `git commit -m "docs: aktualizacja pamięci agenta"`, `git push origin main`). Nie rób z tego Pull Requesta, to ma być natychmiastowa aktualizacja pamięci.
3. Za każdym razem, gdy zaczynasz nowe zadanie, NAJPIERW przeczytaj plik `AGENTS.md`, aby przypomnieć sobie kontekst projektu.

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

- Wszystkie zmiany (kod oraz `AGENTS.md`) są commitujowane i pushowane
  bezpośrednio na `main`, bez gałęzi i Pull Requestów — reguła zmieniona
  2026-11-06 na życzenie użytkownika (wcześniej: „nowa gałąź + PR”, z wyjątkiem
  aktualizacji `AGENTS.md`).
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

## Reguły rozmowy z użytkownikiem

Zanim agent cokolwiek zrobi — **musi być pewien, że dobrze zrozumiał**.

- Jeśli polecenie użytkownika jest niejasne, nieprecyzyjne, brakuje szczegółów technicznych albo agent ma więcej niż jedną interpretację — **agent MA OBOWIĄZEK dopytać**, zanim ruszy dalej.
- Użytkownik mówi potocznie, po polsku, często nietechnicznie — to normalne. Agent nie zakłada, że wie o co chodzi. Dopytuje dopóki nie ma 100% pewności.
- Agent nie zgaduje intencji. Nie domyśla się. Nie „robi coś, bo tak zwykle się robi".
- Agent parafrazuje własnymi słowami to co zrozumiał i pyta „czy dobrze rozumiem?" — dopiero po potwierdzeniu przystępuje do działania.
- Lepiej zadać 2-3 krótkie pytania niż zrobić coś źle.

**Wyjątek:** jeśli użytkownik mówi wprost „zrób to" lub „nie pytaj, po prostu zrób" — agent robi bez dopytywania.

## Automatyczne budowanie APK — proces decyzyjny

Agent **nie pyta użytkownika** „czy zbudować APK?" — robi to automatycznie, bo to już jest ustalone i potwierdzone.

### Kiedy agent buduje APK

Po KAŻDEJ zmianie w kodzie źródłowym (Java, Kotlin, XML, Gradle, AndroidManifest):
1. **Automatycznie** tworzy nową gałąź (jeśli nie istnieje) + commit + push
2. **Automatycznie** otwiera Pull Request (jeśli nie istnieje) → to uruchamia CI
3. **Automatycznie** czeka na wynik CI: `gh run watch` (sprawdza co 30 s)
4. Gdy CI zakończy się sukcesem → raportuje użytkownikowi link do artefaktu APK
5. Gdy CI padnie → analizuje log, próbuje naprawić błąd i pushuje poprawkę

### Kiedy NIE budować

- Zmiany w `AGENTS.md`, `README.md`, `*.txt`, `*.jpeg` — nie wymagają APK
- CI jest już uruchomione dla tego samego commita (unikać duplikatów)

### Zasady

- APK produkuje się wyłącznie przez CI (lokalnie brak narzędzi)
- Testy (`gradle --no-daemon test`) muszą przejść, inaczej agent poprawia kod
- Agent nie pushuje na `main` — zawsze przez PR. **Wyjątek:** użytkownik wyraźnie każe pushnąć na main.
- Po udanym CI → agent raportuje: gałąź + link do PR + nazwa artefaktu APK
- Użytkownik może w każdej chwili powiedzieć „daj APK" → to samo co wyżej

### Uwierzytelnianie do GitHuba

Agent pushuje przez HTTPS. W sandboxie nie ma zapisanych danych logowania,
dlatego:

- Do pushowania agent używa `$GITHUB_PERSONAL_ACCESS_TOKEN`:
  ```bash
  git remote set-url origin "https://${GITHUB_PERSONAL_ACCESS_TOKEN}@github.com/balladyrytual9z/Nowy-Dock-.git"
  git push origin <branch>
  git remote set-url origin "https://github.com/balladyrytual9z/Nowy-Dock-.git"
  ```
  (po pushu przywrócić URL bez tokena, żeby nie leżał w konfiguracji)

- Do sprawdzania CI: `gh run watch` lub curl z `Authorization: token $GITHUB_PERSONAL_ACCESS_TOKEN`

## 2026-11-05 (sesja: panel, ikony iOS, ładowanie mapy, ekran play)

- **Panel łączności**: sama warstwa `LAYER_TYPE_SOFTWARE` bywa niewystarczająca —
  układ przepisany z `weight` na **stałe dp** (suwaki 159×172, lewa kolumna 330,
  NightShift 146×58, ModeSegment 178×58, kafelek rail 74 → 3×80 = 240, pasek ciepła
  220). Dodatkowo po `onAttachedToWindow` wymuszamy `requestLayout()+invalidate()`
  po 260 ms, bo nowo dodane poddrzewo custom views potrafi nie odtworzyć się
  z RenderNode. **Nie wracać do `weight` w tym panelu** i nie dodawać `setElevation`
  ani `setClipToOutline` (opisane w `INSTRUKCJE NAPRAW/`).
- **Ikony rysowane kodem**: literały w `Path.moveTo/lineTo/cubicTo` muszą mieć
  przyrostek `f` (`1f`, `17.71f`) — inaczej CI pada na
  `incompatible types: possible lossy conversion from double to float`.
  Kształty bierzemy z klasycznych Material/iOS, ale rysujemy je sami — wektory XML
  na Mali-T720 wychodzą puste. Wi-Fi w stylu iOS = 3 łuki (`drawArc`, 205°/130°)
  z `StrokeCap.ROUND` + kropka; „off” dokłada przekreślenie.
- **`LoadingCover`** (`pl.dock.widget.maps`): zasłona + kółeczko iOS, własna warstwa
  SOFTWARE, blokuje dotyk. Pokazywana tylko, gdy `MapPlace.startupPlace()` zwróci
  `null` i nie ma fixa ani pozycji z pamięci — znika, gdy pozycja jest znana.
- **Dok na innych ekranach**: każdy `DockView.Listener` musi mieć niepuste
  `onBottom()` (powrót na stronę główną: `Prefs.setGoHome(true)` + `finish()`),
  `onBottomLong()` (ostatnie aplikacje) i `setBottomProgress(1f)` na ekranach
  innych niż główny. Puste metody = martwy przycisk (tak było w `NavigatorActivity`
  i `NowPlayingActivity`).
- **Glif przycisku dolnego doka pokazuje CEL, nie miejsce** (`DockView.onDraw`,
  gałąź `bottomT`): ekran główny i strony widżetów (`bottomT ≈ 0`) → **siatka 9
  kwadratów (3x3)** = „lista aplikacji”, strony aplikacji i Biblioteka
  (`bottomT ≈ 1`) → **jeden kwadrat z zaokrąglonymi rogami** = „ekran główny”.
  To samo dotyczy ekranów obcych, bo wołają `setBottomProgress(1f)` i wracają
  do domu przez `onBottom()`. ⚠️ v1.0.96 (`b62fda5`) to odwrócił „w locie” i glify
  kłamały przez wiele wersji — przy każdej zmianie tego bloku sprawdź spójność
  z `LauncherActivity.onBottom()` (`pager.position() < homeIdx + 0.5f` → `appsStart`).
- **Pułapka przy patchowaniu kodu**: `sed -n ... | cat -n` **dodaje** wcięcia, więc
  kotwice do podmian trzeba potwierdzać przez `grep -n ... | cat -A`. Dziś przez to
  cztery podmiany nie trafiły (nawigator, dock, pole `arrow`).
- **CI na gałęzi**: `workflow_dispatch` jest odrzucane (`403`, token bez zakresu
  `workflow`), a `close/reopen` PR-a nie zawsze odpala workflow. Pewny sposób na
  uruchomienie testów na gałęzi: **nowy commit** (zdarzenie `synchronize`).
- Wydanie tworzy się tylko z `push` na `main`
  (`if: github.event_name == 'push' && github.ref == 'refs/heads/main'`), więc
  ręczne uruchomienie workflow na gałęzi nic nie wypuści.

## 2026-11-05 (sesja: lista aplikacji = ostatnia strona panelu)

Ustalenia z użytkownikiem, żeby nikt tego nie „poprawił” z powrotem:

- **Ostatnia strona pagera to PŁASKA lista alfabetyczna wszystkich aplikacji
  zainstalowanych w tablecie** (`AppLibraryPage`). **Bez kategorii**, bez
  nagłówków liter, bez przyklejonego nagłówka i bez pełnoekranowego okna
  kategorii (`showLibraryFolderPopup` usunięte). Silnik kategoryzowania
  (`AppCategorizer`) i kafelek folderu (`LibraryFolderView`) **zostają w kodzie
  na później**, ale nie są nigdzie używane.
- Źródło danych to **osobna lista** `buildLibraryItems()` w `LauncherActivity`
  (nie `buildItems()`): nie filtruje po `Prefs.isAppSelected`, pokazuje
  **oryginalne nazwy** (`ri.loadLabel`) i **oryginalne ikony**
  (`Icons.forPackageRaw`, bez podmian `Overrides`). Wykluczony jest tylko własny
  pakiet Docka. Trzy narzędzia launchera (Ustawienia, CarPlay, Honda OBD)
  wchodzą do tej samej listy i sortują się alfabetycznie.
- **Pasek liter A–Z jest poziomo, pod polem wyszukiwania, ale NIE na całą
  szerokość**: zajmuje ~65% szerokości paska wyszukiwania i jest wyśrodkowany
  (dwa równe odstępy po bokach). Nad paskiem jest odstęp 16 dp, a powiększenie
  litery to skala 1.6 i wychylenie 6 dp — dzięki temu powiększona litera
  **nie nachodzi na pole wyszukiwania**. Litery nigdy nie są wygaszane; litera pod palcem
  powiększa się z animacją + haptyką, tapnięcie litery przewija listę.
  Kierunek powiększania (w górę / w dół) to ustawienie
  `Prefs.libLetterUp` (Ustawienia → Lista aplikacji), domyślnie w górę.
  Pozycje liter liczymy z **tagu wiersza** (`row.setTag(item)`) — stara wersja
  czytała tag z dzieci i pasek był martwy.
- **Szukanie** idzie po oryginalnej nazwie **oraz** po nazwie podmienionej w
  Docku (`Overrides`), ignorując wielkość liter i polskie ogony
  (Ł→L, Ś→S, Ż→Z). Przykład: wpisane „AirDrop” pokazuje „LocalSend”.
  Pole ma przycisk X (czyszczenie) i zamyka klawiaturę przy wyjściu ze strony.
- **Menu długiego przytrzymania wiersza** (kolejność!):
  „Informacje o aplikacji”, „Dodaj do siatki aplikacji” / „Usuń z siatki
  aplikacji” (zależnie od stanu `Prefs.isAppSelected`), „Odinstaluj”.
  Narzędzia launchera (Ustawienia/CarPlay/Honda OBD) **nie mają menu**.
- **Pułapka `selectedPkgs`**: pusta lista znaczy „wszystkie widoczne”, więc
  usuwanie z siatki musi ją najpierw zmaterializować (`setAppOnGrid`).
- **Tryb minusów (edycja) jest tylko na siatce aplikacji** — na liście go nie ma.
- Kropki stron: `panelPageCount()` nie liczy strony Biblioteki, kapsuła tylko na
  ekranie głównym (to już było na `main`, nie ruszane).
- Siatka aplikacji (drag-reorder między ekranami, minus bez potwierdzenia z
  animacją znikania, trwała kolejność) to **osobne zadanie** — nie robione tutaj.

## 2026-11-06 (sesja: porządki w Ustawieniach + zakładka „Wszystkie aplikacje”)

- **Uporządkowaliśmy Ustawienia (iOS-style)**: usunięte powielone ustawienia
  trybu jasny/ciemny z sekcji „Wygląd” (zostaje tylko Styl + Ramka ikon; tryb,
  harmonogram i jasność żyją wyłącznie w „Ekran i jasność”). Kolejność sekcji
  w `ConfigActivity.build()`/`extraSections()` uporządkowana: w kategorii
  „Aplikacje” lista aplikacji jest przed „Ikona CarPlay w docku”.
- **„Odtwarzanie muzyki” przeniesione z „Wygląd i dźwięk” do „Aplikacje”**
  (`categoryForSection` — dodany warunek `s.contains("odtwarzanie")`).
  To integracja źródła odtwarzania (DiPlay), nie wygląd.
- **Nowa aktywność `AllAppsActivity`** (zarejestrowana w manifeście): lista
  WSZYSTKICH aplikacji podzielona na kategorie przez istniejący `AppCategorizer`
  (Sugestie/Ostatnio dodane/Społeczności/Narzędzia/Rozrywka/Biurowe/Nawigacja/Inne),
  karty w stylu ustawień, tapnięcie otwiera aplikację.
- **Wejście „Wszystkie aplikacje”** na samym końcu listy kategorii w
  `finishLayout()` (ikona `Icons.builtin(this, "grid")`, zawsze widoczne,
  także przy wyszukiwaniu „wszystkie”/„aplikacje”).
- ⚠️ Decyzja o **płaskiej** liście `AppLibraryPage` (ostatnia strona pagera,
  bez kategorii) **pozostaje bez zmian** — kategorie weszły jako osobna
  zakładka w Ustawieniach, nie jako strona launchera.
- ⚠️ W sandboxie nie ma JDK/SDK — weryfikacja wyłącznie przez GitHub Actions
  (push gałęzi → PR → `gh run watch`).

## 2026-11-06 (sesja: panel łączności — wciąż pusty w launcherze)

- **Panel miał już warstwę SOFTWARE na samym panelu (`ConnectivityPanel`), ale
  w nakładce launchera nadal był pusty.** Root cause (wg NAPRAWA-PANEL-LACZNOSCI.md,
  Krok 5): animacja alpha na `overlay` (FrameLayout) uniemożliwia Mali-T720
  odtworzenie nowo-dodanego poddrzewa custom Canvas views z RenderNode.
- **Fix (PR #46, v1.0.244):** w `LauncherActivity.showOverlayAt()` dodana
  `overlay.setLayerType(LAYER_TYPE_SOFTWARE, null)` dla API < 30 — cała nakładka
  renderuje się do bitmapy. Pilnuje tego test
  `ConnectivityPanelGuardTest.overlayNakladkiMaWarstweSoftware()`.
  ⚠️ **Nie usuwać tej linii** — na tym GPU overlay bez niej = pusty panel.
- Panele w `ConnectivityActivity` (osobne okno) i w nakładce launchera to
  **dwa różne konteksty renderowania**; naprawa jednego nie oznacza drugiego.

## 2026-11-06 (sesja 2: gest swipe-up + przezroczyste widżety)

- **Gest swipe-up od dolnej krawędzi NIGDY nie działał** (PR #47, v1.0.250):
  wykrywanie wisiało na `root.setOnTouchListener`, ale pełnoekranowy `PagerView`
  konsumuje KAŻDY ACTION_DOWN u dzieci (`onTouchEvent` → `return true`), więc
  listener na rootcie nigdy nie dostawał zdarzeń — martwy kod. ⚠️ **Nie wracać
  do listenera na rootcie** (alarm w `ConnectivityPanelGuardTest.
  gestSwipeUpMieszkaWPagerze`). Gest mieszka w `PagerView`
  (onInterceptTouchEvent przejmuje dotyk tylko przy pionowym ruchu w górę
  z dolnej strefy 120dp; tapy i przewijanie stron zostają nietknięte).
- **Widżety na stronie widżetów (skrajnie lewa strona) są przezroczyste:**
  nowy `GlassAware` + `Ui.glassCard` (lekki ton + obwódka, tapeta prześwituje).
  `markGlass(v)` wywoływane tylko w ścieżce `stacksPage.addWidget(...)`;
  ekran główny zostaje z nieprzezroczystymi kartami. ⚠️ `MapCard` jest
  w subpaczce `pl.dock.widget.maps` — klasa z `pl.dock.widget` wymaga importu.
- Konwencja wydania: po zmergowaniu PR na main automatyczny build tworzy
  release (np. v1.0.250); APK trafia też do `/workspace/project/apk-artifact/`.

## 2026-11-06 (sesja 3: panel znów pusty PO animacji wejścia)

- **Panel otwierany z HEADER doka (klik pod zegarem) bywał pusty PO zakończeniu
  fade** — mimo warstwy SOFTWARE na panelu i na overlayu (PR #48, v1.0.252).
  Działające `ConnectivityActivity` nie ma żadnej animacji wejścia — i zawsze
  rysuje treść. Wniosek: na Mali-T720/API 28 **jakakolwiek animacja alpha na
  nowo-dodanym poddrzewie custom views jest zawodna**. ✅ Na API < 30 overlay
  wchodzi NATYCHMIAST (`setAlpha(1f)`, bez `animate()`); fade tylko na
  SDK >= 30. ⚠️ **Nie przywracać fade na API < 30** — strażnik
  `ConnectivityPanelGuardTest.overlayNaStarymApiNieAnimujeAlpha()`.

## 2026-11-06 (sesja 4: Ustawienia bez przewijania)

- **Ekran główny Ustawień (ConfigActivity, widok kategorii + „Szukaj ustawień…")**
  miał się przewijać — za wysokie wiersze. Zwężono globalnie w ConfigActivity:
  `row()`/`switchRow()` minHeight 68→52 dp, ikony 48→42 dp, `segRow()` padding
  14→10 dp, tytuł 34→30 sp, pasek szukania 17→16 sp z mniejszymi paddingami,
  paddingi ekranu 20/40→12/24 dp. ScrollView został jako fallback dla
  ekranów podsekcji (tam przewijanie nadal potrzebne). (PR #49, v1.0.254).

## 2026-11-06 (sesja 5: PRZEBUDOWA panelu łączności — rozstrzygnięcie)

- Po 4 wersjach (v1.0.244…256) panel łączności w nakładce launchera był pusty:
  widać tylko okno z zaokrągleniami i pigułkę „Edytuj”, a **żadne custom Canvas
  views się nie rysują** (nawet przy konfiguracji PR #14 1:1, v1.0.256).
- **WNIOSEK (potwierdzony testami użytkownika): na Mali-T720/API 28 w dynamicznie
  doczepianym poddrzewie nakładki renderują się WYŁĄCZNIE widoki frameworka.**
- **Fix (v1.0.258, PR #52): ConnectivityPanel + ToolGrid przebudowane od zera —
  ZERO widoków z własnym onDraw()**. Tylko LinearLayout/FrameLayout/TextView/
  ImageView/View z GradientDrawable/SeekBar; ikony to Bitmapy (Canvas CPU, raz)
  w ImageView; zaokrąglenia to GradientDrawable. Strażnik:
  `ConnectivityPanelGuardTest.panelJestFrameworkowy`.
- Zachowane: wejście nakładki PR #14 (fade + animacja + withEndAction re-layout),
  brak warstwy na overlayu, warstwa SOFTWARE tylko na panelu (API<30),
  Wi‑Fi/hotspot otwierają ekrany ustawień, BT wg `btAction`, ukrywanie wg
  `Prefs.showModeButtons/showNightShift`.
- ⚠️ **Nie dodawać do panelu widoków z onDraw() ani podklas View** — strażnik padnie.
## 2026-11-07 (sesja: hierarchia fizycznego przycisku Home radia jak w CarPlay)

Ustalenia zatwierdzone przez użytkownika (dyktowanie głosowe — „di play delay”
to transkrypcja słowa „DiPlay”):

- **Fizyczny przycisk Home radia** (ten, który przywołuje Dock-launcher) ma nową
  hierarchię:
  1. **Ekran główny Docka** → Home wykonuje akcję Home (`runHomeAction()`,
     domyślnie otwiera DiPlay/CarPlay) — TYLKO stąd Home otwiera DiPlay.
  2. **Siatka aplikacji / Biblioteka / strona widżetów** → pierwsze Home wraca
     na ekran główny; dopiero kolejne Home (będąc już na głównym) działa jak
     w pkt. 1 (czyli otwiera DiPlay).
  3. **DiPlay (aplikacja CarPlay)** → Home **zawsze** wraca na ekran główny
     Docka, a NIE na stronę, z której DiPlay otwarto.
  4. **Inne aplikacje** → bez zmian: Home wraca na stronę źródłową (origin
     page, flaga `returnPage`).
- **Implementacja:** flaga `Prefs.diplayReturn` (klucz `diplay_return`) —
  ustawiana w `Launch.carplay()` przy udanym starcie DiPlay, konsumowana w
  `LauncherActivity.onNewIntent()` w gałęzi `!visible` (`fromDiplay` →
  `target = homeIdx` zamiast `returnPage`), zerowana w `onResume()` (żeby nie
  wisiała po powrocie inną drogą; `onNewIntent` przy Home z DiPlay odpala się
  zawsze przed `onResume`, więc obsłużona prośba nie jest tym zerowana).
- ⚠️ Gałąź widoczna `onNewIntent`: `Math.abs(pager.position() - homeIdx) < 0.5f`
  → akcja Home; w przeciwnym razie `pager.goTo(homeIdx, true)`. Dawniej Home
  z KAŻDEJ strony launchera robił akcję Home i zapamiętywał `returnPage` —
  tego nie przywracać.
- Zapis `setDiplayReturn` idzie przez `sp(c).edit()` bez zwiększania `version`
  (flaga wewnętrzna, nie dotyczy rysunku).
- Zmiana wypchnięta bezpośrednio na `main` (commit 5b932d9a) zgodnie z regułą
  z 2026-11-06; APK buduje CI (`build-release.yml` → release
  `CarPlay-Launcher-v1.0.<N>.apk`).

## 2026-11-07 (sesja 2: liquid glass, Biblioteka, panel łączności — styl iOS)

Wzorzec wizualny = Centrum sterowania iOS (screen użytkownika z iPhona);
zdjęcie tabletu pokazuje stan obecny. Zmiany wypchnięte na `main` (commit ef018565):

- **Liquid glass doka**: snapshot tła odświeżany co ~50 ms (było 150 ms; nadal
  OnPreDraw + throttle, bufor reużywany — zero alokacji, bezpieczne dla
  Mali-T720). LiveBlur: RADIUS 8, PASSES 4, saturacja 1.6. Korpus szkła
  jaśniejszy/mniej matowy (gradient 0x24→0x08→0x05→0x1A zamiast 0x2E/0x0D/0x08/0x24).
- **Biblioteka aplikacji — pasek szukania**: pływająca szklana pigułka NAD listą
  (contentPane to FrameLayout; lista przewija się POD paskiem, pasek zawsze
  widoczny; pole EditText przezroczyste wewnątrz szkła).
- **Biblioteka — literki**: podczas przesuwania palca po pasku liter NAD palcem
  wisi szklany bąbel z powiększoną aktualną literą (`letterBubble`, 30 sp,
  TextView + GradientDrawable, śledzi palec, haptyka przy zmianie). Stara
  animacja `magnifyLetter` (powiększanie litery w pasku) USUNIĘTA — nie wracać.
- **Panel łączności — suwaki**: PillSlider przepisany z LinearLayout+weight na
  czysty FrameLayout. Weight potrafił wyjść zerowy na Mali-T720 i suwaki
  spłaszczały się do poziomych pigułek z wielką ikoną — to była zgłaszana
  „zepsuta” wersja. Suwaki smuklejsze: 160 dp szer., ikona 30 dp przy dolnej
  krawędzi, tor bardziej przezroczysty (0x663A3A3C noc / 0x99E9E9EE dzień).
  Karta łączności 146 dp; glassBg jaśniejsze (0x2EFFFFFF). Zakaz onDraw w panelu
  bez zmian (strażnik `ConnectivityPanelGuardTest`).
- **ToolGrid**: koła 46 dp, etykiety 12 sp, ikony aplikacji rysowane w
  rozdzielczości ekranu (48×den px, wcześniej 48 px = rozmyte na gestszych ekranach).
- **Szary pasek pod dokiem**: diagnoza — szkło doka rozmywa okolicę, w tym
  kapsułę kropek (wskaźnik stron) tuż obok doka, i na dole kolumny doka
  prześwituje ona jako szary pas. Użytkownik ODROCZYŁ naprawę do czasu screena
  z auta — NIE ruszać, dopóki nie przyśle zdjęcia.

### Dodatek (screen IMG_8987)
- Użytkownik przysłał screena (PNG, strona główna): szary pasek ma szerokość
  SAMEGO DOKA (nie całego ekranu), miękka/rozmyta górna krawędź, widoczny
  PRZEZ szkło doka (od dołu, ~1,5 wysokości ikony doka), zasłania dolne
  zaokrąglenia szkła.
- Hipoteza „zamazane kropki/kapsuła PageDots (0x66808088) wypełniające pustą
  kolumnę doka w snapshotcie" — ODRZUCONA przez użytkownika („co innego").
  NIE proponować ponownie tej samej diagnozy bez nowego materiału.
- Temat WSTRZYMANY do czasu, aż użytkownik przyśle CZYSTY SCREENSHOT tabletu
  (zrzut ekranu, nie zdjęcie aparatem). Do tego czasu nie ruszać niczego
  związanego z tym paskiem.

## 2026-11-07 (sesja 3: dolny przycisk doka a centrum łączności)

- **Nowa zasada (zatwierdzona przez użytkownika):** gdy centrum łączności
  (overlay panelu w launcherze) jest OTWARTE, dolny przycisk doka pokazuje
  ikonę zamknięcia — zaokrąglony kwadrat (squircle iOS) z dwoma poziomymi
  suwakami z pełnymi kółkami (wzór SVG od użytkownika) — i dotknięcie zamyka
  panel. Po zamknięciu: wszystko jak dotychczas (crossfade siatka 3×3 / kwadrat).
- **Implementacja (commit 7d2774df):** DockView: `BOTTOM_CC = 3` + glif
  (kreska 1.8 dp, przywracana 3.2 dp). LauncherActivity: `onBottom()` gdy
  `overlay != null` → `toggleOverlay()`; `showOverlayAt` ustawia
  `setBottomMode(BOTTOM_CC)`; `hideOverlay` przywraca `setBottomMode(-1)` +
  `setBottomProgress` wg bieżącej strony; `onScroll` NIE woła
  `setBottomProgress` przy otwartym overlayu (wyzerowałby bottomMode i glif
  zniknął w trakcie przewijania za panelem).
- Reszta zasad przycisku bez zmian: `onBottomLong` = ostatnie aplikacje,
  przytrzymanie nagłówka = toast, ekrany wewnętrzne = glif strony powrotu.

## 2026-11-07 (sesja 4: nawigacja jak w Organic Maps — trasy, zakładki, ślady, Wikipedia)

Dobudowane do istniejącego szkieleu Nawigacji (ekran główny, MapCard, MapState,
HeadingSmoother, MapConfig, SmoothLocationOverlay, MapPlace, LoadingCover,
BulkGuard, MapDownloadActivity — **NIETKNIĘTE**). Nowe moduły w
`pl.dock.widget.maps` (wszystkie Java 1.8, komentarze po polsku, float z `f`):

- **`route/`**: `PolylineCodec` (polyline OSRM precision 5, testy JVM z przykładem
  kanonicznym Google), `Route`/`Route.Step` (model), `Maneuvers` (polskie opisy
  manewrów + distText/minText — idą na banner i do TTS), `Guidance` (maszyna
  prowadzenia: okno najbliższego punktu ±8/+60, prompty FAR/NOW/ARRIVED/OFF_ROUTE
  z progami zależnymi od prędkości, "teraz" na przekroczony manewr),
  `RouteClient` (OSRM https://routing.openstreetmap.de/routed-{car|bike|foot},
  profil z Prefs: auto/rower/pieszo, callback na wątku UI), `VoiceGuide` (TTS pl_PL,
  audio focus TRANSIENT_MAY_DUCK, USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).
- **`bookmark/`**: `Bookmark`, `GpxIo` (GPX/KML zapis+odczyt po regex — org.xmlpull
  nie istnieje w testach JVM), `TrackRecorder` (odrzuca szum <7 m i <3 s),
  `MapsDb` (SQLiteOpenHelper singleton: zakładki + cache miejsc — offline search),
  `TrackFiles` (ślady GPX w filesDir/tracks), `BookmarksActivity` (manager:
  zapis pozycji, import OPEN_DOCUMENT, eksport GPX/KML CREATE_DOCUMENT, listy
  zakładek/śladów, profil wysokości; ⚠️ rebuild() zakłada 8 pierwszych dzieci rows
  = nagłówek+przyciski — przy edycji zachować układ).
- **`elevation/`**: `ElevationProfile` (pure), `ElevationClient` (opentopodata
  srtm90m, 100 pkt/req, pauza 1100 ms), `ElevationProfileView` (Canvas, wypełnienie
  pod krzywą, warstwa SOFTWARE jak inne custom views).
- **`wiki/`**: `WikiNearby` (geosearch pl.wikipedia.org, promień ≤10 km).
- **`NavigatorActivity`**: zielona linia trasy (0xFF30D158, 7 dp), pomarańczowy
  ślad (0xFFFF9500), przyciski Zap/Ślad/Jedź (BTN_SAVE=7/TRACK=8/NAV=9, kolumna
  n=10), długie przytrzymanie (>550 ms) Zap/Ślad → BookmarksActivity, Szukaj →
  Wikipedia; karta celu wyższa (66 dp) i dwulinijkowa podczas nawigacji (manewr +
  manewr·do celu·czas); tick() prowadzi Guidance i mówi TTS (Prefs.mapVoice);
  OFF_ROUTE → reroute() z throttle 15 s; ARRIVED → toast + clearDestination;
  zapisywanie śladu awaryjne w onDestroy; handleIntent/onNewIntent (singleTask)
  czyta dest_lat/dest_lon/track_path z BookmarksActivity; wyszukiwanie najpierw
  offline (zakładki + cache), potem Nominatim, wybór zapisuje miejsce do cache.
- **`Prefs`**: `mapRouteProfile`/`setMapRouteProfile` (auto/rower/pieszo) i
  `mapVoice`/`setMapVoice` (domyślnie true).
- **Manifest**: dodany tylko wpis `BookmarksActivity` (singleTop, NoTitleBar.Fullscreen,
  exported=false) — reszta bez zmian.
- **Testy JVM**: PolylineCodecTest, GuidanceTest, ManeuversTest, GpxIoTest,
  TrackRecorderTest, ElevationProfileTest — deterministyczne dane, bez Androida/org.json.
- Wszystko wypchnięte bezpośrednio na `main` (commity od "Mapy: kodek polyline…"
  do "Mapy: nawigacja jak w Organic Maps…"), build/release robi CI jak zwykle.
