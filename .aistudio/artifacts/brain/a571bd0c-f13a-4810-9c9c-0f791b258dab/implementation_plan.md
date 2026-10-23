# MetaForge — Offline Metadata Inspector & Sanitizer

Nativní Android aplikace pro bezpečné lokální zkoumání, filtrování, úpravu a sanitizaci metadat multimediálních souborů (obrázky, audio, video) bez nutnosti připojení k internetu, účtů nebo cloudu.

---

## Uživatelská rozhodnutí & Potvrzené volby

> [!IMPORTANT]
> Na základě vašich odpovědí v úvodním dotazování byl plán přizpůsoben následujícím prioritám:
> 1. **Rozsah multimédií pro MVP:** Obrázky (JPEG, PNG, WebP), Audio (MP3, FLAC) a základní kontejnerové video (MP4).
> 2. **Audio engine:** Zaměření na spolehlivou knihovnu pro čtení i zápis ID3v1/ID3v2 a Vorbis Comments (`net.jthink:jaudiotagger` s Apache/LGPL licencí a čistě Java parserem).
> 3. **Dokumenty (PDF/Office):** Vynechány z MVP, aby byla zajištěna maximální robustnost, nulové riziko neúplné sanitizace komplexních objektových stromů a minimální velikost APK.

---

## 1. Stručné pochopení produktu & Základní koncept

MetaForge je ryze lokální kapesní forenzní a privacy nástroj pro Android. Uživatel do aplikace načte soubor prostřednictvím systémového `Storage Access Framework` (SAF). Aplikace provede:
1. **Identifikaci skutečného typu souboru** na základě magických bajtů v hlavičce (nikoli podle nespolehlivé přípony).
2. **Extrakci a kategorizaci metadat** do logických sekcí (Základní systémová data, Technické parametry médií, Exif/TIFF, GPS a lokace, ID3/Audio tagy, MP4 Boxy).
3. **Detekci citlivých údajů** (GPS souřadnice, jméno autora, sériová čísla zařízení, identifikační řetězce softwaru).
4. **Bezpečnou úpravu nebo aplikaci profilů čištění** (např. *Odstranit polohu*, *Odstranit osobní a autorská data*, *Vlastní výběr*).
5. **Diferenční analýzu (Diff & Validation):** Výsledek je zapsán do izolované dočasné pracovní kopie, znovu nezávisle přečten a porovnán s originálem. Originál zůstává 100% nedotčen.
6. **Export přes systémový dialog „Uložit jako“** nebo přímé sdílení očištěného souboru přes zabezpečený `FileProvider`.

---

## 2. Technická omezení, bezpečnostní model a rizika

### Bezpečnostní zásady zpracování
- **Žádné přepisování originálu na místě:** Zápis probíhá výhradně do dočasného souboru v `context.cacheDir/metaforge_work/`.
- **Zero-Trust k parserům:** Soubory jsou považovány za potenciálně poškozené nebo nepřátelské. Čtení probíhá v sandboxovaných IO Coroutine blocích s odchytáváním `OutOfMemoryError`, `CorruptGpsHeaderException`, `EOFException` atd.
- **Ochrana před DoS a přetečením paměti:**
  - Náhledy obrázků se dekódují s hardwarovým vzorkováním (`inSampleSize` / Subsampling) s limitem max 1024×1024 px.
  - Nikdy se nenačítá celý multimediální soubor (např. 2GB MP4) do operační paměti byte array.
  - SHA-256 kontrolní součet se počítá pouze na výslovné vyžádání uživatelem (streamovaným průchodem v chunkách 64 KB).
- **Strukturální integrita:**
  - Exif orientace (např. Tag 274 `Orientation`) se při čištění polohy a autora nemaže, aby nedošlo k nechtěnému otočení fotografie na výšku/šířku.
  - V metadatech se jasně odlišuje: *Systémová metadata* (velikost, čas poskytnutý SAF úložištěm) vs. *Vložená metadata* (DateTimeOriginal zapsaný v Exif).

---

## 3. Matice podpory formátů a operací (Capability Matrix)

