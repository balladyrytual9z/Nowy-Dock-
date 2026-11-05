Poniżej znajduje się specyfikacja techniczna tabletu ESSGOO 9" na podstawie informacji, które podałeś wcześniej oraz danych ogólnych tego modelu. Uwaga: poniższe dane dotyczące pamięci, procesora i ekranu pochodzą z Twojego wcześniejszego opisu urządzenia; informacje takie jak GPS, Bluetooth czy Wi-Fi uzupełniono na podstawie publicznych specyfikacji tej samej serii produktów ESSGOO.

---

SPECYFIKACJA TECHNICZNA TABLETU ESSGOO 9"

Identyfikacja urządzenia
Producent: alps
Model / Board: Essgoop3_bsp
Hardware: Essgoo
Brand / Device / Product: alps / Essgoop3_bsp / full_Essgoop3_bsp
Build: AJ_2025.10.11.09_3_2424
Build ID: full_Essgoop3_bsp-user 9 PPR1.180610.011 eng.publis.20251011.091728 release-keys
Fingerprint: alps/full_Essgoop3_bsp/Essgoop3_bsp:9/PPR1.180610.011/eng.publis.20251011.091728:user/release-keys
MCU: CVD1810-WJ_24.09.21_399
CAN: 1.0.1.20250912
Serial: 0000000000000000
Android ID: 75677bfd12614150
Device ID: dc011b202020202020ffa82558a8bc4b

System
Wyświetlana wersja: Android 15.0 (SFALSZOWANA)
PRAWDZIWA wersja: Android 9 (Pie), API 28
Codename: REL
Kernel: 4.9.117+, armv7l
Security Patch: 2021-09-05
Java Runtime: Android Runtime 0.9
Java VM: ART 2.1.0
Root: NIE
Google Play Services: TAK (Play Store działa)

CPU / GPU
SoC: MediaTek MT8163
CPU: 4× ARM Cortex-A53 @ 600–1500 MHz
Architektura: ARMv7 (32-bit), ABI: armeabi-v7a, armeabi
Instruction Set: 64-bit ARMv8-A (pracuje w 32-bit)
CPU Revision: r0p3, Implementer 0x41 (ARM)
BogoMIPS: 32.53
Governor: interactive
Features: half thumb, fastmult, vfp, edsp, neon, vfpv3, tls, vfpv4, idiva, idivt, vfpd32, crc32
GPU: Mali-T720, OpenGL ES 3.2, OpenCL 1.2
Compute Units: 2
GPU Clock: 5 MHz (raportowane)
OpenCL Lib: /system/vendor/lib/libOpenCL.so

Pamięć
RAM: 4 GB (MemTotal 4194304 kB)
MemFree: ~2 GB
MemAvailable: ~2.7 GB
Java VM Heap Size: 128 MB
Storage: 44.80 GB (wolne ~50.81 GB łącznie)
External: 44.80 GB (wolne ~2045 MB)

Wyświetlacz
Rozmiar: 9"
Rozdzielczość: 1280×720
Typ: IPS, dotyk pojemnościowy
Kontroler dotyku: GT9xx_1080x600
Kąt widzenia: szeroki (typowy dla IPS)

Czujniki
ACCELEROMETER: TAK (jedyny)
Thermal: NIE
Żyroskop: NIE
Magnetometr: NIE
Barometr: NIE
Czujnik światła: NIE

Kamera
System fałszywie raportuje "Front-Facing Camera 0.5 MP (800×600)"
Brak fizycznej kamery wbudowanej
Zewnętrzna kamera DVR przez USB (szyba, własny mikrofon, działa w tle)
Aplikacja DVR: com.ankai.cardvr ("HD DVR" v1.0.0, code 50000, Target SDK 19, Min SDK 16)

Łączność
WiFi: TAK (WiFi Direct: TAK, 5 GHz: TAK, WiFi Aware: NIE)
WiFi CHIP: MT6571, FW vb.1.8c
WiFi MAC: randomized
GPS: TAK (wbudowany moduł GPS z anteną)
Bluetooth: 4.0+ (BLE + Classic), AVRCP 1.4, Max BT audio: 5
USB0 (4-pin): kamera DVR
USB1 (6-pin): CarPlay / dane
USB protocol: usb2_0, OTG/Host: TAK
USB tethering: NIE (brak RNDIS)

Aplikacje systemowe
CarPlay: com.zjinnova.zlink (ZLINK5 v5.4.40, code 50440, Target SDK 21, Min SDK 21)
Equalizer/DSP: com.hcn.autoeq (DSP, mt8163_2025_09_15_16_26_12, code 2077, Target SDK 31, Min SDK 27)
Kamera/DVR: com.ankai.cardvr (HD DVR v1.0.0, code 50000, Target SDK 19, Min SDK 16)

Ukryte menu – hasła
Factory Settings: data YYYY.MM.DD (np. 2026.10.05)
Developer Options: 7× klik w Build Number
Engineer Mode: dostępne z Factory Settings

Cykl zasilania
Tablet: stałe 12V + osobny sygnał ACC
Zapłon ON: ekran ON, audio ON, wznowienie ~1–2 s
Zapłon OFF: ekran OFF, audio OFF, uśpienie
Po 12 h bez ACC CAN: pełne wyłączenie
Po 12 h → zimny boot: ~15–30 s

Co działa / nie działa
Działa: CarPlay wireless, GPS, Google Play, DVR, Equalizer
Nie działa: CarPlay wired (plan docelowy: kabel MFi), USB tethering, Hotspot iPhone + CarPlay wireless razem (konflikt WiFi)

Ograniczenia sprzętowe

1. ARMv7 (32-bit) → biblioteki TYLKO armeabi-v7a
2. Java Heap 128 MB → ekstremalna oszczędność pamięci
3. Android 9 (API 28) → NIE używać API 29+
4. Mali-T720 → minimalne animacje
5. Brak roota
6. Brak RNDIS → tethering USB nie działa
7. Hotspot iPhone + CarPlay wireless = konflikt WiFi
8. Brak czujnika światła → auto dark/light przez sunset/sunrise (algorytm NOAA)
9. Brak fizycznej kamery wbudowanej → tylko zewnętrzna DVR
10. Kamera DVR w tle (osobny mikrofon, osobny USB)
11. Zlink wyłącza BT przy starcie → wymaga BT Recovery