# AGENTS.md — pamięć repozytorium Nowy-Dock

## 🛠️ Wymagane umiejętności (Skills)

Agent powinien korzystać z następujących umiejętności. Jeśli któraś nie jest jeszcze zainstalowana, należy ją dodać za pomocą komendy `/add-skill` z adresem URL wskazanym w nawiasie.

### Podstawowe
*   `agent-canvas-environment` – poprawne działanie w środowisku Agent Canvas.
*   `agent-memory` – zapamiętywanie kontekstu projektu.
*   `add-skill` – dynamiczne dodawanie umiejętności.

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
- Nad repo MOŻE równolegle pracować inny agent — przed KAŻDĄ operacją (push, merge, edycja pliku, zamykanie PR) odśwież stan repo i upewnij się, że nie wychodzicie sobie w drogę (szczegóły: sekcja „⚠️ Agenci równolegli” niżej).

## ⚠️ Agenci równolegli (MOŻE pracować drugi agent)
- Nad tym repozytorium MOŻE równolegle pracować INNY agent (2026-11-07, życzenie użytkownika). Nie jest aktywny stale — może wystartować lub skończyć w dowolnym momencie, więc zakładaj, że od Twojego ostatniego spojrzenia stan repo mógł się zmienić.
- ZANIM cokolwiek zrobisz (push, commit na main, merge, edycja pliku, zamknięcie PR-a): odśwież stan repozytorium — HEAD `main`, nowe commity, otwarte PR-y i świeże gałęzie — i upewnij się, że nie kolidujesz z cudzą pracą.
- Nie nadpisuj pracy innego agenta: NIGDY force-push na gałęziach, których nie jesteś właścicielem; przed pushem na własną gałąź sprawdź, czy ktoś w międzyczasie nie dosypał commitów (jeśli tak — rebase/merge, nie nadpisanie); nie przerabiaj plików, które inny agent właśnie zmienia (sprawdź daty ostatnich commitów i otwarte PR-y).
- Jeśli zauważysz obcą aktywność (commity/PR-y/gałęzie, których nie zrobiłeś) — dostosuj się do niej (rebase, praca w innym miejscu), zamiast ją wywalać.
- Przy równoległej pracy nie wchodź dwa razy w ten sam plik na raz; zmiany sporne/rankingowe idą przez PR (nie bezpośrednio na main) — łatwiej się wyminąć i zmergować po kolei.
- Po zakończeniu pracy zostaw w repo czytelnny ślad (opisany commit/PR), żeby drugi agent wiedział, co się zmieniło.

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
- **(2026-11-07) Okna instalatora odcinają dotyk pod nakładkami:** systemowe okno
  potwierdzenia (i ekran Play Protect „Zainstaluj bez skanowania”) **odrzuca dotyk,
  gdy JAKIEKOLWIEK okno rysuje się nad nim** — nawet przezroczysta nakładka z
  `FLAG_NOT_TOUCHABLE` ustawia flagę „obscured”. Dlatego `Updater.install()` woła
  `NightShift.pause(c)` PRZED otwarciem każdego systemowego okna (nakładka Night
  Shift jest wtedy zdejmowana z `WindowManager` CAŁA — sama przezroczystość nie
  wystarcza), a wraca po powrocie do launchera (`BaseActivity.onResume` →
  `NightShift.resume`). **Nie przenosić pauzy „po otwarciu okna” i nie zamieniać
  jej na samo `View.setAlpha(0)` — wtedy przyciski instalatora znów umrą.**
  **Pauza tylko wtedy, gdy okno NA PEWNO się otworzy — nigdy na zapas przed
  `commit()` sesji `PackageInstaller`** (2026-11-07, sesja 3 — zamknięta szczelina
  brzegowa cichej instalacji): sesja pauzuje dopiero w swoim receiverze (przy
  `STATUS_PENDING_USER_ACTION` oraz przed awaryjnym `ACTION_VIEW` po porażce),
  bo cicha sesja OEM (sukces bez okna) zostawiłaby nakładkę zdjętą bez żadnego
  `onResume`, który by ją przywrócił. Strażnik kolejności: `UpdaterPauseGuardTest`.

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
  do domu przez `onBottom()`. ⚠️ v1.0.96 (`b62fda5`) to odwróciło „w locie” i glify
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
  spłaszczały się do poziomych pigułek z wielą ikoną — to była zgłaszana
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

## 2026-11-07 (sesja 3: Night Shift — blady ekran po refactorze + blokowanie instalacji)

