# MetaForge — Offline Metadata Inspector & Sanitizer

**MetaForge** (`cz.metaforge.app`) je nativní Android aplikace vyvinutá v Kotlinu a Jetpack Compose pro detailní analýzu, filtrování, úpravu a bezpečné čištění (sanitizaci) metadat fotografií, audia a videí.

Aplikace funguje **100% lokálně, v offline režimu, bez nutnosti účtu, připojení k internetu, serveru či reklam**.

---

## 🚀 Klíčové vlastnosti

- **Forenzní detekce formátu:** Identifikace souboru podle magických bajtů v hlavičce, nikoli podle nespolehlivé přípony.
- **Bezpečnostní model:** Původní soubor není nikdy přepisován; veškeré operace probíhají nad izolovanou pracovní kopií v privátním úložišti.
- **Kategorizace metadat:** Přehledné rozdělení na *Citlivá data* (GPS, autor, sériová čísla), *Technické vlastnosti*, *Exif*, *Audio tagy* a *Systémová data*.
- **Profily čištění:**
  - 📍 **Odstranit polohu (GPS):** Kompletní vymazání zeměpisné šířky, délky, nadmořské výšky a časových razítek GPS.
  - 👤 **Odstranit osobní a autorská data:** Vymazání autora, copyrightu, modelu fotoaparátu, sériových čísel a názvu software.
  - 🧹 **Maximální bezpečné vyčištění:** Odstranění všech volitelných popisných metadat při zachování strukturálních dat.
  - ⚙️ **Vlastní výběr:** Individuální volba konkrétních tagů k odstranění.
- **Diferenční náhled & Nezávislá verifikace:** Před uložením aplikace zobrazí přesný rozdíl (Před vs Po). Výsledný soubor je po vygenerování nezávisle znovu analyzován.
- **Export & Sdílení:** Uložení přes systémový dialog „Uložit jako“ (SAF `CreateDocument`) nebo bezpečné sdílení přes `FileProvider`.
- **Čisté AMOLED rozhraní:** Pravé `#000000` pozadí s moderními Cyber Teal akcenty (`#00D2B4`) a podporou tmavého, světlého i systémového režimu.
- **Export zprávy:** Možnost exportovat kompletní strom metadat do formátu JSON nebo čitelného textového souboru.

---

## 📊 Matice podpory formátů a operací

| Formát | Detekce (Magic Bytes) | Čtení metadat | Úprava hodnot | Bezpečné odstranění | Známá omezení a poznámky |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **JPEG** | `FF D8 FF` | Exif, GPS, TIFF, IPTC | Ano (jednotlivé Exif tagy) | Ano (GPS, Autor, Vše) | Orientace je zachována, aby nedošlo k otočení fotografie. |
| **PNG** | `89 50 4E 47 0D 0A 1A 0A` | eXIf chunk, tEXt | Ano | Ano (eXIf chunk) | Strukturální bloky (IHDR, IDAT, IEND) zůstávají nedotčeny. |
| **WebP** | `RIFF....WEBP` | EXIF a XMP chunky | Ano | Ano | Obrazová data VP8/VP8L zůstávají bezeztrátově nedotčena. |
| **MP3** | `ID3` / MPEG Sync `FF FB` | ID3v1, ID3v2.2–2.4 | Ano | Ano (odstranění tagů) | Zvukové rámce zůstávají bitově identické; odstraní se hlavička/patička. |
| **FLAC** | `fLaC` | Vorbis Comment, STREAMINFO | Čtení | Ano (vyčištění komentářů) | Blok STREAMINFO je zachován pro zachování dekódovatelnosti. |
| **MP4 / M4A** | `ftyp` box v offsetu 4 | Atomy, rozlišení, GPS, kodeky | Čtení | Ano (přenos bez metadat) | Zobrazí embedded polohu z kamerových telefonů. |
| **Neznámý** | Libovolné | SAF info, Hex signatura, SHA-256 | Ne (deaktivováno) | Ne | Zobrazí se upozornění o nepodporovaném formátu. |

---

## 🛠️ Otevření a sestavení projektu

### Požadavky
- **Android Studio** (Koala / Ladybug / Meerkat nebo novější)
- **JDK 17** nebo **JDK 21**
- **Android SDK:** Compile SDK 36, Min SDK 24 (Android 7.0+)

### Otevření v Android Studiu
1. Naklonujte repozitář nebo otevřete kořenový adresář projektu v Android Studiu přes `File -> Open`.
2. Počkejte na dokončení Gradle Sync.
3. Zvolte konfiguraci `app` a spusťte na emulátoru nebo fyzickém zařízení.

### Lokální build příkazy (terminál)
Obnovení debug keystoru (pokud klonujete nový čistý repozitář):
```bash
base64 -d debug.keystore.base64 > debug.keystore
```

Spuštění unit testů:
```bash
./gradlew testDebugUnitTest
```

Sestavení instalovatelného Debug APK:
```bash
./gradlew assembleDebug
```
Výsledné APK naleznete v cestě:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🤖 GitHub Actions CI & Stažení APK

V repozitáři je nakonfigurován automatický workflow `.github/workflows/android-build.yml`:
- Spouští se automaticky při každém `push`, `pull_request` a lze jej spustit i ručně přes záložku **Actions -> Run workflow**.
- Provede kontrolu, spustí unit testy a sestaví debug APK.

### Postup stažení a instalace APK na telefon:
1. Přejděte na GitHubu do záložky **Actions**.
2. Klikněte na poslední úspěšný běh workflow (*Build & Test MetaForge APK*).
3. V sekci **Artifacts** klikněte na artefakt `metaforge-debug-apk` a stáhněte ZIP archiv.
4. Rozbalte archiv na telefonu nebo v počítači.
5. V telefonu povolte instalaci z neznámých zdrojů pro váš správce souborů a otevřete soubor APK pro instalaci.

---

## 🔒 Zásady ochrany soukromí

- Aplikace **nevyžaduje oprávnění `INTERNET`** (nemá síťový stack).
- Aplikace **nevyžaduje oprávnění `MANAGE_EXTERNAL_STORAGE`**. Přístup k souborům je řízen výhradně uživatelem prostřednictvím systémového `Storage Access Framework` (SAF).
- Dočasné soubory jsou ukládány do privátní mezipaměti (`context.cacheDir`) a lze je kdykoli okamžitě smazat v nastavení aplikace.

---

## 📄 Licence závislostí

- **AndroidX ExifInterface:** Apache License 2.0 (Google LLC)
- **AndroidX Jetpack & Compose:** Apache License 2.0 (The Android Open Source Project)
- **AndroidX DataStore:** Apache License 2.0 (The Android Open Source Project)
- **Kotlin & Coroutines:** Apache License 2.0 (JetBrains s.r.o.)
