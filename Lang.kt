package com.glassplayer.app.ui

import android.content.Context
import android.widget.Toast as AndroidToast
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.glassplayer.app.data.Store

/** Bahasa aplikasi. Teks sumber ditulis dalam Bahasa Indonesia; bahasa lain dicari di kamus. */
object AppLang {
    class Option(val code: String, val native: String)

    val options = listOf(
        Option("id", "Indonesia"),
        Option("en", "English"),
        Option("ar", "\u0627\u0644\u0639\u0631\u0628\u064A\u0629"),
        Option("ms", "Bahasa Melayu"),
        Option("hi", "\u0939\u093F\u0928\u094D\u0926\u0940"),
        Option("es", "Espa\u00F1ol"),
    )

    var code by mutableStateOf("id")
        private set

    val isRtl get() = code == "ar"
    val currentName get() = options.firstOrNull { it.code == code }?.native ?: "Indonesia"

    fun load(ctx: Context) { code = Store(ctx).lang }
    fun set(ctx: Context, c: String) { Store(ctx).lang = c; code = c }

    private val index get() = when (code) { "en" -> 1; "ar" -> 2; "ms" -> 3; "hi" -> 4; "es" -> 5; else -> -1 }

    private class Tpl(val regex: Regex, val out: String)
    private val exact = HashMap<String, Array<String>>()
    private val templates = ArrayList<Pair<String, Array<String>>>()
    private val tplCache = HashMap<Int, List<Tpl>>()
    private val cache = HashMap<String, String>()
    private var cacheCode = ""
    private var ready = false