Zgłoszenia użytkownika: (1) po „poprawkach technicznych” Night Shift ekran robił
się BLADY zamiast żółty, (2) przy włączonej nakładce nie dało się kliknąć
„Zainstaluj bez skanowania” w oknie instalatora (działał tylko Home).

- **Root cause (1):** refactor z 3.11 (60e2204/7fcbcc6/e8f530e, wszystkie
  `[skip ci]`) zamienił stały pomarańcz na liniowy Kelvin 6500→2200 K + drugą
  warstwę czarnego dimu. Przy praktycznym suwaku kolor z krzywej Plancka jest
  prawie biały (sila 40 → RGB(255,224,198) @ 20%), a dim tylko szarzy — efekt
  „blady”. Zakres „2200 K” był nieosiągalny (suwak max 80 → f=0.8 → 3060 K).
- **Fix (PR #62, gałąź `fix/nightshift-ios-warmth`):** jedna warstwa, zero dimu;
  docelowy punkt bieli = interpolacja biel → ColorTemp(2700 K); kolor nakładki
  NASYCONY i liczony tak, aby biel trafiała DOKŁADNIE w punkt bieli celu
  (`ColorTemp.overlayTint`: C = 255 − (255−cel)·255/a, alpha = 0.70·f);
  suwak /80 (80 = pełne 2700 K). Testy JVM: `ColorTempTest` (biały trafia w
  punkt bieli ±1 na całym zakresie). ⚠️ Nie wracać do liniowego Kelvina z alpha
  WARM_MAX i nie dokładać dimu do tej nakładki.
- **Root cause (2):** systemowe okno instalatora (i Play Protect) **odrzuca
  dotyk, gdy jakiekolwiek okno rysuje się nad nim** (flaga „obscured”, ochrona
  przed tapjackingiem) — nakładka Night Shift z `FLAG_NOT_TOUCHABLE` i tak
  zasłania. Android NIE ma typu okna „pod systemowymi dialogami, a nad
  aplikacjami”.
- **Fix (2):** `NightShift.pause/resume` — pauza **usuwa okno z WindowManager**
  (view alpha 0 nie wystarcza: przezroczyste okno nadal ustawia „obscured”);
  `Updater.install()` pauzuje PRZED otwarciem każdego systemowego okna
  („nieznane zrodla”, PackageInstaller, ACTION_VIEW; root bez pauzy — cichy);
  `BaseActivity.onResume()` przywraca filtr po powrocie do launchera.
  ⚠️ Nie przenosić pauzy „po startActivity” — okno zdąży się otworzyć pod
  nakładką i przyciski znów umrą.

### Pułapka CI: PR testuje MERGE z mainem (2026-11-07, sesja 3 — diagnoza)

- **Czerwony main = czerwony KAŻDY PR.** GitHub Actions dla `pull_request` buduje
  MERGE REF (main + gałąź), nie samą gałąź. Runy PR-a #62 padały trzy razy
  (#306, #307, #309), choć kod gałęzi był poprawny — merge zawierał zepsuty main.
- **Objaw:** failing runy ~1m10–1m40s, adnotacja „Run Unit Tests: Process
  completed with exit code 1”, brak failing suite w raporcie = **błąd kompilacji**
  (testy nie zdążyły wystartować). Zielone runy trwają ~3 min.
- **Root cause:** `ef018565` (liquid glass, 13:19) wprowadził w
  `AppLibraryPage.java` anonimowy `OnTouchListener` paska liter, którego
  `onTouch` nie miał `return` na wszystkich ścieżkach → javac „missing return
  statement” → main czerwony od runu #305 (ostatni zielony release: v1.0.304).
  Naprawione dodaniem `return true;` na końcu `onTouch`.
- **Szybki test „czy to mój PR, czy main”:** sprawdzić ostatni release i listę
  runów na stronie /actions — jeśli main od ostatniego release'a ma tylko failed
  runy, winny jest main, nie gałąź.
- **Pułapka logów:** logi CI wymagają logowania, a API ma limit 60/h na IP
  sandboxu — adnotacje joba są czytelne anonimowo na stronie runu.


## 2026-11-07 (sesja 3: NAPRAWA initu OBD - "laczy sie z adapterem, ale nic sie nie dzieje")

Commit 6871773522 (7 plikow). Diagnoza: stary init NIGDY nie weryfikowal
odpowiedzi ECU - "polaczono" pojawialo sie z samego socketa SPP.

- session/ElmSession.kt (rewrite): initialize(preferredProtocol, onStep)
  -> InitResult.Success/Failure(step, error). Sekwencja: ATWS (fallback
  ATZ + 3 s, klon wstaje ~3 s) -> ATE0 x3 -> opcjonalne ATL0/ATH1/ATAT1/
  ATST32 (60 ms odstepy; "?" klona = nie porazka, lista unsupported) ->
  ATSP<pref z prefs obd_protocol|0> -> SONDA 0100 (pierwszy test, ze ECU
  odpowiada; 8 s znany / 15 s auto) -> ATDPN/ATI/ATRV. Hybrydowy ATST:
  ATST32 (~200 ms) rura PID, ATSTFF (~1 s) automatycznie przed wolnymi
  trybami 03/04/06/07/09/0A/21xx/22xx (ElmInitSupport.isSlowDiagnostic).
  ATS0 celowo NIE wysylane - parsery licza na spacje.
- session/ElmInitSupport.kt (nowy, zero android.*): stripNoise /
  isEcuSilence / probeOk / parseDpn / protocolName / parseVoltage /
  errorName / isSlowDiagnostic. Test: 10 testow JUnit4 JVM
  (test/session/ElmInitSupportTest.kt).
- orchestration/ConnectionManager.kt: auto-retry initu max 5 prob,
  backoff 1/2/4/8/16 s (decyzja uzytkownika; akumulator - ELM327 ma staly
  plus na pinie 16). Reczne POLACZ zeruje licznik. Protokol utrwalany
  z ATDPN do prefs obd_protocol. Prawdziwa nazwa protokolu w UI zamiast
  zaszytej stalej. Reinit po reconnect tez przez nowy initialize().
- repository/VehicleUiState.kt: initStep + retryAttempt.
- transport/SppTransport.kt: bajty 0x00 pomijane w czytniku (dok. quirk
  klonow ELM327 - fillery gubily parsery). sendAndWaitPrompt ->
  withTimeoutOrNull (timeout = IOException, nie CancellationException).
- ObdDiscoveryActivity.kt: status "Ponawiam (n/5) - <krok>" przy laczeniu,
  protokol w statusie po polaczeniu.

Plan KROK 2 (nie zrobiony): IgnitionMonitor - wykrywanie zaplonu OFF
(cisza ECU + AT RV < 13.2 V + ACTION_SCREEN_OFF/SHUTDOWN): flush BlackBox,
rozlaczenie BT, resume po zaplonie ON. Uruchadze z userem po teście KROKU 1
na aucie.


## 2026-11-07 (sesja 4: UFO Cluster wg specyfikacji CZESC II dokumentu "ui obd.txt")

Commit b9358ccfa1. Uzytkownik: "buduj jak w dokumencie" - start doprowadzania
modulow OBD do specyfikacji (dokument ma 65 sekcji; kolejnosc wg sekcji 65
miala byc: transport -> Room -> analytics -> services -> UI, ale user chce
widoczne efekty, a warstwa danych dziala po KROKU 1).

- UfoClusterFullView (rewrite 850 linii): pelny stan VehicleUiState (nowe
  BARO/AFR/STFT/MAF/MIL/dtcCount); luki boczne jako PRAWDZIWE luki 180x480
  (SweepGradient, 8 dp, bez podzialek, wypelnienie od dolu; BOOST zero
  w polowie); petla MID BOOST->ECT->VOLTAGE->TPS->IAT->LTFT z ikonami
  24x24 (glify Canvas) i etykietami interwalu (C / TRIP, V / 1h, bar / 5m);
  prawy luk FUEL->TPS->IAT->LTFT (FUEL = AFR z lambda); footer 5 sektorow
  od lewej ZGODNIE z pkt 11 (CE/DTC | ZAPISZ EVENT | SWAP UI | zebatka |
  OBD status + napiecie; wczesniej status i DTC byly zamienione);
  bieg przygaszony (wylczony ze RPM/km/h - pkt 9.2 zakaz udawania pomiaru);
  long press na luku > 1 s = menu przypisania (AlertDialog, ARC_PARAMS);
  LCD long press > 1.5 s = reset MAX/AVG (dokument mowi 1.5 s nie 1 s).
- Progi alarmow luk czytane z prefs alarm_ect_max (102), alarm_volt_min
  (11.8), alarm_volt_max (14.8), alarm_ltft_max (10), alarm_iat_max (65).
  NOWE KLUCZE - do dodania w ObdSettingsActivity (NASTEPNY KROK, razem
  z speed_tap_action: units|dtc|none - pkt 9.7 akcja tapniecia predkosci).
- UfoClusterActivity: update(s) pelnym stanem; showArcAssignDialog().

Kolejnosc dalszych prac wg dokumentu: (1) progi w Ustawieniach OBD,
(2) CZESC III CarPlay Dashboard (Left Dock, karty, lista parametrow,
matryca wykresow 18.x, Multi-Overlay 19), (3) wykres w LCD MID (pkt 8.7:
30 s historii, wybor parametrow), (4) moduly CZESCI IV (DTC freeze frame,
Misfire Lab), (5) Room/logging, (6) IgnitionMonitor (Engine OFF/RUNNING).

## 2026-11-07 (sesja 5: CarPlay Dashboard wg CZESCI III + ustawienia + resume po zaplonie)

User: "rob wszystkie 5 punktow, sprawdz czy cale UI dziala, zostawiam Cie pracuj"
(pelna autonomia). Kolejka z sesji 4 wykonana w ~80% - punkty (4) i (5) okazaly
sie w wiekszosci zrobione wczesniej (audyt potwierdzil).

- Commit c25483195c: ObdSettingsActivity - helpery numFieldDec (pole dziesietne,
  TYPE_NUMBER_FLAG_DECIMAL - bez flagi klawiatura nie przyjmowala kropki) i
  cycleBtn; zakladka ZEGARY: progi alarm_ect_max/alarm_volt_min/alarm_volt_max/
  alarm_ltft_max/alarm_iat_max (pkt 10.7) + speed_tap_action (pkt 9.7).
- Commit 326c8d77: CarPlayDashboardActivity wg CZESCI III. Dock: przewijalna
  lista modulow (OVERVIEW/LIVE DATA/TRIP/BLACK BOX/MISFIRE/DTC/E-RAPORT/SERWIS)
  + DOL PRZYPIETY (QUICK BUTTON/USTAWIENIA/HOME - pkt 15.2); aktywna zakladka
  = pigulka akcentu; QUICK BUTTON konfigurowalny (quick_button_action:
  save|reconnect|cleardtc; reconnect = manager.disconnect()+connect() - oba
  NIE sa suspend, wywolanie z onClick jest poprawne). Karty tapowe z animacja
  press 97% (pkt 16.2); karta DTC z PID 0101 (MIL/dtcCount -> DtcManager);
  dzien/noc wg slonca (night_auto+lat/lon, domyslnie ciemny); akcent
  carplay_accent (blue/red/green); Multi-Select max 4 wykresy, nadmiar
  konfigurowalny multi_select_overflow (fifo|block+toast - dokument
  nierozstrzygnal; decyzja: konfigurowalne, domyslnie FIFO). Nowa sekcja
  "CarPlay Dashboard" w zakladce ZEGARY ustawien OBD.
- Commit (ten): ObdCoreService - dokonczenie KROKU 2/IgnitionMonitor:
  startIgnitionResume() na ACTION_SCREEN_ON + ACTION_POWER_CONNECTED -> po
  2.5 s autoConnect(), gdy sesja nie zyje (szanuje adapter_mac/pair_lock).
  Pelna petla: ACC watchdog rozlacza przy zaplonie OFF + V<12 V przez 3 min,
  resume po powrocie zaplonu; recznie QUICK BUTTON reconnect.
- STWIERDZONE GOTOWE (audyt, bez mojej zmiany): DtcManagerActivity ma juz
  Freeze Frame (Mode 02), kasowanie (Mode 04), VIN (Mode 09); MisfireLab +
  MisfireAnalyzer + Mode 06 co 30 s w ObdCoreService; BlackBox/AppDatabase
  WAL + NanoServer CSV :8080 + TripActivity + VirtualDyno +
  MechanicalHealthMonitor.
- ZOSTAJE wg dokumentu: (a) wykres 30 s w LCD MID zegarow UFO (pkt 8.7),
  (b) scrubber/crosshair + zakresy 30s/1m/5m + eksport PNG z legenda (18.x),
  (c) Multi-Overlay 19 z Correlation Ribbon, (d) ikony na kartach CarPlay
  (pkt 16), (e) klasyczny IgnitionMonitor OFF/ACC/RUN jako pelna maszyna
  stanow (obecnie watchdog+resume).


### Dopisek (ten sam dzien, commit 0f382d74): wykres 30 s w LCD MID (pkt 8.7)

- UfoClusterFullView: pozycje CHART:<param> w petli MID dla WSZYSTKICH
  parametrow apki (CHART_KEYS: BOOST/ECT/VOLTS/TPS/IAT/LTFT/STFT/MAF/BARO/
  FUEL - pelna lista, nic na stale; pkt 8.7 zabrania stalej listy).
  Ring buffer 128 probek, min. odstep 250 ms (~30 s okno), zero alokacji
  w onDraw. Rysunek: pikselowe wypelnienie 4x4 pod linia + gladka linia
  wektorowa (wygladzanie quad), skala liczona z okna 30 s (min/max + 10%
  marginesu) - bez zmyslonej skali. Brak probek = "ZBIERAM DANE...",
  nie udany przebieg. Naglowek z tagiem "WYKRES 30 S"; panel MAX/AVG
  i reset long-press (> 1.5 s) dzialaja tez na pozycji wykresu.
- FIX przy okazji: pozycja petli MID miala klucz "VOLTAGE", a paramValue()
  obsluguje "VOLTS" - pozycja VOLTAGE nigdy nie pokazala danych. Teraz VOLTS.
- Punkt (a) listy "ZOSTAJE" z sesji 5 wykonany; zostaja (b) scrubber/eksport
  PNG (18.x), (c) Multi-Overlay 19 z Correlation Ribbon, (d) ikony na kartach
  CarPlay (pkt 16), (e) pelna maszyna stanow IgnitionMonitor.

### Dopisek 2 (sesja 5 cd., user: "rob ile mozesz w sensownych ilosciach")

Punkty (b)-(e) listy ZOSTAJE wykonane - 6 commitow, kazdy z walidacja
kotwic (count==1), ASCII, bilansu klamer/nawiasow i race-checkem HEAD.

- Commit dec6eada: ChartView wg pkt 18.x/47.x - scrubber z crosshair
  (wartosc + timestamp pomiaru przy przesuwaniu, znika po gesie),
  zakresy 30 s / 1 min / 5 min / cala trasa (timeRangeMs), krzywe
  Beziera, etykiety osi, min/max/avg WIDOCZNEGO okna, exportPng()
  (obszar + legenda + zakres + data; getExternalFilesDir - bez
  uprawnien; brak danych = brak pliku, pkt 47.5), bufor 1200 probek.
  API label/unit/push/sampleCount zachowane - nic nie zepsute.
- Commit 27402689: kontrolki nad matryca wykresow w CarPlay: ZAKRES
  (przelacznik cykliczny) + EKSPORT PNG (kazdy wykres osobny plik,
  Toast z katalogiem; overlay tez respektuje zakres).
- Commit 9a036601: pkt 19 Multi-Overlay - OverlayChartView (wspolna
  os X, linia + indywidualny kolor na ZAZNACZONY parametr, legenda
  z jednostkami; skala kazdej linii znormalizowana do wlasnego zakresu
  - jawnie podpisane na wykresie, bez udawania wspolnej skali Y);
  przycisk OVERLAY w kontrolkach. CorrelationAnalyzer (analytics/,
  czysty JVM): korelacje Pearsona po wspolnych timestampach, pary
  LOAD-LTFT / LOAD-STFT / ECT-LTFT / IAT-ECT / LOAD-MAP, raport po
  polsku (kierunek, r, N probek), statusy OK/INFO/BRAK, MIN_SAMPLES=20,
  seria o zerowej wariancji pomijana. Correlation Ribbon pod wykresem
  prezentuje WYLACZNIE tekst modulu + status (UI nie wymysla diagnozy,
  pkt 19); analiza odswiezana co 5 s. + 6 testow JUnit4.
- Commit 5814b240: pkt 16 ikony kart - CardIcon (Drawable, glify
  wektorowe 24x24 rysowane na Canvas bez plikow zasobow: tacho /
  speedo / bolt / thermo / warning / bars); captionRow = ikona +
  podpis na kazdej karcie Overview.
- Commit 3a0b59bf: pkt 35 + 44.3 EngineState wg dokumentu - OFF:
  RPM==0 + V<12.4 utrzymane > 2 s (hold chroni przed chwilowa cisza
  ECU; zegar wstrzykiwany w konstruktorze); RUNNING: RPM>500 +
  V>13.2; progi jako parametry konstruktora (pkt 35: "musza byc
  konfigurowalne"). CRANKING (1..450) i PROTECTED (V<11.0) zachowane
  - uzywa ich PidScheduler i watchdog ACC. + 6 testow JUnit4.
- ZOSTAJE z dokumentu (nizszy priorytet / wieksze ryzyko bez testu
  na aucie): 16.1 personalizacja kart (Drag&Drop + trwala kolejnosc),
  17.3 Auto-Sort by Anomaly (toggle), 17.4 rozwinięcie parametru ↗
  (pelny ekran), 35.1 zachowania UFO po Engine OFF (luki na VOLTAGE/
  AVG_TRIP_FUEL, dyskretny E-RAPORT), 34.2/34.3 motyw dzien/noc
  zegarow UFO, 36 Split-Screen, 38 widzet Androida.

### Dopisek 3 (sesja 5 cd., user: "Rób dalej" - CALA lista ZOSTAJE wykonana)

- Commit 8b7ffbf9: UFO - motyw dzien/noc zegarow (34.2/34.3: BG_DAY
  #121722 / BG_NIGHT #05070B, ticki #FFFFFF/#FFFDE7, neonowa poswiata
  tarczy TYLKO w nocy, alfa 0x33; setNight() odbudowuje warstwe
  statyczna; systemowa jasnosc nietknieta - 34.4) + Engine OFF
  (35.1/35.2: wskazowki plynnie do zera, lewy luk -> ufo_off_left
  [VOLTS], prawy -> ufo_off_right [AVG_TRIP_FUEL], przypisania OFF
  konfigurowalne w Ustawieniach, powrot do wlasciwych luk po rozruchu,
  dyskretny E-RAPORT na tarczy) + NOWY parametr AVG_TRIP_FUEL (L/100km
  z MAF/AFR 14.7/745 g/l + dystansu, NaN przy < 0.2 km) + usuniety
  FLAG_KEEP_SCREEN_ON z UfoClusterActivity (pkt 37).
- Commit e9eaee0f: 17.3 Auto-Sort by Anomaly (anomal przenosi wiersz
  na poczatek listy Live Data raz, bez migotania; toggle
  auto_sort_anomaly, domyslnie WL) + 17.4 rozwiniecie parametru ↗
  (pelny ekran jednego wykresu + ZAMKNIJ; wykres w mapie charts, wiec
  telemetria plynie bez zmian).
- Commit 7391f788: 16.1 personalizacja kart Overview - rejest kart
  (RPM_SPEED/BATTERY/ECT/DTC/OBD), trwala kolejnosc prefs
  overview_cards_order, pierwsza karta w kolejnosci = duza, reszta
  w rzedach po 2; Drag & Drop (long press + startDragAndDrop, drop na
  karcie = zamiana + zapis), widocznosc kart toggleBtn ov_card_*. PUŁAPKA:
  pressable (animacja 16.2) konsumuje DOWN w onTouchListener, wiec
  klasyczny OnLongClickListener nie zadziala na kartach RPM/SPEED i DTC
  - start drag idzie z TEGO SAMEGO timera long-press w pressable(...)
  (opcjonalny trzeci parametr), nie przez setOnLongClickListener.
- Commit 8e0c52ca: 36 Multi-Window/Split-Screen. CarPlay: w split
  dok idzie na dol jako poziomy pasek ~50 dp (HorizontalScrollView,
  wszystkie moduly + QUICK/USTAWIENIA/HOME), Live Data w JEDNEJ kolumnie
  (applyLiveStack); workArea jest re-parentowane (setContentView
  buildRoot()), wiec karty/zaznaczenia/wykresy przetrwaja zmiane trybu;
  UFO: onMultiWindowModeChanged -> start CarPlay z extra "from_ufo" +
  finish(); CarPlay przy wyjsciu z Multi-Window i from_ufo wraca do
  UFO. dockBtn ma wariant horizontal; buildDock()/buildDockBar() czyszcza
  dockBtns (inaczej po rebuild highlightActive swieci starymi przyciskami).
- Commit 5b5d0f75: 38 widzet OBD - tlo #1C1C1E radius 16 dp obwodka
  #2C2C2E (noca) / #F2F2F7 + #D1D1D6 (dzien, auto wg NOAA jak cala apka),
  wartosci duze bold + jednostki mniejsze, do 4 parametrow (sloty
  widget_p1..p4 w Ustawieniach sposrod WSZYSTKICH parametrow apki,
  default RPM+SPEED), dane z pelnego VehicleUiState (push(context, s)
  - zmieniona sygnatura, obaj nadawcy: ObdCoreService +
  ObdDiscoveryActivity), throttle 1 push / 2 s (RemoteViews drogie),
  klik otwiera OBD Discovery. Nowe drawables obd_widget_bg(_day).xml,
  minHeight widzetu 25 -> 60 dp (4 parametry nie mieszcza sie w 25).
- ZOSTAJE z Dopiska 2: WYKONANE WSZYSTKIE. Dokument "ui obd.txt"
  zaimplementowany w calosci; pozostale ryzyka to wylacznie kwestie
  do potwierdzenia na aucie (EngineState hold 2 s, Split-Screen na
  Android 9 radia, Drag&Drop kart). Na starcie tury release v1.0.312;
  kazdy commit tej tury buduje nowy release v1.0.<run_number> (CI
  ~3 min/push, samoaktualizacja Docka sama podciagnie najnowszy).

### Dopisek 4 (sesja 5 cd.: SAGA CI - 16 faili #313-#329, naprawa, v1.0.331)

- AUDYT po "czy zrobione wszystko": commity z Dopiska 3 wszystkie NA main,
  ale CI padalo w "Run Unit Tests" od runu #313 do #329 - ZADEN commit
  sesji 5 nie byl wydany w APK (ostatni zielony: v1.0.312). Objaw: brak
  failing suite w raporcie = blad KOMPILACJI (testy nawet nie startowaly).
- Diagnostyka (commit 327ef6d5, ZOSTAJE NA STALE): po failu gradle krok
  robi grep "^e: |error: |FAILURE|..." ci.log | head -n 30 -> ::error::<linia>;
  adnotacje sa PUBLICZNE na /commit/<sha>/checks (logi runow wymagaja admina).
  head podniesione 8 -> 30: przy 8 bledy poza top-8 byly niewidoczne
  (przez to saga byla dlugo nierozpoznana).
- Przyczyny i fixy (commity 622f5d1f + 35521760):
  1) ObdCoreService: kod ignition-resume (sesja 5) uzywal BroadcastReceiver/
     IntentFilter BEZ importow. Fix: 2 importy.
  2) CarPlayDashboardActivity: po dodaniu trzeciego parametru
     horizontal: Boolean do dockBtn (commit 36) trailing lambda
     dockBtn("USTAWIENIA") { ... } przestala wiazac sie z onClick (spada na
     horizontal). PUŁAPKA: trailing lambda wiaze sie z OSTATNIM parametrem -
     kazde doklejenie parametru po function type lamie WSZYSTKIE call-sites
     z trailing lambda. Fix: jawny argument dockBtn("USTAWIENIA", { ... });
     to samo dla HOME.
  3) CarPlayDashboardActivity: buildLivePage mialo page.addView(scroll),
     a zmienna nazywa sie liveScroll (regresja commitu 36). Fix: liveScroll.
  4) UfoClusterFullView (commit 8b7ffbf9): zdublowany TICK (nowy var z
     setNight + stary val = konflikt deklaracji), literowka
     Paint.ANI_ALIAS_FLAG (poprawnie ANTI_; kaskada "Variable expected" na
     pStroke), speedKmh(Int).isNaN() (Int nie ma isNaN). Fix: usun stary
     val TICK, popraw literowke, zwykle speedKmh >= 0.
