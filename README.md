# KosaVisuals (Fabric 1.21.4, client-side)

Prawy Shift otwiera menu z kolumnami modułów (przeciągaj panele za nagłówek, PPM / strzałka = ustawienia, pole wyszukiwania).
**L** w menu otwiera edytor HUD. Przycisk **Configi** jest pod wyszukiwarką.

## Moduły (kategorie: PVP, Animations, Sounds, World, HUD, Client)
- **PVP:** **Hitbox Customizer**, **Target ESP** (pierścień, okrąg pod nogami albo **Duszki** – dwa kółka z czarnym środkiem i wyraźną
  smugą o regulowanej grubości), **Hit Color** (zmienia kolor mignięcia trafionego gracza / moba na cały model, z suwakiem mocy),
  **Kill Effect** (tryby: Duch, Piorun, Duch + piorun, **Kosiarz** – postać z kosą pojawia się po prawej
  i wybija zabitego gracza w lewo; czas ducha, wysokość i prędkość wyrzutu, kolory), **Totem POP** (własny dźwięk zbicia totemu), **Damage Numbers** (rozmiar, kolor).
- **Animations:**
  - **Swing Animations** – pozycja X / Y / Z i **Scale** ręki albo miecza w pierwszej osobie; przełącznik **Only swords**
    (włączony: tylko gdy trzymasz miecz, wyłączony: wszystko, także pusta ręka).
  - **Hurt Cam** – tryb „Bez trzęsienia” wyłącza trzęsienie kamery po otrzymaniu obrażeń. Przycisk **Testuj** zamyka menu,
    a duch-gracz uderza Cię raz (tylko wizualnie), żeby było widać różnicę.
  - **Knife** – zamiast miecza w ręce (pierwsza osoba) nóż z CS2: **Karambit**, **Bayonet**, **Butterfly** w wykończeniach
    Stal, **Sapphire** (niebieski), **Ruby** (czerwony), **Emerald** (zielony), Black Pearl. Wybierasz, który miecz zastępuje
    (domyślnie wszystkie miecze) i rozmiar. Pozycję noża ustawisz w Swing Animations.
- **Sounds:** Hit Sound, Kill Sound, Click Sound – m.in. **Mau (mały kotek)**, Mau (kot), Miau, Mrrau.
  Głośność do 300% (głośniejsze ustawienia nakładają kopie dźwięku). Kill Sound ma przełącznik **Moby**.
  Własne .wav: przeciągnij na okno gry przy otwartym menu albo wrzuć do `config/holovis/sounds`.
- **World:** Custom Sky, **Motion Blur** (0–200), **Color Saturation** (korekcja kolorów: style + suwaki; przełącznik
  **Na mobach**), **Halo** (aureola nad głową, domyślnie złota, dowolny kolor, regulowana wysokość; siebie widzisz w F5), **Aspect Ratio**
  (4:3, 5:4, 16:10, 3:2, 1:1, 21:9, 16:9 albo własne – obraz rozciągnięty jak w CS2), **FullBright** (jasność 0–100%,
  w jaskini i nocą widać wszystko), **Jump Circles** (wzór pod nogami przy skoku: koło, sześciokąt, gwiazda, kwadrat,
  trójkąt, podwójne koło, fala, promienie), **World Particles** (spadające gwiazdki, śnieg, płatki albo świetliki;
  na ziemi płynnie topnieją).
- **HUD:** Watermark (nazwa, ping, FPS – sam tekst z cieniem, opcjonalne tło), **Koordynaty** (osobny element), **Potions**
  (aktywne efekty z ikonami z gry, poziomem i czasem), **NameTag** (własny napis z ikonką nad głową: 1–2 linie, rozmiar, wysokość, czcionka, kolor;
  ikonka „Własna” to Twój obrazek `config/holovis/nametag_icon.png`, max 12 KB),
  **Armor Status** (kafelki z własnymi ikonkami zbroi w kolorze materiału, % i pasek wytrzymałości), **TargetHUD** (zostaje 10 s po uderzeniu; opcja **Track player** –
  karta wisi nad graczem), Cooldowns.
- **Client:** Akcent GUI, **Rozmycie klienta** (0–100), **Spotify** (pasek z nazwą piosenki; po najechaniu w czacie karta
  z tekstem i suwakiem głośności), **YT Music** (to samo dla YouTube Music w przeglądarce: Brave, Opera GX, Chrome, Edge,
  Firefox; Windows 10/11) oraz **Kosa Nametag** (neonowe fioletowe „K” przed nickiem, nad głową
  i w TAB; mod działa tylko po stronie klienta, więc „K” widzisz u siebie i u bota UwU_Lizaczek, nie u innych graczy).

