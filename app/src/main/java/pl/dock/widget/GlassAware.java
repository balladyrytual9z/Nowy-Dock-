package pl.dock.widget;

/**
 * Widzety, ktore potrafia rysowac sie w trybie "szkla" - przezroczysta karta
 * (lekki ton + obwodka zamiast nieprzezroczystego wypelnienia). Uzywane na
 * stronie widzetow (WidgetPage), zeby tlo - tapeta - przyswitywalo jak w iOS.
 * Ekran glowny zostaje z klasycznymi, nieprzezroczystymi kartami.
 */
public interface GlassAware {
    void setGlass(boolean glass);
}