- Po fixach: run #331 ZIELONY, release v1.0.331 - APK zawiera CALA prace
  sesji 4-5 (UFO 34/35/37, 17.3/17.4, 16.1, 36, 38). Samoaktualizacja
  Docka podciagnie v1.0.331 sama.
- WNIOSKI NA PRZYSZLOSC: (a) po serii pushow ZAWSZE sprawdzac kolor CI
  (ostatni release != ostatni commit = cos padlo, nie ufac "push poszedl");
  (b) adnotacje ::error:: czytac przed jakimkolwiek zgadywaniem; (c) zmiana
  sygnatury helpera = przejrzenie wszystkich call-sites, takze tych
  pozostawionych w starym stylu.

### Dopisek 5 (sesja 5 cd.: podwojny audyt OBD + 38.2 sloty MAF/BARO/AFR, v1.0.333)

- AUDYT "sprawdz sie 2 razy": (1) poziom CI - run #331 zielony,
  release v1.0.331 kompletny; (2) poziom zrodlowy - kazdy punkt
  34/35/36/37/38/16.1/16.2/17.1-17.4 zweryfikowany marker po markerze
  wg "ui obd.txt" (EngineState progi 12.4/13.2/500/2s, kolory UFO,
  E-RAPORT, offLeft/RightParam, dock 50dp, brak FLAG_KEEP_SCREEN_ON
  we WSZYSTKICH ekranach OBD, kolory widzetu). Jedyna znaleziona
  kwestia: 38.2 - sloty widzetu mialy 10 parametrow (lista Live Data),
  a apka ma tez MAF/BARO/AFR. User zatwierdzil rozszerzenie.