Wybór koloru: kliknij wiersz z kolorem – obok panelu otwiera się okienko z kółkiem, paletą i suwakami nasycenia i jasności.

## Edytor HUD (klawisz L)
- Przeciągnij element, żeby go przesunąć.
- Kropki w rogach zmieniają rozmiar (albo kółko myszy nad elementem). Dotyczy Watermarku, Koordynatów, Potions, Spotify, TargetHUD i Cooldowns.
- Po najechaniu na **Spotify** pojawia się karta z tekstem piosenki – jej rozmiar zmienia prawy dolny róg karty.
- `R` – reset pozycji i rozmiarów, `L` / `ESC` – powrót.

## Configi
Przycisk **Configi** otwiera okno z profilami: nazwa + **Zapisz**, zakładki Ostatnie / Najczęściej / Wszystkie, gwiazdka
(ulubione), **Kod** (kopiuje krótki kod profilu), **x** (usuwa), a na dole **Cofnij**, **Kopiuj kod**, **Wklej kod**, **Domyślne**.
Kod `KV1:...` zapisuje tylko to, co zmieniłeś względem ustawień domyślnych, więc jest krótki. Stare kody `HV1:...` nadal działają.

## Konto i czat
Przycisk w prawym górnym rogu menu: tworzysz konto (nazwa + główka Twojego skina). Konta działają online (baza Firebase):
- konto jest przypisane do Twojego konta Minecraft – skopiowany config nie przenosi go na innego gracza,
- zakładka **Na serwerze** – gracze KosaVisuals na tym samym serwerze, przycisk **+** (dodaj do znajomych) i **Wiadomość**,
- zakładka **Znajomi** – dodawanie po nazwie konta albo nicku; widać, czy znajomy jest online i na jakim serwerze,
  i można do niego pisać z dowolnego serwera,
- nowa wiadomość pojawia się w prawym górnym rogu; po otwarciu czatu (T) są przyciski **Przeczytane**, **Ignoruj** i
  **Odpowiedz** (szybka odpowiedź). Nadawca widzi pod wiadomością niebieskie „Przeczytano”,
- **Właściciel kod** odblokowuje konto właściciela: rangi **VIP**, **FRIEND** i **V-WŁAŚCICIEL** (te same uprawnienia),
  lista funkcji, podgląd wszystkich czatów i zakładka **Configi** – podgląd configów innych graczy (Kopiuj do schowka
  albo Zapisz jako profil; zmieniać ich nie można). Rangi i odznakę K widzą wszyscy gracze KosaVisuals.

Uwaga: właściciele mogą czytać czaty i widzą Twój config, a dane bazy są zapisane w modzie – nie wysyłaj przez czat niczego poufnego.

## Komendy
- `/kosa optymalizacja` – okno optymalizacji: poziom Medium / Hard / Ultra / Extreme, podgląd, co zabiera FPS, przyciski
  **Optymalizuj** i **Przywróć** (przywraca ustawienia Minecrafta i efekty sprzed pierwszej optymalizacji, także po restarcie); na Windows opcjonalnie wysoki priorytet procesu Minecrafta
- `/kosa diag` – pokazuje, które zaczepy w grze działają (Swing, Knife, Hurt Cam, Aspect Ratio, FullBright, Totem POP)
- `/kosa` albo `/kosa toggle` – otwiera menu
- `/kosa clickgui <klawisz>` – ustawia klawisz otwierający menu, np. `/kosa clickgui Insert` (bez klawisza pokazuje obecny)
- `/kosa config` – okno z profilami
- `/kosa config load <kod albo nazwa profilu>` – wczytuje config
- `/kosa config save <nazwa>`, `/kosa config copy`, `/kosa config list`
- `/kosa ghost` / `/kosa ghost remove` – bot „UwU_Lizaczek” widoczny tylko dla Ciebie (obrażenia jak u gracza bez zbroi: pięść 1, miecz netherytowy 8)
- `/kosa killtest` – podgląd Kill Effectu

## Spotify
Windows 10/11: odczyt i sterowanie przez systemową sesję multimediów (bez logowania).
Otwórz czat i najedź na element Spotify: tekst piosenki (lrclib.net), pasek przewijania i przyciski.
Skróty (Sterowanie → KosaVisuals): `[` poprzedni, `]` następny, `\` pauza/play.

## Uwaga przy aktualizacji
Plik moda nazywa się `KosaVisuals-1.9.0.jar`. Usuń ze `mods` starsze `KosaVisuals-*.jar` / `holovis-*.jar`.
