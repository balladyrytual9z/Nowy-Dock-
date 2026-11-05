# PATCH: `ConnectivityPanel` — pusty panel łączności (ESSGOO / MT8163, Android 9)

**Plik:** `app/src/main/java/pl/dock/widget/ConnectivityPanel.java`
**Gałąź:** `fix/panel-lacznosci-patch` (od `main` = v1.0.188) · **Status:** ✅ ZASTOSOWANY i zbudowany przez CI
**Diagnoza źródłowa:** [`NAPRAWA-PANEL-LACZNOSCI.md`](NAPRAWA-PANEL-LACZNOSCI.md) · **Urządzenie:** [`../../docs/urzadzenie-essgoo-9.md`](../../docs/urzadzenie-essgoo-9.md)

Objaw: po otwarciu Centrum łączności widać tylko tło panelu i pigułkę „Edytuj".
Nie rysuje się **żaden** custom `Canvas` View wewnątrz panelu (PillSlider ×2,
NightShiftButton, ModeSegment, kafelki rail Wi-Fi/BT/hotspot, ToolGrid).

---

## 1. Przyczyna (root cause)

Zmierzone rozmiary dzieci są poprawne (`CC_PANEL` w logcat: `w>0, h>0, alpha=1, vis=0`),
więc **to nie jest błąd układu** — to **renderowanie**. Na tym GPU/driverze
(Mali-T720, Android 9 / API 28) nowo dodane poddrzewo custom views **nie jest
odtwarzane z RenderNode**: tło panelu (`GradientDrawable` z `Ui.popupBg`)
i `TextView` z frameworka rysują się, a wszystkie własne widoki `Canvas` — nie.

Dowód rozstrzygający (historia tego repo):
**wersja z warstwą `LAYER_TYPE_SOFTWARE` działała** (użytkownik potwierdził pełną
treść panelu), a **wersja bez niej jest pusta** — mimo identycznego kodu pomiaru.
Typ warstwy nie ma żadnego wpływu na `onMeasure`/`onLayout`, więc skoro zmiana
samej warstwy przywraca treść, przyczyną jest odtwarzanie DisplayList, nie layout.
Warstwa SOFTWARE omija problem, bo panel renderuje się do `Bitmap`, a bitmapę
rysuje standardowa, sprawna ścieżka frameworka (ta sama, która rysuje szkło
z rozmytego zrzutu ekranu).

## 2. Patch (dokładny diff)

W konstruktorze `ConnectivityPanel(Activity a, boolean night, Host host)`,
po `setClickable(true);` — **jedyna** zmiana w tym pliku:

```java
        // Panel na API >= 30 rysuje sie normalnie (hardware). Na starym GPU
        // tabletu (ESSGOO / MT8163, Mali-T720, realny Android 9 = API 28) tresc
        // panelu - suwaki PillSlider, NightShiftButton, ModeSegment i kafelki
        // rail - NIE rysuje sie bez wlasnej warstwy: widoczne sa tylko tlo panelu
        // i TextView z frameworka. Zmierzone rozmiary sa poprawne, wiec to nie
        // uklad, a odtwarzanie nowo dodanego poddrzewa custom views z RenderNode.
        //
        // Warstwa SOFTWARE na API < 30 jest na tym urzadzeniu POTWIERDZONA
        // (dziala z nia: widoczna pelna tresc panelu). Ustawiamy ja RAZ i NIE
        // przelaczamy z powrotem na LAYER_TYPE_NONE - samo przelaczenie czyscilo
        // bufor w trakcie animacji otwarcia i panel gasl (PR #9). Panel
        // przerysowuje sie dopiero przy dotknieciu, wiec koszt CPU jest znikomy.
        if (android.os.Build.VERSION.SDK_INT < 30) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
```

Blok `if (SDK_INT >= 30) { setOutlineProvider(...); setClipToOutline(true); }`
**zostaje bez zmian** (na API 28 i tak nieaktywny — służy jako zabezpieczenie na
przyszłość, z guardem na wymiary > 0).

> Uwaga na komentarze: `AGENTS.md` § 6 wymaga **ASCII** w komentarzach kodu
> (bez polskich znaków) — dlatego nowy komentarz jest bez diakrytyków.

## 3. Czego świadomie NIE zrobiono (i dlaczego)