- Commit 9ebe14dd: ObdWidgetProvider PARAMS 10 -> 13 (MAF "g/s",
  BARO "kPa", AFR ":1" z lambdaAfr; NaN = "--") + paramValue dla
  nowych kluczy + sloty widget_p1..p4 w Ustawieniach rozszerzone
  (wszystkie 4 Triple dostaja MAF/BARO/AFR po 2 pierwszych pozycjach).
  Asercja przed patchem: pola mafGs/baroKpa/lambdaAfr ISTNIEJA w
  VehicleUiState (nie zgadac nazw pol).
- PUŁAPKA concurrency: run #332 (widget) zostal CANCELLED przez push
  rownoleglego agenta (Night Shift, commit 1f973bd5) - cancel-in-progress
  zabija trwajacy build; brak release v1.0.332. To NIE jest blad:
  commit widgetu jest RODZICEM commitu rownoleglego, wiec wszystko
  pojechalo w runie #333 (zielony, release v1.0.333 z APK zawierajacym
  oba zmiany). Przy rownoleglej pracy: po swoim pushu obserwowac KOLOR
  CI, nie tylko "push poszedl" - wlasny run moze byc anulowany.
- Stan: "ui obd.txt" w calosci wdrozony i zweryfikowany podwojnie;
  wydane w v1.0.333. Zostaje wylacznie potwierdzenie na aucie.