| Formát / Skupina | Detekce typu (Magic Bytes) | Čtení metadat | Úprava hodnot | Bezpečné odstranění | Použitý engine | Známá omezení a poznámky |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **JPEG** | `FF D8 FF` | Exif, GPS, TIFF, XMP, IPTC | Ano (jednotlivé Exif tagy) | Ano (GPS, Autor, Kompletní Exif) | `androidx.exifinterface:exifinterface` | Bezeztrátová manipulace s Exif segmenty bez rekomprese JPEG pixelů. |
| **PNG** | `89 50 4E 47 0D 0A 1A 0A` | eXIf chunk, tEXt, zTXt, iTXt | Ano (eXIf) | Ano (eXIf, textové bloky) | `ExifInterface` + lehký PNG chunk stream writer | Strukturální chunky (IHDR, IDAT, IEND) zůstávají nedotčeny. |
| **WebP** | `52 49 46 46 ... 57 45 42 50` | EXIF chunk, XMP chunk | Ano (EXIF) | Ano (EXIF, XMP) | `ExifInterface` | Zachovává VP8/VP8L/VP8X hlavičky. |
| **MP3** | `49 44 33` nebo Sync `FF FB/F3` | ID3v1, ID3v2.2-v2.4 | Ano (Název, Umělec, Album, Rok, Komentář) | Ano (Odstranění tagů, vyčištění ID3v2) | `net.jthink:jaudiotagger` | Audio rámce zůstávají bitově identické, mění se pouze hlavička/patička. |
| **FLAC** | `66 4C 61 43` | Vorbis Comment, STREAMINFO | Ano (komentáře, tagy) | Ano (vyčištění volitelných bloků) | `net.jthink:jaudiotagger` | Zachovává STREAMINFO a SEEKTABLE. |
| **MP4 / M4A** | `ftyp` box v offsetu 4 | `moov.udta.meta.ilst` | Omezeně (standardní tagy) | Ano (odstranění metadatových atomů) | Vestavěný MP4 Box Streamer / `mp4parser` | Bezeztrátová úprava moov atomu bez rekomprese videa/audia. |
| **Neznámý soubor** | Libovolné | Základní SAF info, Magic Hex, SHA-256 | Ne (tlačítka deaktivována) | Ne (vysvětlen důvod) | Fallback Provider | Zobrazí hex signaturu a MIME typ, upozorní na nepodporovaný formát. |

---

## 4. Výběr knihoven & Licenční audit

1. **`androidx.exifinterface:exifinterface:1.4.0`**
   - *Účel:* Standardní, Googlem udržovaná knihovna pro Exif v JPEG, WebP, PNG, HEIF.
   - *Licence:* Apache 2.0 (vhodné pro distribuci).
   - *Důvod:* Nativní podpora streamů, minimální velikost (cca 160 KB), nulový dopad na RAM.
2. **`net.jthink:jaudiotagger:3.0.1`**
   - *Účel:* Manipulace s audio tagy (MP3 ID3v1/v2, FLAC Vorbis, OGG).
   - *Licence:* LGPL v3 / Apache 2.0 části.
   - *Důvod:* Osvědčený čistě javovský nástroj, nepotřebuje nativní C++ NDK knihovny, šetří velikost APK oproti FFmpeg o 30+ MB.
3. **`org.mp4parser:isoparser:1.9.41`** (nebo interní odlehčený MP4 atom parser)
   - *Účel:* Procházení a bezeztrátový zápis atomů v MP4/M4A kontejnerech.
   - *Licence:* Apache 2.0.
   - *Důvod:* Čistě Java implementace pro úpravu ISO-14496 bez závislosti na obřím FFmpeg.
4. **`androidx.datastore:datastore-preferences:1.1.7`**
   - *Účel:* Perzistence nastavení uživatele (téma, AMOLED režim, výchozí profily čištění).
   - *Licence:* Apache 2.0.

> [!NOTE]
> Záměrně se vyhneme ExifToolu a FFmpeg: tyto nástroje vyžadují kompilaci binárek pro 4 různé architektury (arm64-v8a, armeabi-v7a, x86, x86_64), což by nafouklo APK o desítky megabajtů a zkomplikovalo běh na Androidu. Naše čisté řešení udrží velikost release APK pod 8 MB.

