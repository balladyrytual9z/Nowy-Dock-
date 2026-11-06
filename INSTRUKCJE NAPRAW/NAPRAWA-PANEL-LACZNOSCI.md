# NAPRAWA: Panel łączności rysuje tylko przycisk „Edytuj" (puste okno)

**Repo:** `Nowy-Dock-` (branch `main`) · **Objaw występuje od:** serii łatek v1.0.142 → v1.0.166 (PR #14, #17)
**Urządzenie docelowe:** ESSGOO 9" head unit, MT8163 (armv7, 32-bit), Mali-T720, OpenGL ES 3.2, realny **Android 9 / API 28** (wyświetlane „15.0" jest sfalszowane), 1280×720, heap JVM 128 MB — patrz `docs/urzadzenie-essgoo-9.md`.

## 1. Objaw

Dotknięcie górnej strefy doka (HEADER, „niewidoczny przycisk", górne ~164 dp) otwiera nakładkę Centrum łączności. Widać:
- przyciemnienie + rozmyte tło (overlay) — **rysuje się**,
- tło panelu `Ui.popupBg` (zaokrąglone okno) — **rysuje się**,
- pigułka **„Edytuj"/„Gotowane"** (TextView, `WRAP_CONTENT × dp(36)`) — **rysuje się**,
- **cała reszta NIE rysuje się**: PillSlider×2, NightShiftButton, ModeSegment, prawa kolumna z 3× ToolGrid.Tile (Wi-Fi/BT/hotspot), ToolGrid (nawet hint „Brak narzędzi…" z `dispatchDraw`).

Kluczowa obserwacja: **rysują się wyłącznie widoki frameworka (TextView/ImageView), nie rysuje się ŻADEN custom Canvas View wewnątrz panelu.** W tym samym oknie DockView/ClockCard (też custom Canvas) rysują się normalnie.

## 2. Przepływ buga (aktualny kod, main)

`DockView.hit()` strefa HEADER → `LauncherActivity.onHeader()` → `toggleOverlay()` → `showOverlayAt()` (`LauncherActivity.java` ~1441):
1. zrzut ekranu + `Blur.blur` → `ImageView` (szkło) + `View` tint — framework, działa,
2. `overlay = new FrameLayout`, `panel = new ConnectivityPanel(...)`, `overlay.addView(panel, lp)` z **EXACTLY** `panelW = max(dp(240), min(dp(560), windowWidth-24dp))` i `WRAP_CONTENT` wysokością,
3. `root.addView(overlay, indexOfChild(dock), MATCH_PARENT×MATCH_PARENT)` — overlay POD dok (nad pagerem),
4. fade `overlay.animate().alpha(0→1)` 200 ms; **na panelu brak jakiejkolwiek animacji** (usunięta w PR #17).

Panel (`ConnectivityPanel.java`): LinearLayout VERTICAL → head (tytuł + „Edytuj"), `main` HORIZONTAL → kolumna `left` (suwaki, przełączniki — **wszystko na width=0+weight**) + `rail` 72×240 dp, potem `ToolGrid`.

## 3. Różnice względem działającego `Launcher-CarPlay` (ten sam panel działa!)

| Cecha | Launcher-CarPlay (DZIAŁA) | Nowy-Dock- (NIE działa) |
|---|---|---|
| Rozmiary dzieci | **wyłącznie stałe dp** (330 dp, 64×196, 96×96…) | **weighty wszędzie** (width=0+weight: suwaki 1f, przyciski 1f/1.25f, kolumna 1f) |
| Elevation panelu | `setElevation(dp(12))` | **brak elevation** |
| Animacja otwarcia | ViewPropertyAnimator scale/translate **na panelu** (260 ms) + fade overlay — działa na Mali-T720 | brak animacji panelu, tylko fade overlay |
| Dodatkowe widoki | brak | **ToolGrid** (custom ViewGroup z własnym onMeasure) + PanelTool w konstruktorze |
| Outline/clip | brak | guard `SDK>=30`: setClipToOutline + `o.setAlpha(0f)` (na API 28 nieaktywne) |

## 4. Co już WYKLUCZONE (nie powtarzać!)

1. **Gest swipe-up** — naprawiony w PR #17 (ACTION_DOWN konsumowany); panel faktycznie się otwiera, objaw jest renderowania/układu, nie otwierania.
2. **clipPath w PillSlider** — usunięty w PR #17 (zamieniony na drawRoundRect), objaw bez zmian.
3. **ViewPropertyAnimator na panelu jako przyczyna** — wykluczone: Launcher-CarPlay animuje panel dokładnie tak samo i działa na tym samym urządzeniu.
4. **Puste narzędzia / uszkodzone Prefs** — `PanelTool.load()` zawsze zwraca `defaults()` przy pustym/uszkodzonym zapisie; rail tworzy kafelki z twardej listy `{"wifi","bt","hot"}`, `find()` nie zwraca dla nich null.
5. **ToolGrid.onMeasure zwracający 0** — ma poprawny fallback (`w<=0 → dp(520)`); poza tym rail nie zależy od ToolGrid.
6. **Teoria „GPU nie umie rysować custom views"** — DockView, ClockCard, MapCard to custom Canvas views w tym samym oknie i działają.

⚠️ **Konflikt w historii, który trzeba znać:** PR #14 przywrócił `LAYER_TYPE_SOFTWARE` na API<30 i **użytkownik potwierdził działanie treści panelu**; PR #17 usunął tę warstwę („zbędna, DockView działa bez niej") i **panel znów jest pusty**. To najsilniejszy sygnał, że na tym konkretnym GPU/driverze wyrenderowanie NOWO-DODANEGO poddrzewa custom views pod aktywną animacją alpha jest zawodne, mimo że stare, osadzone od startu widoki działają. Samodzielnie potwierdzone na tym sprzęcie dziwactwa: RenderNode nie przebudowuje się poprawnie (PR #9), obrysowe wektory z przezroczystym wypełnieniem renderują się pusto (PR #21).

## 5. Plan naprawy — krok po kroku

### Krok 0 — Diagnostyka na urządzeniu (OBOWIĄZKOWY, najpierw!)

W `ConnectivityPanel.onAttachedToWindow` jest już log CC_PANEL (po 300 ms; loguje self + każdy child: w/h/alpha/vis). Zbuduj APK z CI, zainstaluj, otwórz panel i zbierz:

```
adb logcat -s CC_PANEL
```

**Interpretacja (drzewo decyzyjne):**
- **child w=0 lub h=0** (np. PillSlider w=0) → **przyczyna pomiaru** → wykonaj Krok 1 (weighty) — to potwierdzi, że weighty przy tym drzewie/pomiarze padają do zera.
- **w/h > 0, alpha=1, vis=V, a widoków nadal nie widać** → **przyczyna renderowania** → wykonaj Krok 3 (warstwa SOFTWARE) i Krok 2.
- Jeśli logu nie ma w ogóle — panel nie doczekał attach (rzadkie; sprawdź `self`).

### Krok 1 — Usunąć weighty, zastąpić stałymi rozmiarami dp (jak w Launcher-CarPlay)

Plik `app/src/main/java/pl/dock/widget/ConnectivityPanel.java`, metoda `build()`. Zamień LayoutParams z `width=0 + weight` na stałe dp (wartości przy 560 dp szerokości panelu; skaluj przez `dp()`):

- ~L149 `titleSpacer`: zostaw `(0, WRAP, 1f)` (to przypadków nie dotyczy, niewidoczny i tak).
- ~L188/203 suwaki `briSlider`/`volSlider`: `(0, dp(172), 1f)` → `(dp(150), dp(172))` (+zachowaj marginesy 6 dp; suma 150+150+12+marginesy ≤ szerokość `left`).
- ~L212 `nightBtn`: `(0, dp(58), 1f)` → `(dp(140), dp(58))`.
- ~L217 `modeSeg`: `(0, dp(58), 1.25f)` → `(dp(180), dp(58))`.
- ~L223 kolumna `left` w `main`: `(0, WRAP, 1f)` → `(dp(316), WRAP_CONTENT)` (316 = 300 suwaków/przycisków + marginesy).
- ~L248 kafelki rail: `(MATCH_PARENT, 0, 1f)` → `(MATCH_PARENT, dp(72))` (3×72+6dp marginesy = ~240 dp wysokości rail).
- `main` (~L260) i `grid` (~L285) zostają `MATCH_PARENT×WRAP_CONTENT` — to jest OK (MATCH_PARENT przy EXACTLY panelW daje konkretną szerokość, inaczej niż weight).

**Rationale:** lc używa wyłącznie stałych dp i działa; weighty są tu jedyną twardą różnicą architektoniczną w pomiarze. Naprawa jest tania i w 100% zgodna ze sprawdzonym wzorcem.

### Krok 2 — Dodać `setElevation(dp(12))` na panelu (jak w lc)

W konstruktorze `ConnectivityPanel`, po `setClickable(true)`:
```java
setElevation(dp(12));
```
Elevation wymusza własną warstwę/RenderNode panelu i sprawdzony w lc. ⚠️ Zastrzeżenie z PR #10: na tym GPU elevation bywa problematyczne — jeśli po tym kroku coś się pogorszy (np. zniknie tło panelu), usuń i kontynuuj Krok 3.

### Krok 3 — Jeśli w/h są OK, a treści nadal brak: przywrócić warstwę SOFTWARE na API < 30

To fix **empirycznie potwierdzony na tym urządzeniu** (PR #14). W konstruktorze `ConnectivityPanel`:
```java
if (android.os.Build.VERSION.SDK_INT < 30) {
    setLayerType(View.LAYER_TYPE_SOFTWARE, null);   // stałe, BEZ przełączania z powrotem na NONE
}
```
Ważne: NIE przełączać z powrotem na `LAYER_TYPE_NONE` po czasie (to czyszczenie bufora wgrywało się w koniec animacji otwarcia i psuło obraz — patrz PR #9). Panel rysuje się raz i rzadko się invalidate'uje, więc koszt CPU jest akceptowalny; w razie oporu suwaków można zoptymalizować później (np. `postInvalidateOnAnimation`).

### Krok 4 — Zmniejszyć powierzchnię ryzyka ToolGrid (opcjonalny, wykonać po 1–3)

ToolGrid + `PanelTool.init(a)` w konstruktorze to druga duża różnica vs lc. Jeśli panel po krokach 1–3 rysuje moduł bazowy, a siatka nie:
- dodawaj ToolGrid dopiero PO pierwszym layout panelu: `post(() -> { addView(grid, lp); })`,
- albo wyświetlaj siatkę tylko po wejściu w tryb edycji (`enterEditMode()`).

### Krok 5 — Sprawdzić okno zapasowe jako test A/B

`ConnectivityActivity` (osobne okno, root 0x66000000, DockView w środku) pokazuje ten sam panel w świeżej aktywności, bez fade overlay. Jeśli tam panel **działa** w nakładce launchera nie — problem siedzi w kontekście animacji alpha overlay (wtedy Krok 3 jest obligatoryjny: software-layer na `overlay` zamiast na panelu).

**❌ UCHYLONA 2026-11-06 (v1.0.255):** poprzednia notatka opierała się na błędnej
teorii („fade psuł treść”). Użytkownik potwierdził na urządzeniu, że objaw to
**tło okna + pigułka „Edytuj" widoczne, żaden custom Canvas View nie** — dokładnie
stan sprzed PR #14. Ustalenie na podstawie gita (commit b21e953):

- **Potwierdzona przez użytkownika** konfiguracja (PR #14): warstwa SOFTWARE
  **TYLKO na ConnectivityPanel** + **pełna sekwencja wejścia**: fade overlayu
  (220ms/0ms przy reduceMotion) + animacja panelu (scale 0.92→1, translateY
  40→0, Overshoot) + `withEndAction` z **`panel.requestLayout()` +
  `panel.invalidate()` + `overlay.invalidate()`** — to jest mechanizm naprawczy
  („pierwszy pomiar panelu w animowanej overlay potrafił wyjść 0×0 i treść nigdy
  się nie rysowała”).
- **Regresje, które to zepsuły:** (1) warstwa SOFTWARE na overlayu — PR #46
  (commit 2907c5e): dwie zagnieżdżone warstwy na Mali-T720 nie renderują treści,
  usunięta; (2) usunięcie animacji wejścia panelu i withEndAction — commit 73de241:
  przywrócone 1:1.
- ⚠️ **Nie usuwać animacji wejścia ani withEndAction** i **nie dodawać warstwy
  na overlayu**. Pilnują testy `overlayNieMaWlasnejWarstwy` i
  `overlayZachowujePotwierdzonaSekwencjeWejscia`.

## 6. Weryfikacja (kolejność obowiązkowa)

1. CI: `gradle test` + `assembleDebug` zielone (testy `OverlayTriggerTest`, `withoutBaseTools` nie mogą paść).
2. Zainstalować release na tabletce, otworzyć panel z HEADER doka **i** gestem swipe-up — w obu przypadkach pełny moduł bazowy: 2 suwaki, Night Shift, tryb wyglądu, 3 kafelki Wi-Fi/BT/hotspot, siatka narzędzi (domyślnie rotate/mute/loc) lub hint.
3. `adb logcat -s CC_PANEL`: wszystkie dzieci w>0, h>0, alpha=1, vis=0.
4. Test interakcji: przeciągnięcie suwaków zmienia jasność/głość, „Edytuj"/„Gotowane" działa, long-press suwaka = suwak ciepła, klik kafelka Wi-Fi działa.
5. Test powtarzalności: zamknij/otwórz panel 10× (RenderNode na Mali-T720 potrafi się nie przebudować — patrz PR #9; bug może być raz na kilka otwarć).
6. Test motywów: dzień i noc.

## 7. Czego NIE robić

- Nie przywracać `setClipToOutline` bez guardu `SDK>=30` i wymiarów >0 (PR #9: obcinało panel do 0×0 na stałe).
- Nie dokładać animacji skali/pivot na panel „dla ładniejszego efektu" bez testu na urządzeniu — historia (#9→#14→#17) to seria łatek wzajemnie się znoszących; każda zmiana renderowania tego panelu MUSI przejść test z pkt. 6.5 (10× open/close).
- Nie opierać logiki na `Build.VERSION.RELEASE` (sfalszowane na urządzeniu) — tylko `SDK_INT`.
- Nie wracać do teorii „GPU nie umie custom views" ani „martwy gest" — wykluczone (sekcja 4).

## 8. Ścieżki plików

- `app/src/main/java/pl/dock/widget/ConnectivityPanel.java` — build(), konstruktor, CC_PANEL log
- `app/src/main/java/pl/dock/widget/LauncherActivity.java` — showOverlayAt() ~1441, hideOverlay()
- `app/src/main/java/pl/dock/widget/ToolGrid.java` — onMeasure/onLayout (poprawne, nie ruszać bez potrzeby)
- `app/src/main/java/pl/dock/widget/PanelTool.java` — find/load/defaults (poprawne)
- `app/src/main/java/pl/dock/widget/OverlayTrigger.java` — pozycjonowanie panelu (z testami)
- Referencja działająca: `balladyrytual9z/Launcher-CarPlay` → `ConnectivityPanel.java` (stałe dp + setElevation + animacja panelu działa na tym samym urządzeniu)