### Dopisek 6 (sesja 5 cd.: audyt "honda zegary.txt" + fix, v1.0.334)

- AUDYT "honda zegary.txt" (drugi dokument zrodlowy, stylistyka zegarow
  UFO): pelne porownanie punkt po punkcie z UfoClusterFullView + rozstrzyganie
  konfliktow z ui obd.txt (nadrzedny). Stan na wejsciu: ~95% zgodne.
- Uzasadnione odstapienia (konflikt dokumentow, wygrywa ui obd.txt):
  (a) tlo #0A0D14 z sekcji 1 nigdy nie wystepuje - tryb dzien/noc 34.2/34.3
  (NOAA zawsze aktywny) nadpisuje palete statyczna: dzien #121722, noc
  #05070B; (b) #FFFDE7 (34.3) zamiast #FFFEEA (sekcja 1/7.2); (c) aura
  20% noca zamiast 80% - ui obd 34.3 "ograniczona intensywnosc", dzien
  wylaczona (34.2); (d) jasnosc ekranu NIETKNIETA (34.4 rozwiazuje
  dwuznacznosc z honda zegary); (e) AVG czarny - ui obd 8.5 dopuszcza
  #000000 lub bursztyn; (f) LCD bez radius - ui obd 8.5 "naroce ostre".