    private fun part0() = listOf(
        arrayOf("Beranda", "Home", "الرئيسية", "Laman Utama", "होम", "Inicio"),
        arrayOf("Home", "Home", "الرئيسية", "Laman Utama", "होम", "Inicio"),
        arrayOf("New", "New", "جديد", "Baharu", "नया", "Novedades"),
        arrayOf("Radio", "Radio", "الراديو", "Radio", "रेडियो", "Radio"),
        arrayOf("Library", "Library", "المكتبة", "Pustaka", "लाइब्रेरी", "Biblioteca"),
        arrayOf("Search", "Search", "بحث", "Cari", "खोज", "Buscar"),
        arrayOf("Pustaka", "Library", "المكتبة", "Pustaka", "लाइब्रेरी", "Biblioteca"),
        arrayOf("Cari", "Search", "بحث", "Cari", "खोजें", "Buscar"),
        arrayOf("Favorit", "Favorites", "المفضلة", "Kegemaran", "पसंदीदा", "Favoritos"),
        arrayOf("Favorites", "Favorites", "المفضلة", "Kegemaran", "पसंदीदा", "Favoritos"),
        arrayOf("Menu", "Menu", "القائمة", "Menu", "मेनू", "Menú"),
        arrayOf("Menu lainnya", "More options", "المزيد من الخيارات", "Lagi pilihan", "और विकल्प", "Más opciones"),
        arrayOf("Setelan", "Settings", "الإعدادات", "Tetapan", "सेटिंग्स", "Ajustes"),
        arrayOf("Kembali", "Back", "رجوع", "Kembali", "वापस", "Atrás"),
        arrayOf("Tutup", "Close", "إغلاق", "Tutup", "बंद करें", "Cerrar"),
        arrayOf("Batal", "Cancel", "إلغاء", "Batal", "रद्द करें", "Cancelar"),
        arrayOf("Simpan", "Save", "حفظ", "Simpan", "सहेजें", "Guardar"),
        arrayOf("Hapus", "Delete", "حذف", "Padam", "हटाएं", "Eliminar"),
        arrayOf("Hapus semua", "Clear all", "مسح الكل", "Padam semua", "सभी हटाएं", "Borrar todo"),
        arrayOf("Clear", "Clear", "مسح", "Kosongkan", "साफ़ करें", "Borrar"),
        arrayOf("Oke", "OK", "حسناً", "OK", "ठीक है", "Aceptar"),
        arrayOf("OK", "OK", "حسناً", "OK", "ठीक है", "Aceptar"),
        arrayOf("Nanti", "Later", "لاحقاً", "Nanti", "बाद में", "Más tarde"),
        arrayOf("Izinkan", "Allow", "سماح", "Benarkan", "अनुमति दें", "Permitir"),
        arrayOf("Tambah", "Add", "إضافة", "Tambah", "जोड़ें", "Añadir"),
        arrayOf("Pilih", "Select", "اختيار", "Pilih", "चुनें", "Seleccionar"),
        arrayOf("Pilih semua", "Select all", "تحديد الكل", "Pilih semua", "सभी चुनें", "Seleccionar todo"),
        arrayOf("Putar", "Play", "تشغيل", "Main", "चलाएं", "Reproducir"),
        arrayOf("Acak", "Shuffle", "عشوائي", "Rawak", "शफ़ल", "Aleatorio"),
        arrayOf("Acak daftar", "Shuffle list", "تشغيل القائمة عشوائياً", "Rawak senarai", "सूची शफ़ल करें", "Mezclar lista"),
        arrayOf("Putar Sekarang", "Play Now", "شغّل الآن", "Main Sekarang", "अभी चलाएं", "Reproducir ahora"),
        arrayOf("Putar/Jeda", "Play/Pause", "تشغيل/إيقاف مؤقت", "Main/Jeda", "चलाएं/रोकें", "Reproducir/Pausa"),
        arrayOf("Berikutnya", "Next", "التالي", "Seterusnya", "अगला", "Siguiente"),
        arrayOf("Sebelumnya", "Previous", "السابق", "Sebelumnya", "पिछला", "Anterior"),
        arrayOf("Ulangi", "Repeat", "تكرار", "Ulang", "दोहराएं", "Repetir"),
        arrayOf("Urutkan", "Sort", "ترتيب", "Isih", "क्रमबद्ध करें", "Ordenar"),
        arrayOf("Terbaru", "Newest", "الأحدث", "Terbaharu", "नवीनतम", "Más nuevas"),
        arrayOf("A–Z", "A–Z", "أ–ي", "A–Z", "अ–ज्ञ", "A–Z"),
        arrayOf("Lihat semua", "See all", "عرض الكل", "Lihat semua", "सभी देखें", "Ver todo"),
        arrayOf("Lainnya", "More", "المزيد", "Lagi", "और", "Más"),
        arrayOf("Unduh", "Download", "تنزيل", "Muat turun", "डाउनलोड", "Descargar"),
        arrayOf("Perbarui", "Update", "تحديث", "Kemas kini", "अपडेट करें", "Actualizar"),
        arrayOf("Pasang", "Install", "تثبيت", "Pasang", "इंस्टॉल करें", "Instalar"),
        arrayOf("Coba lagi", "Try again", "حاول مرة أخرى", "Cuba lagi", "फिर से कोशिश करें", "Reintentar"),
        arrayOf("Periksa lagi", "Check again", "تحقق مرة أخرى", "Semak semula", "फिर से जाँचें", "Comprobar de nuevo"),
        arrayOf("Setel ulang", "Reset", "إعادة تعيين", "Tetap semula", "रीसेट करें", "Restablecer"),
        arrayOf("Diterapkan", "Applied", "تم التطبيق", "Diterapkan", "लागू किया गया", "Aplicado"),
        arrayOf("Aktif", "On", "مفعّل", "Aktif", "चालू", "Activado"),
        arrayOf("Mati", "Off", "متوقف", "Mati", "बंद", "Desactivado"),
        arrayOf("Bawaan", "Default", "الافتراضي", "Lalai", "डिफ़ॉल्ट", "Predeterminado"),
        arrayOf("Normal (1.0x)", "Normal (1.0x)", "عادي (1.0x)", "Biasa (1.0x)", "सामान्य (1.0x)", "Normal (1.0x)"),
        arrayOf("Lagu", "Songs", "الأغاني", "Lagu", "गाने", "Canciones"),
        arrayOf("Artis", "Artists", "الفنانون", "Artis", "कलाकार", "Artistas"),
        arrayOf("Artists", "Artists", "الفنانون", "Artis", "कलाकार", "Artistas"),
        arrayOf("Album", "Albums", "الألبومات", "Album", "एल्बम", "Álbumes"),
        arrayOf("Albums", "Albums", "الألبومات", "Album", "एल्बम", "Álbumes"),
        arrayOf("Folder", "Folders", "المجلدات", "Folder", "फ़ोल्डर", "Carpetas"),
        arrayOf("Playlists", "Playlists", "قوائم التشغيل", "Senarai main", "प्लेलिस्ट", "Listas"),
        arrayOf("Everything", "Everything", "الكل", "Semua", "सब कुछ", "Todo"),
        arrayOf("Daftar putar", "Playlists", "قوائم التشغيل", "Senarai main", "प्लेलिस्ट", "Listas de reproducción"),
    )
    private fun part1() = listOf(
        arrayOf("Daftar putar baru", "New playlist", "قائمة تشغيل جديدة", "Senarai main baharu", "नई प्लेलिस्ट", "Nueva lista"),
        arrayOf("New playlist", "New playlist", "قائمة تشغيل جديدة", "Senarai main baharu", "नई प्लेलिस्ट", "Nueva lista"),
        arrayOf("+ Daftar putar baru", "+ New playlist", "+ قائمة تشغيل جديدة", "+ Senarai main baharu", "+ नई प्लेलिस्ट", "+ Nueva lista"),
        arrayOf("Buat daftar putar", "Create playlist", "إنشاء قائمة تشغيل", "Cipta senarai main", "प्लेलिस्ट बनाएं", "Crear lista"),
        arrayOf("Nama daftar putar", "Playlist name", "اسم قائمة التشغيل", "Nama senarai main", "प्लेलिस्ट का नाम", "Nombre de la lista"),
        arrayOf("Nama kosong atau sudah dipakai", "Name is empty or already used", "الاسم فارغ أو مستخدم بالفعل", "Nama kosong atau sudah digunakan", "नाम खाली है या पहले से उपयोग में है", "El nombre está vacío o ya se usa"),
        arrayOf("Belum ada daftar putar", "No playlists yet", "لا توجد قوائم تشغيل بعد", "Belum ada senarai main", "अभी कोई प्लेलिस्ट नहीं है", "Aún no hay listas"),
        arrayOf("Hapus daftar putar", "Delete playlist", "حذف قائمة التشغيل", "Padam senarai main", "प्लेलिस्ट हटाएं", "Eliminar lista"),
        arrayOf("Semua Lagu", "All songs", "كل الأغاني", "Semua Lagu", "सभी गाने", "Todas las canciones"),
        arrayOf("Artis tidak dikenal", "Unknown artist", "فنان غير معروف", "Artis tidak dikenali", "अज्ञात कलाकार", "Artista desconocido"),
        arrayOf("Tanpa judul", "Untitled", "بدون عنوان", "Tanpa tajuk", "बिना शीर्षक", "Sin título"),
        arrayOf("Terkini", "Recent", "الأخيرة", "Terkini", "हाल के", "Recientes"),
        arrayOf("Terbaru Diputar", "Recently played", "شُغّلت مؤخراً", "Baru dimainkan", "हाल में चलाए गए", "Reproducidas recientemente"),
        arrayOf("Baru Ditambah", "Recently added", "أُضيفت حديثاً", "Baru ditambah", "नए जोड़े गए", "Añadidas recientemente"),
        arrayOf("Ditambahkan", "Added", "أُضيفت", "Ditambah", "जोड़ा गया", "Añadida"),
        arrayOf("Sedang Diputar", "Now playing", "قيد التشغيل", "Sedang dimainkan", "अभी चल रहा है", "Reproduciendo"),
        arrayOf("Antrian", "Queue", "قائمة الانتظار", "Giliran", "कतार", "Cola"),
        arrayOf("Antrian ({})", "Queue ({})", "قائمة الانتظار ({})", "Giliran ({})", "कतार ({})", "Cola ({})"),
        arrayOf("Rekomendasi", "Recommended", "مقترحات", "Cadangan", "सुझाव", "Recomendado"),
        arrayOf("Musik", "Music", "الموسيقى", "Muzik", "संगीत", "Música"),
        arrayOf("Lokasi", "Location", "الموقع", "Lokasi", "स्थान", "Ubicación"),
        arrayOf("Durasi", "Duration", "المدة", "Tempoh", "अवधि", "Duración"),
        arrayOf("Ukuran", "Size", "الحجم", "Saiz", "आकार", "Tamaño"),
        arrayOf("Judul", "Title", "العنوان", "Tajuk", "शीर्षक", "Título"),
        arrayOf("Nama file", "File name", "اسم الملف", "Nama fail", "फ़ाइल का नाम", "Nombre de archivo"),
        arrayOf("Detail lagu", "Song details", "تفاصيل الأغنية", "Butiran lagu", "गाने का विवरण", "Detalles de la canción"),
        arrayOf("Edit info lagu", "Edit song info", "تعديل معلومات الأغنية", "Sunting maklumat lagu", "गाने की जानकारी संपादित करें", "Editar información"),
        arrayOf("Putar nanti", "Play later", "تشغيل لاحقاً", "Main kemudian", "बाद में चलाएं", "Reproducir después"),
        arrayOf("Diputar berikutnya", "Playing next", "سيُشغَّل التالي", "Dimainkan seterusnya", "अगला चलेगा", "Se reproducirá a continuación"),
        arrayOf("Tambah ke antrian", "Add to queue", "إضافة إلى قائمة الانتظار", "Tambah ke giliran", "कतार में जोड़ें", "Añadir a la cola"),
        arrayOf("Ditambahkan ke antrian", "Added to queue", "أُضيفت إلى قائمة الانتظار", "Ditambah ke giliran", "कतार में जोड़ा गया", "Añadida a la cola"),
        arrayOf("Tambah ke daftar putar", "Add to playlist", "إضافة إلى قائمة تشغيل", "Tambah ke senarai main", "प्लेलिस्ट में जोड़ें", "Añadir a una lista"),
        arrayOf("Tambah ke favorit", "Add to favorites", "إضافة إلى المفضلة", "Tambah ke kegemaran", "पसंदीदा में जोड़ें", "Añadir a favoritos"),
        arrayOf("Hapus dari favorit", "Remove from favorites", "إزالة من المفضلة", "Buang daripada kegemaran", "पसंदीदा से हटाएं", "Quitar de favoritos"),
        arrayOf("Hapus dari antrian", "Remove from queue", "إزالة من قائمة الانتظار", "Buang daripada giliran", "कतार से हटाएं", "Quitar de la cola"),
        arrayOf("Hapus dari daftar putar ini", "Remove from this playlist", "إزالة من قائمة التشغيل هذه", "Buang daripada senarai main ini", "इस प्लेलिस्ट से हटाएं", "Quitar de esta lista"),
        arrayOf("Bagikan lagu", "Share song", "مشاركة الأغنية", "Kongsi lagu", "गाना साझा करें", "Compartir canción"),
        arrayOf("Berbagi", "Share", "مشاركة", "Kongsi", "साझा करें", "Compartir"),
        arrayOf("Nada dering", "Ringtone", "نغمة الرنين", "Nada dering", "रिंगटोन", "Tono de llamada"),
        arrayOf("Pilih lagu", "Select songs", "اختر الأغاني", "Pilih lagu", "गाने चुनें", "Seleccionar canciones"),
        arrayOf("Tambah lagu", "Add songs", "إضافة أغانٍ", "Tambah lagu", "गाने जोड़ें", "Añadir canciones"),
        arrayOf("Tambahkan ({})", "Add ({})", "إضافة ({})", "Tambah ({})", "जोड़ें ({})", "Añadir ({})"),
        arrayOf("{} item terpilih", "{} items selected", "{} عناصر محددة", "{} item dipilih", "{} आइटम चुने गए", "{} seleccionados"),
        arrayOf("{} lagu", "{} songs", "{} أغنية", "{} lagu", "{} गाने", "{} canciones"),
        arrayOf("{} items", "{} items", "{} عنصر", "{} item", "{} आइटम", "{} elementos"),
        arrayOf("Total {} lagu", "Total {} songs", "المجموع {} أغنية", "Jumlah {} lagu", "कुल {} गाने", "Total: {} canciones"),
        arrayOf("Putar acak • {} lagu", "Shuffle • {} songs", "تشغيل عشوائي • {} أغنية", "Main rawak • {} lagu", "शफ़ल • {} गाने", "Aleatorio • {} canciones"),
        arrayOf("{} lagu dihapus", "{} songs deleted", "تم حذف {} أغنية", "{} lagu dipadam", "{} गाने हटाए गए", "{} canciones eliminadas"),
        arrayOf("{} lagu ditambahkan ke favorit", "{} songs added to favorites", "تمت إضافة {} أغنية إلى المفضلة", "{} lagu ditambah ke kegemaran", "{} गाने पसंदीदा में जोड़े गए", "{} canciones añadidas a favoritos"),
        arrayOf("{} lagu ditambahkan ke \"{}\"", "{} songs added to \"{}\"", "تمت إضافة {} أغنية إلى \"{}\"", "{} lagu ditambah ke \"{}\"", "{} गाने \"{}\" में जोड़े गए", "{} canciones añadidas a \"{}\""),
        arrayOf("Ditambahkan ke \"{}\"", "Added to \"{}\"", "أُضيفت إلى \"{}\"", "Ditambah ke \"{}\"", "\"{}\" में जोड़ा गया", "Añadida a \"{}\""),
        arrayOf("Sudah ada di \"{}\"", "Already in \"{}\"", "موجودة بالفعل في \"{}\"", "Sudah ada dalam \"{}\"", "पहले से \"{}\" में है", "Ya está en \"{}\""),
        arrayOf("{} file ditambahkan", "{} files added", "تمت إضافة {} ملف", "{} fail ditambah", "{} फ़ाइलें जोड़ी गईं", "{} archivos añadidos"),
        arrayOf("Hapus {} lagu?", "Delete {} songs?", "حذف {} أغنية؟", "Padam {} lagu?", "{} गाने हटाएं?", "¿Eliminar {} canciones?"),
        arrayOf("Lagu dihapus", "Song deleted", "تم حذف الأغنية", "Lagu dipadam", "गाना हटाया गया", "Canción eliminada"),
        arrayOf("File akan dihapus dari perangkat dan tidak bisa dikembalikan.", "The file will be deleted from the device and can't be restored.", "سيتم حذف الملف من الجهاز ولا يمكن استرجاعه.", "Fail akan dipadam daripada peranti dan tidak boleh dipulihkan.", "फ़ाइल डिवाइस से हटा दी जाएगी और वापस नहीं लाई जा सकेगी।", "El archivo se eliminará del dispositivo y no se podrá recuperar."),
        arrayOf("Sebagian lagu tidak bisa dihapus", "Some songs couldn't be deleted", "تعذّر حذف بعض الأغاني", "Sebahagian lagu tidak dapat dipadam", "कुछ गाने हटाए नहीं जा सके", "No se pudieron eliminar algunas canciones"),
        arrayOf("{} lagu tidak bisa dihapus", "{} songs couldn't be deleted", "تعذّر حذف {} أغنية", "{} lagu tidak dapat dipadam", "{} गाने हटाए नहीं जा सके", "No se pudieron eliminar {} canciones"),
        arrayOf("Lagu ini tidak bisa dihapus dari sini", "This song can't be deleted from here", "لا يمكن حذف هذه الأغنية من هنا", "Lagu ini tidak boleh dipadam dari sini", "यह गाना यहाँ से नहीं हटाया जा सकता", "Esta canción no se puede eliminar desde aquí"),
        arrayOf("Lagu ini tidak bisa diedit", "This song can't be edited", "لا يمكن تعديل هذه الأغنية", "Lagu ini tidak boleh disunting", "यह गाना संपादित नहीं किया जा सकता", "Esta canción no se puede editar"),
    )
    private fun part2() = listOf(
        arrayOf("Info lagu diperbarui", "Song info updated", "تم تحديث معلومات الأغنية", "Maklumat lagu dikemas kini", "गाने की जानकारी अपडेट हुई", "Información actualizada"),
        arrayOf("Gagal memperbarui info lagu", "Couldn't update song info", "تعذّر تحديث معلومات الأغنية", "Gagal mengemas kini maklumat lagu", "गाने की जानकारी अपडेट नहीं हो सकी", "No se pudo actualizar la información"),
        arrayOf("Perubahan tersimpan di data media Android, file aslinya tidak diubah.", "Changes are saved in Android's media data; the original file is not modified.", "يتم حفظ التغييرات في بيانات وسائط أندرويد ولا يتم تعديل الملف الأصلي.", "Perubahan disimpan dalam data media Android, fail asal tidak diubah.", "बदलाव Android के मीडिया डेटा में सहेजे जाते हैं, मूल फ़ाइल नहीं बदलती।", "Los cambios se guardan en los datos multimedia de Android; el archivo original no se modifica."),
        arrayOf("Gagal mengatur nada dering untuk lagu ini", "Couldn't set a ringtone for this song", "تعذّر تعيين نغمة رنين لهذه الأغنية", "Gagal menetapkan nada dering untuk lagu ini", "इस गाने के लिए रिंगटोन सेट नहीं हो सकी", "No se pudo establecer el tono de llamada"),
        arrayOf("Nada dering diatur: {}", "Ringtone set: {}", "تم تعيين نغمة الرنين: {}", "Nada dering ditetapkan: {}", "रिंगटोन सेट हुई: {}", "Tono de llamada establecido: {}"),
        arrayOf("Izinkan \"Ubah setelan sistem\" untuk Musix, lalu ulangi", "Allow \"Modify system settings\" for Musix, then try again", "اسمح بـ \"تعديل إعدادات النظام\" لـ Musix ثم أعد المحاولة", "Benarkan \"Ubah suai tetapan sistem\" untuk Musix, kemudian cuba lagi", "Musix के लिए \"सिस्टम सेटिंग बदलें\" की अनुमति दें, फिर दोबारा कोशिश करें", "Permite \"Modificar ajustes del sistema\" para Musix y vuelve a intentarlo"),
        arrayOf("Tidak bisa membagikan lagu", "Couldn't share the song", "تعذّرت مشاركة الأغنية", "Tidak dapat berkongsi lagu", "गाना साझा नहीं हो सका", "No se pudo compartir la canción"),
        arrayOf("Tidak bisa membuka tautan", "Couldn't open the link", "تعذّر فتح الرابط", "Tidak dapat membuka pautan", "लिंक नहीं खुल सका", "No se pudo abrir el enlace"),
        arrayOf("Pemutar belum siap, coba lagi", "Player isn't ready, try again", "المشغّل غير جاهز، حاول مرة أخرى", "Pemain belum sedia, cuba lagi", "प्लेयर तैयार नहीं है, फिर कोशिश करें", "El reproductor no está listo, inténtalo de nuevo"),
        arrayOf("Izinkan akses semua file?", "Allow access to all files?", "السماح بالوصول إلى كل الملفات؟", "Benarkan akses ke semua fail?", "सभी फ़ाइलों का एक्सेस दें?", "¿Permitir acceso a todos los archivos?"),
        arrayOf("Sistem menolak menghapus file lagu ini dari penyimpanan. Aktifkan \"Akses semua file\" untuk Musix, lalu kembali ke aplikasi: file akan dihapus otomatis. Lagu ini sudah disembunyikan dari daftar.", "The system refused to delete this song file from storage. Turn on \"All files access\" for Musix, then return to the app and the file will be deleted automatically. This song is already hidden from the list.", "رفض النظام حذف ملف هذه الأغنية من التخزين. فعّل \"الوصول إلى كل الملفات\" لـ Musix ثم عد إلى التطبيق وسيُحذف الملف تلقائياً. هذه الأغنية مخفية بالفعل من القائمة.", "Sistem enggan memadam fail lagu ini daripada storan. Aktifkan \"Akses semua fail\" untuk Musix, kemudian kembali ke aplikasi: fail akan dipadam secara automatik. Lagu ini sudah disembunyikan daripada senarai.", "सिस्टम ने इस गाने की फ़ाइल को स्टोरेज से हटाने से मना कर दिया। Musix के लिए \"सभी फ़ाइलों का एक्सेस\" चालू करें और ऐप में लौटें, फ़ाइल अपने आप हट जाएगी। यह गाना सूची से पहले ही छिपा दिया गया है।", "El sistema se negó a eliminar el archivo de esta canción. Activa \"Acceso a todos los archivos\" para Musix y vuelve a la app: el archivo se eliminará automáticamente. Esta canción ya está oculta de la lista."),
        arrayOf("Buka pengaturan", "Open settings", "فتح الإعدادات", "Buka tetapan", "सेटिंग्स खोलें", "Abrir ajustes"),
        arrayOf("{} file belum terhapus dari penyimpanan, hapus lewat pengelola file", "{} files weren't deleted from storage; delete them with a file manager", "لم يتم حذف {} ملف من التخزين، احذفها عبر مدير الملفات", "{} fail belum dipadam daripada storan, padam melalui pengurus fail", "{} फ़ाइलें स्टोरेज से नहीं हटीं, फ़ाइल मैनेजर से हटाएं", "{} archivos no se eliminaron; bórralos con un administrador de archivos"),
        arrayOf("{} file masih gagal dihapus", "{} files still couldn't be deleted", "لا يزال تعذّر حذف {} ملف", "{} fail masih gagal dipadam", "{} फ़ाइलें अब भी नहीं हट सकीं", "{} archivos aún no se pudieron eliminar"),
        arrayOf("{} file dihapus permanen", "{} files permanently deleted", "تم حذف {} ملف نهائياً", "{} fail dipadam secara kekal", "{} फ़ाइलें हमेशा के लिए हटाई गईं", "{} archivos eliminados permanentemente"),
        arrayOf("Cari lagu, daftar putar, dan artis", "Search songs, playlists and artists", "ابحث عن الأغاني وقوائم التشغيل والفنانين", "Cari lagu, senarai main dan artis", "गाने, प्लेलिस्ट और कलाकार खोजें", "Busca canciones, listas y artistas"),
        arrayOf("Lagu, artis, album", "Songs, artists, albums", "أغانٍ، فنانون، ألبومات", "Lagu, artis, album", "गाने, कलाकार, एल्बम", "Canciones, artistas, álbumes"),
        arrayOf("Search your library", "Search your library", "ابحث في مكتبتك", "Cari dalam pustaka anda", "अपनी लाइब्रेरी में खोजें", "Busca en tu biblioteca"),
        arrayOf("Nothing found.", "Nothing found.", "لم يتم العثور على شيء.", "Tiada apa-apa ditemui.", "कुछ नहीं मिला।", "No se encontró nada."),
        arrayOf("Tidak ada hasil untuk \"{}\"", "No results for \"{}\"", "لا توجد نتائج لـ \"{}\"", "Tiada hasil untuk \"{}\"", "\"{}\" के लिए कोई परिणाम नहीं", "Sin resultados para \"{}\""),
        arrayOf("Ketik judul lagu, artis, atau album di kolom bawah.", "Type a song title, artist or album in the box below.", "اكتب عنوان أغنية أو فناناً أو ألبوماً في الحقل بالأسفل.", "Taip tajuk lagu, artis atau album di ruang bawah.", "नीचे बॉक्स में गाने का नाम, कलाकार या एल्बम लिखें।", "Escribe un título, artista o álbum en el cuadro de abajo."),
        arrayOf("DARI PENCARIAN ANDA", "FROM YOUR SEARCHES", "من عمليات بحثك", "DARIPADA CARIAN ANDA", "आपकी खोजों से", "DE TUS BÚSQUEDAS"),
        arrayOf("Lagu di HP Anda", "Songs on your phone", "الأغاني على هاتفك", "Lagu dalam telefon anda", "आपके फ़ोन के गाने", "Canciones en tu teléfono"),
        arrayOf("Cari dan putar", "Search and play", "ابحث وشغّل", "Cari dan main", "खोजें और चलाएं", "Busca y reproduce"),
        arrayOf("Local files", "Local files", "الملفات المحلية", "Fail setempat", "स्थानीय फ़ाइलें", "Archivos locales"),
        arrayOf("Downloaded songs", "Downloaded songs", "الأغاني المنزّلة", "Lagu dimuat turun", "डाउनलोड किए गए गाने", "Canciones descargadas"),
        arrayOf("Lagu terunduh", "Downloaded songs", "الأغاني المنزّلة", "Lagu dimuat turun", "डाउनलोड किए गए गाने", "Canciones descargadas"),
        arrayOf("Sort: {}", "Sort: {}", "الترتيب: {}", "Isih: {}", "क्रम: {}", "Orden: {}"),
        arrayOf("LIBRARY", "LIBRARY", "المكتبة", "PUSTAKA", "लाइब्रेरी", "BIBLIOTECA"),
        arrayOf("Nikmati musik", "Enjoy music", "استمتع بالموسيقى", "Nikmati muzik", "संगीत का आनंद लें", "Disfruta la música"),
        arrayOf("favoritmu", "your favorites", "المفضلة لديك", "kegemaranmu", "अपनी पसंद का", "que más amas"),
        arrayOf("PLAYLIST SPESIAL", "SPECIAL PLAYLIST", "قائمة تشغيل خاصة", "SENARAI MAIN ISTIMEWA", "विशेष प्लेलिस्ट", "LISTA ESPECIAL"),
        arrayOf("Malam yang\nLebih Tenang", "A Calmer\nNight", "ليلة\nأكثر هدوءاً", "Malam yang\nLebih Tenang", "और भी\nशांत रात", "Una noche\nmás tranquila"),
        arrayOf("Kumpulan lagu pilihan untuk\ntemani malam harimu.", "A selection of songs\nto keep you company tonight.", "مجموعة أغانٍ مختارة\nتؤنس ليلتك.", "Koleksi lagu pilihan\nuntuk menemani malam anda.", "चुने हुए गानों का संग्रह\nआपकी रात के साथी।", "Una selección de canciones\npara acompañar tu noche."),
        arrayOf("Favorit\nKamu", "Your\nFavorites", "مفضلتك", "Kegemaran\nAnda", "आपके\nपसंदीदा", "Tus\nfavoritos"),
        arrayOf("Lagu-lagu yang paling\nkamu sukai.", "The songs you\nlove the most.", "الأغاني التي\nتحبها أكثر.", "Lagu-lagu yang\npaling anda suka.", "वे गाने जो\nआपको सबसे ज़्यादा पसंद हैं।", "Las canciones que\nmás te gustan."),
        arrayOf("Baru\nDitambahkan", "Recently\nAdded", "أُضيفت\nحديثاً", "Baru\nDitambah", "हाल ही में\nजोड़े गए", "Añadidas\nrecientemente"),
        arrayOf("Lagu terbaru yang masuk\nke perangkatmu.", "The newest songs\non your device.", "أحدث الأغاني\nعلى جهازك.", "Lagu terbaharu yang masuk\nke peranti anda.", "आपके डिवाइस में\nआए नए गाने।", "Las canciones más nuevas\nde tu dispositivo."),
        arrayOf("Acak\nSemua", "Shuffle\nAll", "تشغيل الكل\nعشوائياً", "Rawak\nSemua", "सब\nशफ़ल करें", "Todo\nal azar"),
        arrayOf("Semua lagumu diputar\nacak untukmu.", "All your songs\nplayed in random order.", "كل أغانيك تُشغَّل\nبترتيب عشوائي.", "Semua lagu anda dimainkan\nsecara rawak.", "आपके सभी गाने\nशफ़ल होकर चलेंगे।", "Todas tus canciones\nen orden aleatorio."),
        arrayOf("Untuk Kamu", "For You", "لك", "Untuk Anda", "आपके लिए", "Para ti"),
        arrayOf("Favorit Kamu", "Your Favorites", "مفضلتك", "Kegemaran Anda", "आपके पसंदीदा", "Tus favoritos"),
        arrayOf("Santai Sejenak", "Take a Break", "استرخِ قليلاً", "Santai Sebentar", "थोड़ा आराम", "Un respiro"),
        arrayOf("Lagu Populer", "Popular Songs", "الأغاني الشائعة", "Lagu Popular", "लोकप्रिय गाने", "Canciones populares"),
        arrayOf("Belum ada lagu. Tambahkan lewat Setelan → Pindai ulang musik.", "No songs yet. Add some via Settings → Rescan music.", "لا توجد أغانٍ بعد. أضفها عبر الإعدادات ← إعادة فحص الموسيقى.", "Belum ada lagu. Tambah melalui Tetapan → Imbas semula muzik.", "अभी कोई गाना नहीं। सेटिंग्स → संगीत दोबारा स्कैन करें से जोड़ें।", "Aún no hay canciones. Añádelas en Ajustes → Volver a escanear música."),
        arrayOf("Belum ada lagu di sini.", "No songs here yet.", "لا توجد أغانٍ هنا بعد.", "Belum ada lagu di sini.", "यहाँ अभी कोई गाना नहीं है।", "Aún no hay canciones aquí."),
        arrayOf("Belum ada lagu ditemukan", "No songs found yet", "لم يتم العثور على أغانٍ بعد", "Belum ada lagu ditemui", "अभी कोई गाना नहीं मिला", "Aún no se encontraron canciones"),
        arrayOf("Belum ada lagu yang cocok di koleksi lokal", "No matching songs in your local collection yet", "لا توجد أغانٍ مطابقة في مجموعتك المحلية بعد", "Belum ada lagu yang sesuai dalam koleksi setempat", "आपके स्थानीय संग्रह में अभी कोई मेल खाता गाना नहीं", "Aún no hay canciones que coincidan en tu colección local"),
        arrayOf("Lagu terbaru", "Latest songs", "أحدث الأغاني", "Lagu terbaharu", "नए गाने", "Últimas canciones"),
        arrayOf("MOODS & MOMENTS", "MOODS & MOMENTS", "الحالات واللحظات", "SUASANA & MOMEN", "मूड और पल", "ESTADOS DE ÁNIMO Y MOMENTOS"),
        arrayOf("Relax", "Relax", "استرخاء", "Rehat", "रिलैक्स", "Relax"),
        arrayOf("Sad", "Sad", "حزين", "Sedih", "उदास", "Triste"),
        arrayOf("Happy", "Happy", "سعيد", "Gembira", "खुश", "Feliz"),
        arrayOf("Romantic", "Romantic", "رومانسي", "Romantik", "रोमांटिक", "Romántico"),
        arrayOf("Chill", "Chill", "هادئ", "Santai", "चिल", "Tranquilo"),
        arrayOf("Focus", "Focus", "تركيز", "Fokus", "फ़ोकस", "Concentración"),
        arrayOf("Energize", "Energize", "نشاط", "Bertenaga", "ऊर्जा", "Energía"),
        arrayOf("Party", "Party", "حفلة", "Parti", "पार्टी", "Fiesta"),
        arrayOf("Workout", "Workout", "تمارين", "Senaman", "वर्कआउट", "Entrenamiento"),
        arrayOf("Commute", "Commute", "التنقل", "Perjalanan", "सफ़र", "Trayecto"),
    )
    private fun part3() = listOf(
        arrayOf("Sleep", "Sleep", "نوم", "Tidur", "नींद", "Dormir"),
        arrayOf("{} mix", "{} mix", "مزيج {}", "Campuran {}", "{} मिक्स", "Mix {}"),
        arrayOf("Music videos", "Music videos", "مقاطع الفيديو الموسيقية", "Video muzik", "म्यूज़िक वीडियो", "Videos musicales"),
        arrayOf("Buka di YouTube", "Open in YouTube", "فتح في يوتيوب", "Buka di YouTube", "YouTube में खोलें", "Abrir en YouTube"),
        arrayOf("Memuat video musik…", "Loading music videos…", "جارٍ تحميل مقاطع الفيديو الموسيقية…", "Memuatkan video muzik…", "म्यूज़िक वीडियो लोड हो रहे हैं…", "Cargando videos musicales…"),
        arrayOf("Kunci YouTube API belum diisi. Tambahkan secret YT_API_KEY di GitHub lalu build ulang.", "The YouTube API key isn't set. Add the YT_API_KEY secret on GitHub and rebuild.", "لم يتم تعيين مفتاح YouTube API. أضف السر YT_API_KEY في GitHub ثم أعد البناء.", "Kunci API YouTube belum diisi. Tambah rahsia YT_API_KEY di GitHub dan bina semula.", "YouTube API कुंजी सेट नहीं है। GitHub में YT_API_KEY सीक्रेट जोड़ें और फिर से बिल्ड करें।", "La clave de la API de YouTube no está configurada. Añade el secreto YT_API_KEY en GitHub y vuelve a compilar."),
        arrayOf("Albums & singles", "Albums & singles", "الألبومات والأغاني المنفردة", "Album & single", "एल्बम और सिंगल", "Álbumes y sencillos"),
        arrayOf("Downloads and storage", "Downloads and storage", "التنزيلات والتخزين", "Muat turun dan storan", "डाउनलोड और स्टोरेज", "Descargas y almacenamiento"),
        arrayOf("Unduhan dan ruang penyimpanan", "Downloads and storage space", "التنزيلات ومساحة التخزين", "Muat turun dan ruang storan", "डाउनलोड और स्टोरेज स्पेस", "Descargas y espacio de almacenamiento"),
        arrayOf("App", "App", "التطبيق", "Aplikasi", "ऐप", "App"),
        arrayOf("Language, appearance, system and links", "Language, appearance, system and links", "اللغة والمظهر والنظام والروابط", "Bahasa, penampilan, sistem dan pautan", "भाषा, दिखावट, सिस्टम और लिंक", "Idioma, apariencia, sistema y enlaces"),
        arrayOf("Language", "Language", "اللغة", "Bahasa", "भाषा", "Idioma"),
        arrayOf("Pilih bahasa aplikasi", "Choose the app language", "اختر لغة التطبيق", "Pilih bahasa aplikasi", "ऐप की भाषा चुनें", "Elige el idioma de la app"),
        arrayOf("Layar kunci kaca", "Glass lock screen", "شاشة القفل الزجاجية", "Skrin kunci kaca", "ग्लास लॉक स्क्रीन", "Pantalla de bloqueo de cristal"),
        arrayOf("Tampil saat layar mati & musik jalan", "Shown when the screen is off and music is playing", "تظهر عند إطفاء الشاشة وتشغيل الموسيقى", "Dipaparkan apabila skrin mati & muzik dimainkan", "स्क्रीन बंद और संगीत चलने पर दिखता है", "Se muestra con la pantalla apagada y música sonando"),
        arrayOf("Timer tidur", "Sleep timer", "مؤقت النوم", "Pemasa tidur", "स्लीप टाइमर", "Temporizador de sueño"),
        arrayOf("Pindai ulang musik", "Rescan music", "إعادة فحص الموسيقى", "Imbas semula muzik", "संगीत दोबारा स्कैन करें", "Volver a escanear música"),
        arrayOf("Tambah file musik manual", "Add music files manually", "إضافة ملفات موسيقى يدوياً", "Tambah fail muzik secara manual", "संगीत फ़ाइलें मैन्युअली जोड़ें", "Añadir archivos de música manualmente"),
        arrayOf("Equalizer", "Equalizer", "المعادل الصوتي", "Penyama", "इक्वलाइज़र", "Ecualizador"),
        arrayOf("Kecepatan putar", "Playback speed", "سرعة التشغيل", "Kelajuan main balik", "प्लेबैक गति", "Velocidad de reproducción"),
        arrayOf("Tema warna", "Color theme", "سمة الألوان", "Tema warna", "रंग थीम", "Tema de color"),
        arrayOf("Versi aplikasi", "App version", "إصدار التطبيق", "Versi aplikasi", "ऐप संस्करण", "Versión de la app"),
        arrayOf("Pembaruan aplikasi", "App update", "تحديث التطبيق", "Kemas kini aplikasi", "ऐप अपडेट", "Actualización de la app"),
        arrayOf("Periksa dan pasang versi terbaru", "Check for and install the latest version", "تحقق من أحدث إصدار وثبّته", "Semak dan pasang versi terkini", "नवीनतम संस्करण जाँचें और इंस्टॉल करें", "Busca e instala la última versión"),
        arrayOf("Ikuti & hubungi pembuat", "Follow & contact the creator", "تابع وتواصل مع المطوّر", "Ikuti & hubungi pencipta", "क्रिएटर को फ़ॉलो करें और संपर्क करें", "Sigue y contacta al creador"),
        arrayOf("Appearance", "Appearance", "المظهر", "Penampilan", "दिखावट", "Apariencia"),
        arrayOf("Tema dan warna aplikasi", "App theme and colors", "سمة التطبيق وألوانه", "Tema dan warna aplikasi", "ऐप की थीम और रंग", "Tema y colores de la app"),
        arrayOf("System", "System", "النظام", "Sistem", "सिस्टम", "Sistema"),
        arrayOf("Izin, baterai, dan pengaturan Android", "Permissions, battery and Android settings", "الأذونات والبطارية وإعدادات أندرويد", "Kebenaran, bateri dan tetapan Android", "अनुमतियाँ, बैटरी और Android सेटिंग्स", "Permisos, batería y ajustes de Android"),
        arrayOf("Hitam murni (OLED)", "Pure black (OLED)", "أسود نقي (OLED)", "Hitam tulen (OLED)", "शुद्ध काला (OLED)", "Negro puro (OLED)"),
        arrayOf("Latar hitam total, piksel OLED mati", "Fully black background, OLED pixels off", "خلفية سوداء بالكامل، وبكسلات OLED مطفأة", "Latar belakang hitam sepenuhnya, piksel OLED dimatikan", "पूरी तरह काली पृष्ठभूमि, OLED पिक्सल बंद", "Fondo totalmente negro, píxeles OLED apagados"),
        arrayOf("Hanya bisa dipakai di tema gelap (Malam / Hitam)", "Only available with a dark theme (Night / Black)", "متاح فقط مع السمة الداكنة (ليل / أسود)", "Hanya boleh digunakan dengan tema gelap (Malam / Hitam)", "केवल डार्क थीम (रात / काला) में उपलब्ध", "Solo disponible con un tema oscuro (Noche / Negro)"),
        arrayOf("Pasir (bawaan)", "Sand (default)", "رملي (الافتراضي)", "Pasir (lalai)", "रेत (डिफ़ॉल्ट)", "Arena (predeterminado)"),
        arrayOf("Samudra", "Ocean", "المحيط", "Lautan", "सागर", "Océano"),
        arrayOf("Hutan", "Forest", "الغابة", "Hutan", "जंगल", "Bosque"),
        arrayOf("Senja", "Dusk", "الغروب", "Senja", "शाम", "Atardecer"),
        arrayOf("Hitam", "Black", "أسود", "Hitam", "काला", "Negro"),
        arrayOf("Malam", "Night", "الليل", "Malam", "रात", "Noche"),
        arrayOf("Downloads", "Downloads", "التنزيلات", "Muat turun", "डाउनलोड", "Descargas"),
        arrayOf("Download over Wi-Fi only", "Download over Wi-Fi only", "التنزيل عبر Wi-Fi فقط", "Muat turun melalui Wi-Fi sahaja", "केवल Wi-Fi पर डाउनलोड", "Descargar solo con Wi-Fi"),
        arrayOf("Buka folder Download", "Open the Download folder", "فتح مجلد التنزيلات", "Buka folder Muat Turun", "डाउनलोड फ़ोल्डर खोलें", "Abrir la carpeta Descargas"),
        arrayOf("File musik pada folder Download", "Music files in the Download folder", "ملفات الموسيقى في مجلد التنزيلات", "Fail muzik dalam folder Muat Turun", "डाउनलोड फ़ोल्डर की संगीत फ़ाइलें", "Archivos de música en Descargas"),
        arrayOf("Penyimpanan", "Storage", "التخزين", "Storan", "स्टोरेज", "Almacenamiento"),
        arrayOf("Penyimpanan perangkat terpakai", "Device storage used", "مساحة التخزين المستخدمة", "Storan peranti digunakan", "उपयोग किया गया डिवाइस स्टोरेज", "Almacenamiento usado"),
        arrayOf("Tersedia {} dari {}", "{} free of {}", "متاح {} من {}", "{} tersedia daripada {}", "{} में से {} उपलब्ध", "{} libres de {}"),
        arrayOf("Catatan: Musix memutar file lokal. Pengelolaan unduhan streaming dan penghapusan cache khusus belum tersedia.", "Note: Musix plays local files. Streaming download management and dedicated cache clearing aren't available yet.", "ملاحظة: يشغّل Musix الملفات المحلية. إدارة تنزيلات البث ومسح ذاكرة التخزين المؤقت المخصصة غير متاحة بعد.", "Nota: Musix memainkan fail setempat. Pengurusan muat turun penstriman dan pemadaman cache khas belum tersedia.", "नोट: Musix स्थानीय फ़ाइलें चलाता है। स्ट्रीमिंग डाउनलोड प्रबंधन और विशेष कैश साफ़ करना अभी उपलब्ध नहीं है।", "Nota: Musix reproduce archivos locales. La gestión de descargas por streaming y la limpieza de caché aún no están disponibles."),
        arrayOf("Salin file musik ke HP, atau pilih file secara manual.", "Copy music files to your phone, or pick files manually.", "انسخ ملفات الموسيقى إلى هاتفك أو اخترها يدوياً.", "Salin fail muzik ke telefon, atau pilih fail secara manual.", "संगीत फ़ाइलें फ़ोन में कॉपी करें या मैन्युअली चुनें।", "Copia archivos de música al teléfono o elígelos manualmente."),
        arrayOf("Pembaruan tersedia", "Update available", "يتوفر تحديث", "Kemas kini tersedia", "अपडेट उपलब्ध है", "Actualización disponible"),
        arrayOf("Versi {} tersedia", "Version {} is available", "الإصدار {} متاح", "Versi {} tersedia", "संस्करण {} उपलब्ध है", "La versión {} está disponible"),
        arrayOf("Musix {} sudah tersedia. Perbarui sekarang untuk fitur dan perbaikan terbaru.", "Musix {} is available. Update now for the latest features and fixes.", "Musix {} متاح الآن. حدّث الآن للحصول على أحدث الميزات والإصلاحات.", "Musix {} sudah tersedia. Kemas kini sekarang untuk ciri dan pembaikan terkini.", "Musix {} उपलब्ध है। नई सुविधाओं और सुधारों के लिए अभी अपडेट करें।", "Musix {} está disponible. Actualiza ahora para obtener las últimas funciones y mejoras."),
        arrayOf("Memeriksa versi terbaru…", "Checking for the latest version…", "جارٍ التحقق من أحدث إصدار…", "Menyemak versi terkini…", "नवीनतम संस्करण जाँचा जा रहा है…", "Buscando la última versión…"),
        arrayOf("Sudah versi terbaru", "You're up to date", "لديك أحدث إصدار", "Sudah versi terkini", "आप नवीनतम संस्करण पर हैं", "Ya tienes la última versión"),
        arrayOf("Versi {} adalah yang paling baru.", "Version {} is the latest.", "الإصدار {} هو الأحدث.", "Versi {} ialah yang terkini.", "संस्करण {} नवीनतम है।", "La versión {} es la más reciente."),
        arrayOf("Versi sekarang {}", "Current version {}", "الإصدار الحالي {}", "Versi semasa {}", "वर्तमान संस्करण {}", "Versión actual {}"),
        arrayOf("Versi {}", "Version {}", "الإصدار {}", "Versi {}", "संस्करण {}", "Versión {}"),
        arrayOf("Mengunduh versi {}…", "Downloading version {}…", "جارٍ تنزيل الإصدار {}…", "Memuat turun versi {}…", "संस्करण {} डाउनलोड हो रहा है…", "Descargando la versión {}…"),
        arrayOf("Unduhan selesai", "Download complete", "اكتمل التنزيل", "Muat turun selesai", "डाउनलोड पूरा हुआ", "Descarga completada"),
        arrayOf("Unduhan gagal. Periksa koneksi internet lalu coba lagi.", "Download failed. Check your internet connection and try again.", "فشل التنزيل. تحقق من اتصال الإنترنت وحاول مرة أخرى.", "Muat turun gagal. Semak sambungan internet dan cuba lagi.", "डाउनलोड विफल रहा। इंटरनेट कनेक्शन जाँचें और फिर कोशिश करें।", "La descarga falló. Revisa tu conexión a internet e inténtalo de nuevo."),
        arrayOf("Ketuk Pasang. Jika Android meminta izin \"pasang aplikasi dari sumber ini\", aktifkan, lalu kembali ke sini dan ketuk Pasang lagi. Data dan pengaturan Anda tetap aman.", "Tap Install. If Android asks for the \"install apps from this source\" permission, turn it on, then come back here and tap Install again. Your data and settings stay safe.", "اضغط على تثبيت. إذا طلب أندرويد إذن \"تثبيت التطبيقات من هذا المصدر\" فعّله ثم عد إلى هنا واضغط تثبيت مرة أخرى. بياناتك وإعداداتك تبقى آمنة.", "Ketik Pasang. Jika Android meminta kebenaran \"pasang aplikasi daripada sumber ini\", aktifkan, kemudian kembali ke sini dan ketik Pasang semula. Data dan tetapan anda kekal selamat.", "इंस्टॉल दबाएं। अगर Android \"इस स्रोत से ऐप इंस्टॉल करें\" की अनुमति माँगे तो उसे चालू करें, फिर यहाँ लौटकर दोबारा इंस्टॉल दबाएं। आपका डेटा और सेटिंग्स सुरक्षित रहेंगे।", "Toca Instalar. Si Android pide el permiso \"instalar apps de esta fuente\", actívalo, vuelve aquí y toca Instalar de nuevo. Tus datos y ajustes están a salvo."),
        arrayOf("Gaya pemutar", "Player style", "نمط المشغّل", "Gaya pemain", "प्लेयर स्टाइल", "Estilo del reproductor"),
    )
    private fun part4() = listOf(
        arrayOf("Vinyl klasik", "Classic vinyl", "فينيل كلاسيكي", "Vinil klasik", "क्लासिक विनाइल", "Vinilo clásico"),
        arrayOf("Vinyl modern", "Modern vinyl", "فينيل عصري", "Vinil moden", "मॉडर्न विनाइल", "Vinilo moderno"),
        arrayOf("Kaset", "Cassette", "كاسيت", "Kaset", "कैसेट", "Casete"),
        arrayOf("Kaset dengan roda berputar", "Cassette with spinning reels", "كاسيت بعجلات تدور", "Kaset dengan roda berputar", "घूमते पहियों वाली कैसेट", "Casete con ruedas giratorias"),
        arrayOf("CD", "CD", "CD", "CD", "CD", "CD"),
        arrayOf("Cakram CD mengilap", "Shiny CD disc", "قرص CD لامع", "Cakera CD berkilat", "चमकदार CD डिस्क", "Disco CD brillante"),
        arrayOf("Piringan hitam berputar", "Spinning vinyl record", "أسطوانة فينيل تدور", "Rekod vinil berputar", "घूमता विनाइल रिकॉर्ड", "Disco de vinilo girando"),
        arrayOf("Piringan hitam dengan jarum", "Vinyl record with a needle", "أسطوانة فينيل مع إبرة", "Rekod vinil dengan jarum", "सुई के साथ विनाइल रिकॉर्ड", "Disco de vinilo con aguja"),
        arrayOf("Cover album persegi membulat", "Rounded square album cover", "غلاف ألبوم مربع بزوايا مستديرة", "Kulit album segi empat bulat", "गोल कोनों वाला एल्बम कवर", "Portada cuadrada redondeada"),
        arrayOf("Kapsul", "Capsule", "كبسولة", "Kapsul", "कैप्सूल", "Cápsula"),
        arrayOf("Piringan persegi", "Square disc", "قرص مربع", "Cakera segi empat sama", "चौकोर डिस्क", "Disco cuadrado"),
        arrayOf("Piringan persegi panjang", "Rectangular disc", "قرص مستطيل", "Cakera segi empat tepat", "आयताकार डिस्क", "Disco rectangular"),
        arrayOf("Pemutar mini + 4 lagu terakhir", "Mini player + last 4 songs", "مشغّل مصغّر + آخر 4 أغانٍ", "Pemain mini + 4 lagu terakhir", "मिनी प्लेयर + आखिरी 4 गाने", "Minirreproductor + últimas 4 canciones"),
        arrayOf("Pemutar mini + kolom cari", "Mini player + search box", "مشغّل مصغّر + حقل بحث", "Pemain mini + ruang carian", "मिनी प्लेयर + सर्च बॉक्स", "Minirreproductor + buscador"),
        arrayOf("Kartu kaca dengan tombol putar, jeda, berikutnya", "Glass card with play, pause and next buttons", "بطاقة زجاجية بأزرار التشغيل والإيقاف والتالي", "Kad kaca dengan butang main, jeda, seterusnya", "प्ले, पॉज़ और नेक्स्ट बटन वाला ग्लास कार्ड", "Tarjeta de cristal con reproducir, pausa y siguiente"),
        arrayOf("Kontrol cepat putar/jeda", "Quick play/pause control", "تحكم سريع بالتشغيل/الإيقاف المؤقت", "Kawalan pantas main/jeda", "प्ले/पॉज़ का त्वरित नियंत्रण", "Control rápido de reproducir/pausa"),
        arrayOf("Piringan di kiri, tombol putar di kanan", "Disc on the left, play button on the right", "القرص على اليسار وزر التشغيل على اليمين", "Cakera di kiri, butang main di kanan", "बाएँ डिस्क, दाएँ प्ले बटन", "Disco a la izquierda, botón de reproducir a la derecha"),
        arrayOf("Widget Layar utama", "Home screen widgets", "ودجات الشاشة الرئيسية", "Widget Skrin utama", "होम स्क्रीन विजेट", "Widgets de la pantalla de inicio"),
        arrayOf("Pilih widget lalu ketuk Tambah. Jika launcher tidak mendukung, tahan lama layar utama → Widget → Musix.", "Choose a widget, then tap Add. If your launcher doesn't support it, long-press the home screen → Widgets → Musix.", "اختر ودجت ثم اضغط إضافة. إذا لم يدعمها المُشغّل، اضغط مطولاً على الشاشة الرئيسية ← الودجات ← Musix.", "Pilih widget kemudian ketik Tambah. Jika pelancar tidak menyokongnya, tekan lama skrin utama → Widget → Musix.", "विजेट चुनें और जोड़ें दबाएं। अगर लॉन्चर सपोर्ट न करे तो होम स्क्रीन को देर तक दबाएं → विजेट → Musix।", "Elige un widget y toca Añadir. Si tu launcher no lo admite, mantén pulsada la pantalla de inicio → Widgets → Musix."),
        arrayOf("Bass boost", "Bass boost", "تعزيز الجهير", "Peningkat bes", "बास बूस्ट", "Refuerzo de graves"),
        arrayOf("Putar sebuah lagu dulu agar equalizer aktif. Pengaturan Anda tetap tersimpan.", "Play a song first to activate the equalizer. Your settings are kept.", "شغّل أغنية أولاً لتفعيل المعادل. إعداداتك محفوظة.", "Mainkan satu lagu dahulu untuk mengaktifkan penyama. Tetapan anda kekal disimpan.", "इक्वलाइज़र चालू करने के लिए पहले कोई गाना चलाएं। आपकी सेटिंग्स सहेजी रहती हैं।", "Reproduce una canción primero para activar el ecualizador. Tus ajustes se conservan."),
        arrayOf("Timer tidur (aktif)", "Sleep timer (on)", "مؤقت النوم (مفعّل)", "Pemasa tidur (aktif)", "स्लीप टाइमर (चालू)", "Temporizador de sueño (activo)"),
        arrayOf("Timer tidur dimatikan", "Sleep timer turned off", "تم إيقاف مؤقت النوم", "Pemasa tidur dimatikan", "स्लीप टाइमर बंद किया गया", "Temporizador de sueño desactivado"),
        arrayOf("Timer tidur: {} menit", "Sleep timer: {} minutes", "مؤقت النوم: {} دقيقة", "Pemasa tidur: {} minit", "स्लीप टाइमर: {} मिनट", "Temporizador de sueño: {} minutos"),
        arrayOf("Matikan timer", "Turn off timer", "إيقاف المؤقت", "Matikan pemasa", "टाइमर बंद करें", "Desactivar temporizador"),
        arrayOf("{} menit", "{} minutes", "{} دقيقة", "{} minit", "{} मिनट", "{} minutos"),
        arrayOf("{} detik", "{} seconds", "{} ثانية", "{} saat", "{} सेकंड", "{} segundos"),
        arrayOf("Crossfade", "Crossfade", "المزج بين الأغاني", "Crossfade", "क्रॉसफ़ेड", "Fundido cruzado"),
        arrayOf("Volume memudar di ujung lagu dan naik perlahan di awal lagu berikutnya.", "Volume fades at the end of a song and rises slowly at the start of the next.", "يخفت الصوت في نهاية الأغنية ويرتفع تدريجياً في بداية التالية.", "Volum menghilang di hujung lagu dan naik perlahan di awal lagu seterusnya.", "गाने के अंत में आवाज़ धीमी होती है और अगले गाने की शुरुआत में धीरे-धीरे बढ़ती है।", "El volumen baja al final de una canción y sube lentamente al inicio de la siguiente."),
        arrayOf("Tentang", "About", "حول", "Tentang", "के बारे में", "Acerca de"),
        arrayOf("Berjalan di latar belakang dan di layar kunci.\n\nVersi {}", "Runs in the background and on the lock screen.\n\nVersion {}", "يعمل في الخلفية وعلى شاشة القفل.\n\nالإصدار {}", "Berjalan di latar belakang dan pada skrin kunci.\n\nVersi {}", "बैकग्राउंड और लॉक स्क्रीन पर चलता है।\n\nसंस्करण {}", "Funciona en segundo plano y en la pantalla de bloqueo.\n\nVersión {}"),
        arrayOf("Pemutar musik offline untuk semua format audio (MP3, M4A, FLAC, WAV, OGG, OPUS, dll). ", "An offline music player for all audio formats (MP3, M4A, FLAC, WAV, OGG, OPUS, etc.). ", "مشغّل موسيقى دون اتصال لجميع صيغ الصوت (MP3, M4A, FLAC, WAV, OGG, OPUS وغيرها). ", "Pemain muzik luar talian untuk semua format audio (MP3, M4A, FLAC, WAV, OGG, OPUS, dll). ", "सभी ऑडियो फ़ॉर्मेट (MP3, M4A, FLAC, WAV, OGG, OPUS आदि) के लिए ऑफ़लाइन म्यूज़िक प्लेयर। ", "Reproductor de música sin conexión para todos los formatos de audio (MP3, M4A, FLAC, WAV, OGG, OPUS, etc.). "),
        arrayOf("Kebijakan Privasi", "Privacy Policy", "سياسة الخصوصية", "Dasar Privasi", "गोपनीयता नीति", "Política de privacidad"),
        arrayOf("Laporan bug", "Report a bug", "الإبلاغ عن خطأ", "Laporkan pepijat", "बग रिपोर्ट करें", "Informar de un error"),
        arrayOf("Pertanyaan? Hubungi {}", "Questions? Contact {}", "أسئلة؟ تواصل مع {}", "Soalan? Hubungi {}", "सवाल? संपर्क करें {}", "¿Preguntas? Contacta con {}"),
        arrayOf("Tidak ada aplikasi email. Kirim ke {}", "No email app found. Send to {}", "لا يوجد تطبيق بريد. أرسل إلى {}", "Tiada aplikasi e-mel. Hantar ke {}", "कोई ईमेल ऐप नहीं मिला। इस पते पर भेजें: {}", "No hay app de correo. Envía a {}"),
        arrayOf("Izinkan akses musik", "Allow music access", "السماح بالوصول إلى الموسيقى", "Benarkan akses muzik", "संगीत एक्सेस की अनुमति दें", "Permitir acceso a la música"),
        arrayOf("Musix perlu izin untuk membaca file musik di perangkat Anda.", "Musix needs permission to read the music files on your device.", "يحتاج Musix إلى إذن لقراءة ملفات الموسيقى على جهازك.", "Musix memerlukan kebenaran untuk membaca fail muzik pada peranti anda.", "Musix को आपके डिवाइस की संगीत फ़ाइलें पढ़ने की अनुमति चाहिए।", "Musix necesita permiso para leer los archivos de música de tu dispositivo."),
        arrayOf("Buka pengaturan aplikasi", "Open app settings", "فتح إعدادات التطبيق", "Buka tetapan aplikasi", "ऐप सेटिंग्स खोलें", "Abrir ajustes de la app"),
        arrayOf("Ketuk untuk memutar musik", "Tap to play music", "اضغط لتشغيل الموسيقى", "Ketik untuk memainkan muzik", "संगीत चलाने के लिए टैप करें", "Toca para reproducir música"),
    )

