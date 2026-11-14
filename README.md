# Musix

Pemutar musik offline untuk Android dengan tampilan kaca (glassmorphism) ala iPhone di atas latar gradasi.

## Fitur
- Memutar **semua format audio**: MP3, M4A/AAC, FLAC, WAV, OGG, OPUS, dll. Seek MP3 (CBR) akurat.
- **Jalan di latar belakang** (foreground service) dan **kontrol di layar kunci** + notifikasi media (Media3 MediaSession).
- Otomatis jeda saat headset dicabut, ikut aturan audio focus (telepon/aplikasi lain).
- **Layar kunci kaca** (opsional): saat layar mati dan musik jalan, muncul layar Sedang Diputar bergaya kaca di atas lock screen (jam, cover, seekbar, kontrol). Menutup otomatis saat HP dibuka kuncinya.
  Aktifkan di menu samping → "Layar kunci kaca", lalu izinkan "Tampil di atas aplikasi lain". Di HP Xiaomi/Oppo/Vivo, aktifkan juga "Tampilkan di layar kunci" / "Tampilkan pop-up saat berjalan di latar belakang" pada izin aplikasi.
- Beranda seperti referensi: bilah cari, 3 kartu kaca (Terbaru / Favorit / Baru Ditambah), panel kaca Playlist & Album, daftar semua lagu, menu samping, mini player, navigasi bawah.
- Layar "Sedang Diputar": cover besar, seekbar, acak, ulangi, favorit, timer tidur.
- Favorit, playlist buatan sendiri, pencarian, urutkan (A–Z / terbaru / artis).
- Menu "Tambah file musik manual" untuk file yang tidak terdeteksi otomatis, serta bisa dibuka langsung dari file manager.

## Cara mendapatkan APK lewat GitHub
1. Buat repository baru di GitHub (kosong), lalu upload **seluruh isi folder ini** (termasuk folder `.github`) ke branch `main`.
   - Dari HP: buka repo → *Add file* → *Upload files* → pilih semua file/folder hasil ekstrak ZIP.
     Pastikan folder `.github/workflows/build.yml` ikut terupload.
   - Dari komputer: `git init && git add . && git commit -m "init" && git branch -M main && git remote add origin <URL-REPO> && git push -u origin main`
2. Buka tab **Actions** → workflow **Build APK** berjalan otomatis (±5–8 menit). Bisa juga dijalankan manual lewat *Run workflow*.
3. Setelah hijau, ambil APK di:
   - tab **Releases** (file `GlassPlayer.apk`, paling mudah diunduh dari HP), atau
   - halaman run → bagian **Artifacts** → `GlassPlayer-apk`.
4. Install APK (izinkan "install dari sumber tidak dikenal"), buka aplikasi, izinkan akses musik dan notifikasi.

> APK yang dihasilkan bertipe debug (ditandatangani kunci debug), cukup untuk dipakai sehari-hari.

## Build di komputer (opsional)
Butuh JDK 17 + Android SDK, lalu buka folder ini di Android Studio dan jalankan *Build > Build APK*.