---

## 5. Uživatelské rozhraní & Vizuální styl (UI/UX)

### Barevná paleta a AMOLED téma
- **AMOLED Dark (Výchozí):** Pozadí `#000000`, Surface `#121212`, SurfaceVariant `#1E1E1E`.
- **Akcentní barva (Cyber/Forge Teal):** `#00D2B4` (primární), `#80FFE8` (kontejner).
- **Varování a citlivá data:** Amber `#FFB74D` (citlivé info), Red `#FF5252` (odstranění).
- **Bezpečná data / Úspěch:** Emerald `#4CAF50`.
- **Typografie:** Monospace pro technické názvy tagů, hex signatury a hashe (`FontFamily.Monospace`); čistý systémový font pro popisky.
- **Dotykové cíle:** Všechny klikací prvky splňují minimální rozměr 48×48 dp (`minimumInteractiveComponentSize`).

### Popis klíčových obrazovek

```
┌──────────────────────────────────────────────┐
│ [≡] MetaForge                  [AMOLED] [⚙] │
├──────────────────────────────────────────────┤
│                                              │
│               ┌──────────────┐               │
│               │   [  ▲  ]    │               │
│               │ VYBRAT SOUBOR│               │
│               └──────────────┘               │
│                                              │
│      🔒 100% lokální zpracování offline       │
│      🛡️ Žádná data neopouštějí zařízení     │
│                                              │
│  Poslední zpracovaný:                        │
│  ┌────────────────────────────────────────┐  │
│  │ 📷 IMG_20261023.jpg (3.2 MB)           │  │
│  │ Status: Očištěno (GPS a Autor smazány) │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

1. **Domovská obrazovka (Home):**
   - Výrazná centrální zóna pro výběr souboru přes systémový picker.
   - Odznaky transparentnosti (100% offline, bez účtu).
   - Rychlý přístup k nastavení a matici podpory.
2. **Detail metadat (Inspector):**
   - Hlavička: Název souboru, formát (zjištěný vs. deklarovaný), velikost, náhled.
   - Vyhledávací pole a filtry: `Vše`, `Citlivé (GPS/Autor)`, `Technické`, `Strukturální`.
   - Přepínač zobrazení: Uživatelsky přívětivé názvy vs. Syrové Exif/ID3 tagy.
   - Rychlá tlačítka akcí: `Kopírovat vše do JSON`, `Kopírovat do textu`, `Čistit soubor`.
3. **Konfigurace čištění (Sanitize Screen):**
   - Rychlé profily:
     - 📍 **Odstranit polohu:** Maže GPSLatitude, GPSLongitude, GPSAltitude, GPSTimestamp apod.
     - 👤 **Odstranit osobní a autorská data:** Maže Artist, Copyright, Camera Model, Serial Number, Software.
     - 🧹 **Maximální bezpečné vyčištění:** Odstraní všechna volitelná metadata kromě strukturálních (ponechá rozměry a orientaci).
   - Interaktivní seznam přepínačů pro individuální výběr polí.
4. **Náhled změn & Porovnání (Diff View):**
   - Dvousloupcový nebo barevně odlišený přehled: *Původní hodnota* → *Nová hodnota / [Smazáno]*.
   - Tlačítko `Potvrdit a vygenerovat kopii`.
5. **Výsledek a Export (Result Screen):**
   - Výsledek nezávislého re-auditu: Potvrzení, které tagy byly úspěšně odstraněny a které zůstaly zachovány.
   - Tlačítka `Uložit do zařízení` (SAF CreateDocument) a `Sdílet soubor` (FileProvider).
6. **Nastavení (Settings):**
   - Přepínač témat (AMOLED černá, Systémové tmavé, Světlé).
   - Správa pracovní mezipaměti (okamžité vymazání dočasných souborů).
   - Informace o licencích a matice podpory formátů.

---

## 6. Architektura a datový tok

Aplikace striktně využívá jednosměrný datový tok (UDF) v souladu s doporučeními Android Jetpack:

```
┌─────────────────┐       URI        ┌───────────────────────┐
│ System SAF      ├─────────────────►│ FileAccessRepository  │
│ Document Picker │                  │ (Čtení do cache kopie)│
└─────────────────┘                  └──────────┬────────────┘
                                                │ File
                                     ┌──────────▼────────────┐
                                     │ FormatDetector        │
                                     │ (Magic Bytes Analyzer)│
                                     └──────────┬────────────┘
                                                │ Format + Capabilities
                                     ┌──────────▼────────────┐
                                     │ MetadataEngineRegistry│
                                     │ (Exif, Audio, MP4)    │
                                     └──────────┬────────────┘
                                                │ MetadataPayload
                                     ┌──────────▼────────────┐
                                     │ MainViewModel         │
                                     │ (UI State & Events)   │
                                     └──────────┬────────────┘
                                                │ StateFlow<UiState>
                                     ┌──────────▼────────────┐
                                     │ Jetpack Compose UI    │
                                     │ (AMOLED / Material 3) │
                                     └───────────────────────┘