    private fun init() {
        if (ready) return
        ready = true
        val all = ArrayList<Array<String>>()
        all += part0()
        all += part1()
        all += part2()
        all += part3()
        all += part4()
        for (r in all) {
            if (r[0].contains("{}")) templates += r[0] to r else exact[r[0]] = r
        }
        templates.sortByDescending { it.first.replace("{}", "").length }
    }

    private fun tpls(i: Int): List<Tpl> = tplCache.getOrPut(i) {
        templates.map { (k, r) ->
            val pieces = k.split("{}")
            val rx = pieces.joinToString("(.+?)") { Regex.escape(it) }
            Tpl(Regex("^" + rx + "$", RegexOption.DOT_MATCHES_ALL), r[i])
        }
    }

    fun tr(s: String): String {
        val i = index
        if (i < 0 || s.isEmpty()) return s
        init()
        if (cacheCode != code) { cache.clear(); cacheCode = code }
        cache[s]?.let { return it }
        val out = translate(s, i)
        cache[s] = out
        return out
    }

    private fun translate(s: String, i: Int, depth: Int = 0): String {
        exact[s]?.let { return it[i] }
        if (depth > 3) return s
        for (t in tpls(i)) {
            val m = t.regex.matchEntire(s) ?: continue
            var res = t.out
            for (g in 1 until m.groupValues.size) {
                val v = m.groupValues[g]
                val tv = if (v.any { it.isDigit() } && v.none { it.isLetter() }) v else translate(v, i, depth + 1)
                res = res.replaceFirst("{}", tv)
            }
            return res
        }
        return s
    }
}

fun tr(s: String): String = AppLang.tr(s)

/** Toast yang ikut diterjemahkan. */
object TrToast {
    const val LENGTH_SHORT = AndroidToast.LENGTH_SHORT
    const val LENGTH_LONG = AndroidToast.LENGTH_LONG
    fun makeText(ctx: Context, text: CharSequence, duration: Int): AndroidToast =
        AndroidToast.makeText(ctx, tr(text.toString()), duration)
}

/** Menggantikan material3.Text di package ini: teks otomatis diterjemahkan. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle,
        fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}