- REALNE USTERKI znalezione i naprawione (commit e524f689, v1.0.334):
  1) MALE PODZIALKI 1.2dp < minimum 1.5dp (honda zegary pkt 2 - na IPS
     720p cieńsze linie znikaja) -> 1.5f;
  2) CYFRY SKALI 30sp zamiast 28sp Bold Italic (honda zegary pkt 6) -> 28f;
  3) ETYKIETY "V / 1h", "bar / 5m" KLAMALY - MAX/AVG liczone "od startu
     sesji", a nie z deklarowanego okna. Fix: PRAWDZIWE statystyki
     okienne (BOOST/MAF: 5 min; VOLTS/BARO: 1 h) - ring buffory 1200/14400
     probek (~375 KB, OK przy 128 MB), skan 4x/s throttled, reszta parametrow
     zostaje "od startu" zgodnie z etykieta "/ TRIP". Reset long-press
     czysci tez bufory okienne;
  4) ALOKACJE STRING w onDraw ("MAX: " + fmtParam co klatke = 2 Stringi/
     klatke przy 60 FPS) - lamiemy zasade zero-alokacji (honda zegary pkt 3,
     Mali-T720 GC) -> cache statKey/statHave/statMaxSrc/statAvgSrc, rebuild
     tylko przy zmianie wartosci (~4 Hz).
- PUŁAPKA: dokumenty zrodlowe bywaja sprzeczne SAMA ZE SOBA (honda zegary
  sekcja 1 vs 4: LCD #6B7C80 vs #5E6F73; sekcja 1 vs 5: #FFFEEA vs #FFFDE7).
  Rozstrzygnieta wersja = bardziej szczegolowa / nadrzedna (ui obd.txt).
  Dokladniejsza sekcja wygrywa (4 > 1, 34.x > 6).
- Stan: OBA dokumenty ("ui obd.txt" + "honda zegary.txt") wdrozone w pelni;
  wydane w v1.0.334.