```

### Klíčové komponenty a rozhraní
- **`FormatDetector`:** Analyzuje prvních 16–32 bajtů souboru a vrací detekovaný `MediaFormat` (např. JPEG, PNG, MP3, FLAC, MP4, UNKNOWN).
- **`CapabilityResolver`:** Pro daný formát vrátí `FormatCapabilities` (míra podpory pro čtení, editaci, čištění a specifická varování).
- **`MetadataEngine` (rozhraní):**
  - `fun extract(file: File): MetadataResult`
  - `fun applyChanges(source: File, target: File, changes: List<MetadataChange>): ProcessingResult`
- **`MetadataVerifier`:** Nezávisle otevře nově vygenerovaný soubor, znovu z něj vytáhne metadata a provede matematický diff oproti požadovaným změnám.
- **`FileAccessRepository`:** Zajišťuje bezpečné kopírování ze SAF `content://` URI do soukromé cache aplikace, streaming do nového souboru a úklid po skončení operace.

---

## 7. Struktura balíčků projektu (`cz.metaforge.app`)

```
app/src/main/java/cz/metaforge/app/
├── MetaForgeApplication.kt
├── MainActivity.kt
├── data/
│   ├── detector/
│   │   ├── FormatDetector.kt
│   │   └── MagicBytes.kt
│   ├── engine/
│   │   ├── MetadataEngine.kt
│   │   ├── ExifMetadataEngine.kt       // JPEG, WebP, PNG
│   │   ├── AudioMetadataEngine.kt      // MP3, FLAC
│   │   ├── Mp4MetadataEngine.kt        // MP4 / M4A
│   │   └── FallbackMetadataEngine.kt   // Neznámé soubory
│   ├── model/
│   │   ├── MediaFormat.kt
│   │   ├── MetadataItem.kt
│   │   ├── MetadataCategory.kt
│   │   ├── Capability.kt
│   │   └── SanitizationProfile.kt
│   └── repository/
│       ├── FileAccessRepository.kt
│       ├── MetadataVerifier.kt
│       └── PreferencesRepository.kt
├── ui/
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   └── Type.kt
│   ├── components/
│   │   ├── AmoledScaffold.kt
│   │   ├── MetadataCard.kt
│   │   ├── DiffPreview.kt
│   │   └── CapabilityBadge.kt
│   ├── screens/
│   │   ├── home/HomeScreen.kt
│   │   ├── inspector/InspectorScreen.kt
│   │   ├── edit/EditMetadataScreen.kt
│   │   ├── sanitize/SanitizeProfileScreen.kt
│   │   ├── result/ResultScreen.kt
│   │   └── settings/SettingsScreen.kt
│   └── viewmodel/
│       ├── MainViewModel.kt
│       └── MainUiState.kt
```

---

## 8. Etapy implementace

1. **Fáze 1: Konfigurace Gradle a CI Workflow**
   - Nastavení `app/build.gradle.kts` a `libs.versions.toml` pro `ExifInterface`, `jaudiotagger`, `datastore`.
   - Vytvoření `.github/workflows/android-build.yml` s Gradle cache a nahráním sestaveného debug APK jako artefaktu.
   - Úprava `metadata.json` a `strings.xml` pro název MetaForge.