| Krok z `NAPRAWA-PANEL-LACZNOSCI.md` | Decyzja | Uzasadnienie |
| --- | --- | --- |
| Krok 1 — weighty → stałe `dp` | ❌ pominięte | Typ warstwy nie wpływa na pomiar, a z warstwą SOFTWARE panel rysował się **poprawnie**, czyli pomiar jest OK. Wymiana weightów na stałe `dp` (316 dp kolumna) zawęża panel do ≥ ~584 dp szerokości okna — to nowe ryzyko bez podstawy. |
| Krok 2 — `setElevation(dp(12))` | ❌ pominięte | `bc46e18` **usunął** elevation, bo na Mali-T720 warstwa cienia renderowała pusty bufor → panel był przezroczysty. Dodawanie jej z powrotem to proszenie się o powrót tamtego objawu. |
| Krok 4 — odroczenie `ToolGrid` | ❌ pominięte | Nie ma dowodu, że siatka narzędzi jest problemem; zmiana zwiększa zakres patcha. |
| Krok 3 — warstwa SOFTWARE (stała) | ✅ **zastosowane** | Jedyny wariant potwierdzony empirycznie na tym urządzeniu, i to dwukrotnie. |

Świadomie **odrzucona** jest też poprzednia próba patcha z gałęzi
`fix/panel-lacznosci-empty-render`, która dokładała `setElevation` i wymieniała
weighty — patrz § 4.

## 4. 🔴 Incydent: nie używać gałęzi `fix/panel-lacznosci-empty-render`

Na tej gałęzi plik `ConnectivityPanel.java` ma **8 bajtów** — jest w nim dosłownie
tekst `see_file` (pozostałość nieudanego zapisu). 1423 linie kodu zostały
skasowane, commit nadal twierdzi „applied changes". Gałąź nie ma PR-a i nigdy nie
przeszła CI. Każdy, kto zmergowałby ją do `main`, **wysadziłby całą aplikację**.
Do usunięcia (gałąź zdalna, bez PR-a).

## 5. Weryfikacja

**CI (`main` ← PR):** `gradle test` + `clean assembleDebug -Pvc=<run>` zielone.

**Na urządzeniu (kolejność obowiązkowa):**

1. Zainstaluj APK z artefaktu CI (ten sam keystore = instalacja jako UPDATE).
2. Otwórz panel z górnej strefy doka **i** gestem swipe-up — w obu przypadkach musi być
   pełny moduł bazowy: 2 suwaki, Night Shift, tryb wyglądu, 3 kafelki Wi-Fi/BT/hotspot,
   siatka narzędzi lub jej podpowiedź.
3. `adb logcat -s CC_PANEL` → każde dziecko `w>0, h>0, alpha=1, vis=0`.
4. Interakcje: przeciąganie suwaków zmienia jasność/głośność, „Edytuj"/„Gotowe" działa,
   long-press suwaka jasności = suwak ciepła, klik kafelka Wi-Fi przełącza Wi-Fi.
5. **Test powtarzalności: 10× zamknij/otwórz panel** (RenderNode na Mali-T720 potrafi
   się nie przebudować — bug może wystąpić raz na kilka otwarć).
6. Motyw dzienny i nocny.
7. Sprawdź płynność przeciągania suwaka pod warstwą SOFTWARE. Jeśli zauważysz wyraźne
   zacięcia — zgłoś, jest miejsce na optymalizację (`postInvalidateOnAnimation`),
   **nie** przez usuwanie warstwy.

## 6. Czego nie robić

- **Nie** przelaczać warstwy na `LAYER_TYPE_NONE` po czasie — czyszczenie bufora w trakcie
  animacji otwarcia gasiło panel (PR #9, `bc46e18`).
- **Nie** dodawać `setElevation` na panelu (fix #10 — przezroczysty panel).
- **Nie** dodawać clipowania na API < 30 (`setClipToOutline` bez guardu obcinał panel do 0×0).
- **Nie** opierać logiki na `Build.VERSION.RELEASE` (sfalszowane „15.0"), wyłącznie `SDK_INT`.
- **Nie** wskrzeszać gałęzi `fix/panel-lacznosci-empty-render` (§ 4).

## 7. Rollback

Jedna linia: usuń blok `if (SDK_INT < 30) setLayerType(...)` z konstruktora.
Stan wyjściowy = `main` @ v1.0.188 (panel pusty na tym tablecie).

## 8. Historia (dlaczego to trwało tak długo)

| Commit / PR | Co zrobił | Efekt |
| --- | --- | --- |
| `bc46e18` | dodał SOFTWARE + usunął elevation | panel przezroczysty → cofnięte |
| `b21e953` (PR #14) | przywrócił SOFTWARE na API < 30 | ✅ treść panelu widoczna (potwierdzone) |
| `fdda983` (PR #9) | usunął SOFTWARE, naprawił clip | panel 0×0 — dwie łaty się zniosły |
| `7d95de1` (gałąź `fix/panel-lacznosci-regresja`, **niezmergowana**) | usunął SOFTWARE + dodał `ConnectivityPanelSourceGuardTest` | test-strażnik istnieje tylko na tej gałęzi, **na `main` go nie ma** |
| `73de241` (PR #17) | usunął SOFTWARE („root cause") | 🔴 regresja: panel znów pusty |
| **ten patch** | statyczna warstwa SOFTWARE na API < 30 | do potwierdzenia na urządzeniu |
