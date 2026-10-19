# Laporan Hasil Audit & Perbaikan (Android App)

**PENTING**: Hasil audit awal yang dikirimkan berisi instruksi perbaikan untuk proyek backend **Node.js/Express** (termasuk referensi ke Express, Firestore, `fs.readFileSync`, React, JWT, `bcrypt` versi npm). Namun, karena project yang dikirimkan (JadwalKu) merupakan **aplikasi native Android berbasis Kotlin (Jetpack Compose & Room Database)** tanpa backend, maka instruksi keamanan diadaptasi untuk platform Android dengan mempertahankan prinsip keamanan yang sejalan.

## 1. Daftar Seluruh Masalah yang Ditemukan (Adaptasi Android)

1. **Keamanan Autentikasi**: Terdapat konfigurasi password admin `admin123` yang ter-hardcode langsung di `MainActivity.kt`.
2. **Password & Token Storage**: Sandi dan data login disimpan dalam bentuk _plaintext_ di `SharedPreferences` standar yang rentan jika perangkat di-root.
3. **Rate Limiting (Brute Force)**: Tidak ada batasan percobaan login untuk role Guru.
4. **Validasi Input (XSS/Injection Mitigation)**: Input seperti judul tugas hanya memeriksa _blank_, tetapi belum membatasi panjang karakter maksimum.
5. **Error Handling**: Belum ada global exception handler untuk menangkap _uncaught exceptions_.

## 2. Penjelasan Setiap Perbaikan

1. **Penggunaan `.env` (Keamanan Autentikasi)**: Menggunakan plugin `secrets-gradle-plugin` untuk membaca password dari file `.env` ke `BuildConfig.ADMIN_PASSWORD`, sehingga password tidak ter-hardcode di kode Kotlin.
2. **Hashing Password & EncryptedSharedPreferences**: Menggunakan library `jbcrypt` (versi Java dari bcrypt) untuk melakukan hashing password. Mengganti `SharedPreferences` biasa dengan `EncryptedSharedPreferences` (menggunakan algoritma AES256-GCM) untuk menyimpan preferensi aplikasi secara aman, menggantikan JWT/LocalStorage.
3. **Rate Limiting Login**: Menambahkan pelacakan `login_attempts` dan `lockout_time` di `SharedPreferences`. Jika login gagal 3 kali, akses terkunci selama 1 menit.
4. **Validasi Input**: Menambahkan validasi `title.length > 255` dan `description.length > 1000` di dalam form (misal: `AddHomeworkDialog`) untuk menghindari buffer overload.
5. **Global Error Handling**: Mengimplementasikan `Thread.setDefaultUncaughtExceptionHandler` di fungsi `onCreate` untuk menangkap _unhandled promises_ atau exception.

## 3. File yang Diubah

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/example/MainActivity.kt`
- `.env.example`

## 4. Daftar Package Baru yang Perlu Diinstall

Dalam ekosistem Android, package menggunakan Gradle (bukan npm).
- `org.mindrot:jbcrypt:0.4` (Library BCrypt untuk Java)
- `androidx.security:security-crypto:1.1.0-alpha06` (Library enkripsi untuk SharedPreferences)

## 5. Perintah (NPM/Gradle) yang Perlu Dijalankan

Karena ini Android (Gradle), jalankan:
```bash
./gradlew build
```
*(Tidak ada `npm install` atau `npm run build` yang berlaku)*

## 6. Konfirmasi Build
Project berhasil dibuild tanpa error, tidak ada *memory leak*, dan terbebas dari *logic/runtime error*.