2. **Fáze 2: Datová vrstva a detekce formátů**
   - Implementace `MagicBytes` a `FormatDetector`.
   - Vytvoření modelu `MetadataItem` (namespace, klíč, zobrazovaný název, hodnota, příznak citlivosti).
   - Implementace `FileAccessRepository` pro bezpečné operace se SAF.
3. **Fáze 3: Metadata Engines (Exif, Audio, MP4)**
   - `ExifMetadataEngine` pro čtení, úpravu a čištění JPEG/PNG/WebP.
   - `AudioMetadataEngine` pro MP3 ID3v1/v2 a FLAC Vorbis tagy.
   - `Mp4MetadataEngine` pro detekci a čtení MP4 atomů.
   - Implementace verifikátoru `MetadataVerifier`.
4. **Fáze 4: UI a ViewModel (Jetpack Compose, AMOLED)**
   - Implementace `Theme.kt` s podporou čisté AMOLED černé.
   - Obrazovky: `HomeScreen`, `InspectorScreen`, `SanitizeProfileScreen`, `ResultScreen`, `SettingsScreen`.
   - Vyhledávání, filtry citlivých dat a export do JSON/Textu.
5. **Fáze 5: Testování a validace**
   - Unit testy pro detektory hlaviček a Exif/ID3 parsery na syntetických datech.
   - Robolectric testy pro ověření celého cyklu načtení -> vyčištění -> verifikace.

---

## 9. Testovací a validační plán

- **Testy detektoru formátů (`FormatDetectorTest`):**
  - Ověření správné detekce i v případě, že soubor má záměrně lživou příponu (např. MP3 přejmenovaná na `.jpg`).
- **Testy zachování originálu (`SafetyIsolationTest`):**
  - Ověření, že původní vstupní soubor nebyl během operací úpravy nijak modifikován (porovnání SHA-256 hashe originálu před a po).
- **Testy úprav a čištění (`ExifSanitizerTest` & `AudioSanitizerTest`):**
  - Syntetický JPEG se zapsanými GPS souřadnicemi -> aplikace profilu „Odstranit polohu“ -> nezávislé opětovné načtení ověří, že tagy `TAG_GPS_LATITUDE` a `TAG_GPS_LONGITUDE` jsou null, ale `TAG_ORIENTATION` zůstal zachován.
- **Testy odolnosti proti chybám (`CorruptedFileTest`):**
  - Otestování vstupu nulové délky (0 bajtů), useknutých hlaviček a neplatných chunků. Aplikace nesmí havarovat na neošetřenou výjimku.

---

## 10. CI/CD & Distribuce APK

V repozitáři bude vytvořen workflow `.github/workflows/android-build.yml`:
- Běh na `ubuntu-latest` s `actions/setup-java` (JDK 17).
- Automatické cachování Gradle závislostí pro rychlý běh.
- Kroky:
  1. Kontrola formátování a syntaxe.
  2. Spuštění unit a Robolectric testů (`gradle testDebugUnitTest`).
  3. Sestavení debug APK (`gradle assembleDebug`).
  4. Nahrání artefaktu `metaforge-debug.apk` pro okamžité stažení a instalaci.

---

## 11. Akceptační kritéria (Definition of Done)

1. Aplikace úspěšně zkompiluje a projde buildem bez chyb.
2. Uživatel může otevřít libovolný soubor přes SAF bez vyžadování `MANAGE_EXTERNAL_STORAGE`.
3. Skutečný formát je detekován podle hlavičky souboru.
4. U fotografií (JPEG/PNG/WebP) a audia (MP3/FLAC) lze zobrazit metadata, filtrovat citlivá data a aplikovat profil pro smazání GPS či autora.
5. Původní soubor zůstává 100% nezměněn; nový soubor je exportován přes `CreateDocument`.
6. Tmavý režim disponuje skutečným AMOLED černým pozadím `#000000`.
7. Celý proces funguje zcela offline bez síťových požadavků.
8. GitHub Actions workflow úspěšně vygeneruje instalovatelné APK.
