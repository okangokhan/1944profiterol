/**
 * @OnlyCurrentDoc
 *
 * ════════════════════════════════════════════════════════════════════
 *  GÖREV TAKİP — Personel Günlük Rutin Takibi (Google Apps Script)
 * ════════════════════════════════════════════════════════════════════
 *
 *  Bu dosya iki bölümden oluşur:
 *    BÖLÜM 1 — Web uygulaması (doGet, doğrulama, günlük veri, kayıt)
 *    BÖLÜM 2 — Yönetici özellikleri (menü, link üretici, özet sayfası)
 *
 *  @OnlyCurrentDoc etiketi, betiğin yalnızca bağlı olduğu bu e-tabloya
 *  erişim izni istemesini sağlar (Drive'daki diğer dosyalara değil).
 *
 *  ÖNEMLİ GÜVENLİK NOTU:
 *  Apps Script'te adı alt çizgi (_) ile BİTMEYEN her global fonksiyon,
 *  web sayfasından google.script.run ile çağrılabilir. Bu yüzden:
 *    • Yardımcı fonksiyonların hepsi "_" ile biter (istemciden çağrılamaz).
 *    • İstemciye açık olan yalnızca iki fonksiyon vardır:
 *        gunlukVeriGetir(kod)  ve  gorevleriKaydet(kod, idler, not, gun)
 *      İkisi de personel kodunu her çağrıda sunucuda yeniden doğrular.
 *    • Menü/kurulum fonksiyonları yoneticiKontrol_() ile korunur; web
 *      uygulaması üzerinden anonim olarak tetiklenemez.
 */


/* ════════════════════════════════════════════════════════════════════
 *  YAPILANDIRMA
 * ════════════════════════════════════════════════════════════════════ */

const CONFIG = Object.freeze({
  UYGULAMA_ADI: 'Görev Takip',
  SAAT_DILIMI: 'Europe/Istanbul',

  SAYFA: Object.freeze({
    PERSONEL: 'Personel',
    GOREVLER: 'Gorevler',
    KAYITLAR: 'Kayitlar',
    OZET: 'Ozet'
  }),

  BASLIK: Object.freeze({
    PERSONEL: ['Kod', 'Ad Soyad', 'Aktif', 'Kişisel Link'],
    GOREVLER: ['Görev Adı', 'Kategori', 'Atanan Personel', 'Aktif', 'Sıra'],
    KAYITLAR: ['Zaman Damgası', 'Tarih', 'Personel Kodu', 'Personel Adı',
               'Görev Adı', 'Kategori', 'Durum', 'Not']
  }),

  // Sütun indeksleri (0 tabanlı; getValues() dizisindeki konum)
  SUTUN: Object.freeze({
    PERSONEL: Object.freeze({ KOD: 0, AD: 1, AKTIF: 2, LINK: 3 }),
    GOREVLER: Object.freeze({ AD: 0, KATEGORI: 1, ATANAN: 2, AKTIF: 3, SIRA: 4 }),
    KAYITLAR: Object.freeze({ ZAMAN: 0, TARIH: 1, KOD: 2, AD: 3, GOREV: 4,
                              KATEGORI: 5, DURUM: 6, NOT: 7 })
  }),

  DURUM: Object.freeze({ TAMAM: 'Tamamlandı', YAPILMADI: 'Yapılmadı' }),

  // Kategoriler bu sırayla gösterilir; listede olmayanlar sona eklenir.
  KATEGORI_SIRASI: ['Açılış', 'Gün İçi', 'Kapanış'],
  VARSAYILAN_KATEGORI: 'Genel',

  BICIM: Object.freeze({
    ZAMAN: 'dd.MM.yyyy HH:mm',
    TARIH: 'dd.MM.yyyy',
    METIN: '@'
  }),

  KOD_UZUNLUGU: 6,             // Yeni üretilen personel kodlarının uzunluğu
  ONBELLEK_SURE_SN: 300,       // CacheService süresi (5 dakika)
  ONBELLEK_ANAHTAR: Object.freeze({ PERSONEL: 'gt_personel_v1', GOREVLER: 'gt_gorevler_v1' }),
  KILIT_BEKLEME_MS: 30000,     // LockService en fazla bu kadar bekler
  NOT_MAKS_UZUNLUK: 1000,
  WEBAPP_URL_OZELLIK: 'WEBAPP_URL',
  YONETICI_OZELLIK: 'YONETICI_KODLARI', // Yönetici kodları Script Properties'te saklanır (tabloda değil)
  YONETICI_KOD_UZUNLUGU: 24,
  TREND_GUN: 7,                // Yönetici panelindeki eğilim grafiği kaç günü gösterir

  RENK: Object.freeze({ BASLIK_ARKA: '#4F46E5', BASLIK_YAZI: '#FFFFFF' })
});

const AYLAR_ = ['Ocak', 'Şubat', 'Mart', 'Nisan', 'Mayıs', 'Haziran',
                'Temmuz', 'Ağustos', 'Eylül', 'Ekim', 'Kasım', 'Aralık'];
const GUNLER_ = ['Pazartesi', 'Salı', 'Çarşamba', 'Perşembe', 'Cuma', 'Cumartesi', 'Pazar'];


/* ════════════════════════════════════════════════════════════════════
 *  BÖLÜM 1 — WEB UYGULAMASI
 * ════════════════════════════════════════════════════════════════════ */

/**
 * Web uygulamasının giriş noktası.
 * Personel kodu URL'deki ?k= parametresinden SUNUCUDA okunur
 * (istemcideki window.location iframe nedeniyle güvenilir değildir),
 * Personel sayfasıyla doğrulanır ve sonuç HtmlTemplate'e aktarılır.
 * Geçersiz kodda istemciye hiçbir personel/görev bilgisi gönderilmez.
 */
function doGet(e) {
  // ?y=... → yönetici paneli (ayrı sayfa, ayrı kod)
  if (e && e.parameter && e.parameter.y) return yoneticiSayfasi_(String(e.parameter.y));

  const hamKod = (e && e.parameter && e.parameter.k) ? String(e.parameter.k) : '';
  const personel = personelDogrula_(hamKod);

  let baslangic;
  if (personel) {
    const simdi = new Date();
    baslangic = {
      gecerli: true,
      kod: personel.kod,
      ad: personel.ad.split(/\s+/)[0],   // "Hoş geldin, Ayşe"
      tamAd: personel.ad,
      gun: gunAnahtari_(simdi),
      tarihMetni: tarihMetni_(simdi)
    };
  } else {
    baslangic = { gecerli: false };
  }

  const sablon = HtmlService.createTemplateFromFile('index');
  sablon.baslangicJson = guvenliJson_(baslangic);

  return sablon.evaluate()
    .setTitle(CONFIG.UYGULAMA_ADI)
    // GAS'ta HTML içindeki viewport etiketi yok sayılır; burada eklenmelidir.
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .addMetaTag('mobile-web-app-capable', 'yes')
    .addMetaTag('apple-mobile-web-app-capable', 'yes')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.DEFAULT);
}

/**
 * [İSTEMCİYE AÇIK] Personelin bugünkü görev listesini ve bugün daha önce
 * kaydedilmiş durumunu döndürür. Sayfa açıldığında çağrılır.
 *
 * Dönen yapı:
 * {
 *   gun: 'yyyy-MM-dd', tarihMetni: '8 Ekim 2026, Perşembe',
 *   kategoriler: [{ ad: 'Açılış', gorevler: [{ id, ad }] }],
 *   toplam: 8, tamamlananlar: ['Açılış::Kasayı aç', ...],
 *   not: '...', sonKayit: '14:32' | null
 * }
 */
function gunlukVeriGetir(kod) {
  try {
    const personel = personelDogrula_(kod);
    if (!personel) throw kullaniciHatasi_('Oturumunuz geçersiz. Lütfen kişisel linkinizle yeniden açın.');

    const simdi = new Date();
    const gun = gunAnahtari_(simdi);
    const gorevler = atanmisGorevler_(personel, false);
    const bugunku = bugunkuKayitlar_(personel.kod, gun);

    // Bugün "Tamamlandı" kaydedilmiş ve hâlâ atanmış olan görevler işaretli gelir.
    const gecerliIdler = new Set(gorevler.map(function (g) { return g.id; }));
    const tamamlananlar = bugunku.kayitlar
      .filter(function (k) { return k.tamam && gecerliIdler.has(k.id); })
      .map(function (k) { return k.id; });

    return {
      gun: gun,
      tarihMetni: tarihMetni_(simdi),
      kategoriler: kategorilereGrupla_(gorevler),
      toplam: gorevler.length,
      tamamlananlar: tamamlananlar,
      not: bugunku.not,
      sonKayit: bugunku.sonZaman
        ? Utilities.formatDate(bugunku.sonZaman, CONFIG.SAAT_DILIMI, 'HH:mm')
        : null
    };
  } catch (hata) {
    throw istemciHatasi_(hata, 'Görevler yüklenirken bir sorun oluştu.');
  }
}

/**
 * [İSTEMCİYE AÇIK] Personelin bugünkü görev durumunu kaydeder.
 *
 * Mantık (ekleme değil GÜNCELLEME):
 *   1. Kod sunucuda yeniden doğrulanır.
 *   2. Görev listesi tablodan TAZE okunur; istemciden gelen kimliklerden
 *      yalnızca bu personele atanmış aktif görevlere ait olanlar kabul edilir.
 *   3. ScriptLock alınır → aynı personelin bugüne ait eski satırları silinir
 *      → atanmış TÜM görevler (Tamamlandı / Yapılmadı) tek setValues ile yazılır.
 *
 * @param {string}   kod            Personel kodu
 * @param {string[]} tamamlananIdler İşaretli görev kimlikleri ("Kategori::Görev")
 * @param {string}   not            İsteğe bağlı genel not
 * @param {string}   istemciGunu    Sayfanın yüklendiği gün (yyyy-MM-dd)
 */
function gorevleriKaydet(kod, tamamlananIdler, not, istemciGunu) {
  try {
    const personel = personelDogrula_(kod);
    if (!personel) throw kullaniciHatasi_('Oturumunuz geçersiz veya hesabınız pasif. Lütfen yöneticinizle görüşün.');

    // ── Girdi doğrulama ──
    if (!Array.isArray(tamamlananIdler)) tamamlananIdler = [];
    const istenen = new Set(
      tamamlananIdler
        .slice(0, 1000)
        .filter(function (x) { return typeof x === 'string' && x.length <= 500; })
    );
    const temizNot = notuTemizle_(not);

    const simdi = new Date();
    const gun = gunAnahtari_(simdi);

    // Sayfa dün açılıp gece yarısından sonra kaydedilirse eski listeyi bugüne yazmayalım.
    if (istemciGunu && String(istemciGunu) !== gun) {
      return { basarili: false, gunDegisti: true, tarihMetni: tarihMetni_(simdi) };
    }

    // Görevler kayıt sırasında önbellekten DEĞİL, doğrudan tablodan okunur.
    const gorevler = atanmisGorevler_(personel, true);
    if (gorevler.length === 0) throw kullaniciHatasi_('Size atanmış aktif görev bulunmuyor.');

    const tarih = Utilities.parseDate(gun, CONFIG.SAAT_DILIMI, 'yyyy-MM-dd'); // İstanbul gece yarısı
    let tamamSayisi = 0;
    const satirlar = gorevler.map(function (g, i) {
      const tamam = istenen.has(g.id);
      if (tamam) tamamSayisi++;
      return [
        simdi,                    // A: Zaman Damgası (gerçek tarih-saat)
        tarih,                    // B: Tarih (gerçek tarih)
        personel.kod,             // C: Personel Kodu
        personel.ad,              // D: Personel Adı
        g.ad,                     // E: Görev Adı
        g.kategori,               // F: Kategori
        tamam ? CONFIG.DURUM.TAMAM : CONFIG.DURUM.YAPILMADI, // G: Durum
        i === 0 ? temizNot : ''   // H: Not — yalnızca günün İLK satırına (bkz. rehber)
      ];
    });

    // ── Kilitli bölge: sil + yaz atomik olmalı ──
    const kilit = LockService.getScriptLock();
    if (!kilit.tryLock(CONFIG.KILIT_BEKLEME_MS)) {
      throw kullaniciHatasi_('Sistem şu anda yoğun. Lütfen birkaç saniye sonra tekrar deneyin.');
    }
    try {
      const sayfa = sayfaGetir_(CONFIG.SAYFA.KAYITLAR, CONFIG.BASLIK.KAYITLAR);

      // 1) Aynı personelin bugüne ait eski satırlarını bul ve sil
      const sonSatir = sayfa.getLastRow();
      if (sonSatir >= 2) {
        const S = CONFIG.SUTUN.KAYITLAR;
        // Yalnızca Tarih (B) ve Kod (C) sütunlarını okumak yeterli → daha hızlı
        const degerler = sayfa.getRange(2, S.TARIH + 1, sonSatir - 1, 2).getValues();
        const silinecek = [];
        for (let i = 0; i < degerler.length; i++) {
          if (kodNormalize_(degerler[i][1]) === personel.kod && hucreGunAnahtari_(degerler[i][0]) === gun) {
            silinecek.push(i + 2);
          }
        }
        satirlariSil_(sayfa, silinecek);
      }

      // 2) Yeni durumu tek seferde yaz
      const baslangic = sayfa.getLastRow() + 1;
      const gereken = baslangic + satirlar.length - 1;
      if (sayfa.getMaxRows() < gereken) {
        sayfa.insertRowsAfter(sayfa.getMaxRows(), gereken - sayfa.getMaxRows());
      }
      const n = satirlar.length;
      // Kod sütunu metin biçiminde olmalı (ör. "12e4" sayıya dönüşmesin)
      sayfa.getRange(baslangic, CONFIG.SUTUN.KAYITLAR.KOD + 1, n, 1).setNumberFormat(CONFIG.BICIM.METIN);
      sayfa.getRange(baslangic, 1, n, satirlar[0].length).setValues(satirlar);
      sayfa.getRange(baslangic, CONFIG.SUTUN.KAYITLAR.ZAMAN + 1, n, 1).setNumberFormat(CONFIG.BICIM.ZAMAN);
      sayfa.getRange(baslangic, CONFIG.SUTUN.KAYITLAR.TARIH + 1, n, 1).setNumberFormat(CONFIG.BICIM.TARIH);

      SpreadsheetApp.flush(); // Kilit bırakılmadan önce yazımın tamamlandığından emin ol
    } finally {
      kilit.releaseLock();
    }

    return {
      basarili: true,
      tamamlanan: tamamSayisi,
      toplam: gorevler.length,
      saat: Utilities.formatDate(simdi, CONFIG.SAAT_DILIMI, 'HH:mm'),
      tarihMetni: tarihMetni_(simdi)
    };
  } catch (hata) {
    throw istemciHatasi_(hata, 'Kayıt sırasında bir sorun oluştu. Lütfen tekrar deneyin.');
  }
}


/* ────────────────────────────────────────────────────────────────────
 *  Personel ve görev verisi (önbellekli okuma)
 * ──────────────────────────────────────────────────────────────────── */

/**
 * Personel sayfasını { normalizeKod: {kod, ad, aktif} } biçiminde döndürür.
 * Sonuç 5 dk önbelleklenir. Bu harita ASLA istemciye gönderilmez.
 */
function personelHaritasi_() {
  const onbellek = CacheService.getScriptCache();
  const kayitli = onbellek.get(CONFIG.ONBELLEK_ANAHTAR.PERSONEL);
  if (kayitli) {
    try { return JSON.parse(kayitli); } catch (e) { /* bozuksa yeniden oku */ }
  }

  const sayfa = sayfaGetir_(CONFIG.SAYFA.PERSONEL, CONFIG.BASLIK.PERSONEL);
  const S = CONFIG.SUTUN.PERSONEL;
  const harita = {};
  const son = sayfa.getLastRow();
  if (son >= 2) {
    sayfa.getRange(2, 1, son - 1, 3).getValues().forEach(function (r) {
      const kod = kodNormalize_(r[S.KOD]);
      const ad = String(r[S.AD] || '').trim();
      if (kod && ad && !harita[kod]) {
        harita[kod] = { kod: kod, ad: ad, aktif: evetMi_(r[S.AKTIF]) };
      }
    });
  }
  onbellegeYaz_(CONFIG.ONBELLEK_ANAHTAR.PERSONEL, harita);
  return harita;
}

/**
 * Verilen kodu doğrular. Geçerli ve AKTİF personel ise {kod, ad} döner,
 * aksi halde null. Biçimsiz kodlar tabloya bakılmadan reddedilir.
 */
function personelDogrula_(hamKod) {
  const kod = kodNormalize_(hamKod);
  if (!kod || !/^[a-z0-9]{3,32}$/.test(kod)) return null;
  const p = personelHaritasi_()[kod];
  return (p && p.aktif) ? { kod: p.kod, ad: p.ad } : null;
}

/**
 * Gorevler sayfasındaki AKTİF görevleri döndürür.
 * @param {boolean} taze true ise önbellek atlanır (kayıt sırasında kullanılır).
 */
function aktifGorevler_(taze) {
  const onbellek = CacheService.getScriptCache();
  if (!taze) {
    const kayitli = onbellek.get(CONFIG.ONBELLEK_ANAHTAR.GOREVLER);
    if (kayitli) {
      try { return JSON.parse(kayitli); } catch (e) { /* yeniden oku */ }
    }
  }

  const sayfa = sayfaGetir_(CONFIG.SAYFA.GOREVLER, CONFIG.BASLIK.GOREVLER);
  const S = CONFIG.SUTUN.GOREVLER;
  const liste = [];
  const son = sayfa.getLastRow();
  if (son >= 2) {
    sayfa.getRange(2, 1, son - 1, CONFIG.BASLIK.GOREVLER.length).getValues().forEach(function (r, i) {
      const ad = String(r[S.AD] || '').trim();
      if (!ad || !evetMi_(r[S.AKTIF])) return;
      const siraSayi = Number(r[S.SIRA]);
      liste.push({
        ad: ad,
        kategori: String(r[S.KATEGORI] || '').trim() || CONFIG.VARSAYILAN_KATEGORI,
        // "Ayşe Yılmaz, Mehmet Demir" → ['ayşe yılmaz', 'mehmet demir']
        atanan: String(r[S.ATANAN] || '').split(/[,;\n]/).map(metinNormalize_).filter(String),
        sira: (r[S.SIRA] === '' || isNaN(siraSayi)) ? 1e9 : siraSayi,
        satir: i
      });
    });
  }
  onbellegeYaz_(CONFIG.ONBELLEK_ANAHTAR.GOREVLER, liste);
  return liste;
}

/**
 * Personele atanmış aktif görevleri kategori sırası → Sıra → satır sırasına
 * göre sıralı döndürür. Atanan Personel boşsa görev herkese aittir.
 * Atanan sütununa ad soyad yerine personel kodu da yazılabilir.
 */
function atanmisGorevler_(personel, taze) {
  const adN = metinNormalize_(personel.ad);
  const gorulen = new Set();

  const liste = aktifGorevler_(taze).filter(function (g) {
    const bana = g.atanan.length === 0 || g.atanan.indexOf(adN) !== -1 || g.atanan.indexOf(personel.kod) !== -1;
    if (!bana) return false;
    const id = gorevId_(g.kategori, g.ad);
    if (gorulen.has(id)) return false; // aynı kategoride aynı adlı mükerrer satırı atla
    gorulen.add(id);
    return true;
  }).map(function (g) {
    return { id: gorevId_(g.kategori, g.ad), ad: g.ad, kategori: g.kategori, sira: g.sira, satir: g.satir };
  });

  // Kategori sıralama anahtarı: önce CONFIG.KATEGORI_SIRASI, sonra en küçük Sıra değeri
  const bilinen = CONFIG.KATEGORI_SIRASI.map(metinNormalize_);
  const katAnahtar = {};
  liste.forEach(function (g) {
    const k = g.kategori;
    const idx = bilinen.indexOf(metinNormalize_(k));
    if (!katAnahtar[k]) katAnahtar[k] = { idx: idx === -1 ? 999 : idx, minSira: g.sira, ilkSatir: g.satir };
    katAnahtar[k].minSira = Math.min(katAnahtar[k].minSira, g.sira);
    katAnahtar[k].ilkSatir = Math.min(katAnahtar[k].ilkSatir, g.satir);
  });

  liste.sort(function (a, b) {
    const ka = katAnahtar[a.kategori], kb = katAnahtar[b.kategori];
    return (ka.idx - kb.idx) || (ka.minSira - kb.minSira) || (ka.ilkSatir - kb.ilkSatir) ||
           (a.sira - b.sira) || (a.satir - b.satir);
  });
  return liste;
}

/** Sıralı görev listesini istemcinin beklediği kategori gruplarına çevirir. */
function kategorilereGrupla_(gorevler) {
  const gruplar = [];
  const harita = {};
  gorevler.forEach(function (g) {
    if (!harita[g.kategori]) {
      harita[g.kategori] = { ad: g.kategori, gorevler: [] };
      gruplar.push(harita[g.kategori]);
    }
    harita[g.kategori].gorevler.push({ id: g.id, ad: g.ad });
  });
  return gruplar;
}

/**
 * Kayitlar sayfasından personelin verilen güne ait satırlarını okur.
 * Önbellek KULLANILMAZ; her zaman tablodaki güncel veri döner.
 */
function bugunkuKayitlar_(kod, gun) {
  const sonuc = { kayitlar: [], not: '', sonZaman: null };
  const sayfa = sayfaGetir_(CONFIG.SAYFA.KAYITLAR, CONFIG.BASLIK.KAYITLAR);
  const son = sayfa.getLastRow();
  if (son < 2) return sonuc;

  const S = CONFIG.SUTUN.KAYITLAR;
  const veriler = sayfa.getRange(2, 1, son - 1, CONFIG.BASLIK.KAYITLAR.length).getValues();
  veriler.forEach(function (r) {
    if (kodNormalize_(r[S.KOD]) !== kod || hucreGunAnahtari_(r[S.TARIH]) !== gun) return;
    sonuc.kayitlar.push({
      id: gorevId_(String(r[S.KATEGORI] || '').trim() || CONFIG.VARSAYILAN_KATEGORI, String(r[S.GOREV] || '').trim()),
      tamam: metinNormalize_(r[S.DURUM]) === metinNormalize_(CONFIG.DURUM.TAMAM)
    });
    const not = String(r[S.NOT] || '').trim();
    if (not && !sonuc.not) sonuc.not = not;
    if (r[S.ZAMAN] instanceof Date && (!sonuc.sonZaman || r[S.ZAMAN] > sonuc.sonZaman)) {
      sonuc.sonZaman = r[S.ZAMAN];
    }
  });
  return sonuc;
}


/* ────────────────────────────────────────────────────────────────────
 *  Yardımcılar (tarih, metin, sayfa, hata)
 * ──────────────────────────────────────────────────────────────────── */

/** Günü İstanbul saatine göre 'yyyy-MM-dd' anahtarına çevirir (karşılaştırma için). */
function gunAnahtari_(tarih) {
  return Utilities.formatDate(tarih || new Date(), CONFIG.SAAT_DILIMI, 'yyyy-MM-dd');
}

/**
 * Bir hücre değerini gün anahtarına çevirir. Gerçek tarih değerlerini ve
 * elle yazılmış "dd.MM.yyyy" metinlerini destekler.
 */
function hucreGunAnahtari_(deger) {
  if (deger instanceof Date && !isNaN(deger.getTime())) return gunAnahtari_(deger);
  const m = String(deger || '').trim().match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/);
  if (!m) return '';
  return m[3] + '-' + ('0' + m[2]).slice(-2) + '-' + ('0' + m[1]).slice(-2);
}

/** "8 Ekim 2026, Perşembe" biçiminde Türkçe tarih metni (İstanbul saatine göre). */
function tarihMetni_(tarih) {
  const tz = CONFIG.SAAT_DILIMI;
  const gun = Number(Utilities.formatDate(tarih, tz, 'd'));
  const ay = Number(Utilities.formatDate(tarih, tz, 'M'));
  const yil = Utilities.formatDate(tarih, tz, 'yyyy');
  const haftaGunu = Number(Utilities.formatDate(tarih, tz, 'u')); // 1 = Pazartesi … 7 = Pazar
  return gun + ' ' + AYLAR_[ay - 1] + ' ' + yil + ', ' + GUNLER_[haftaGunu - 1];
}

/** Türkçe karakterlere duyarlı küçük harfe çevirme + boşluk sadeleştirme. */
function metinNormalize_(s) {
  return String(s == null ? '' : s)
    .replace(/İ/g, 'i').replace(/I/g, 'ı')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

/** Personel kodunu karşılaştırma için normalize eder. */
function kodNormalize_(s) {
  return String(s == null ? '' : s).trim().toLowerCase();
}

/** "Evet", "E", TRUE (onay kutusu), "Aktif" gibi değerleri true kabul eder. */
function evetMi_(v) {
  if (v === true) return true;
  const n = metinNormalize_(v);
  return n === 'evet' || n === 'e' || n === 'true' || n === '1' || n === 'aktif';
}

/** Görevin benzersiz kimliği: aynı adlı görev farklı kategorilerde olabilir. */
function gorevId_(kategori, ad) {
  return kategori + '::' + ad;
}

/**
 * Kullanıcı notunu temizler: uzunluk sınırı, kontrol karakterleri ve
 * formül enjeksiyonu (=, +, -, @ ile başlayan metin) koruması.
 */
function notuTemizle_(not) {
  let s = (typeof not === 'string') ? not : '';
  s = s.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '').trim();
  if (s.length > CONFIG.NOT_MAKS_UZUNLUK) s = s.slice(0, CONFIG.NOT_MAKS_UZUNLUK);
  if (/^[=+\-@]/.test(s)) s = "'" + s;
  return s;
}

/**
 * Nesneyi HTML <script> içine güvenle gömülebilecek JSON'a çevirir
 * ("</script>" gibi dizilerin sayfayı bozmasını engeller).
 */
function guvenliJson_(nesne) {
  return JSON.stringify(nesne)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

/** Önbelleğe yazar; 100 KB sınırı aşılırsa sessizce atlar (veri tablodan okunmaya devam eder). */
function onbellegeYaz_(anahtar, deger) {
  try {
    CacheService.getScriptCache().put(anahtar, JSON.stringify(deger), CONFIG.ONBELLEK_SURE_SN);
  } catch (e) {
    console.warn('Önbelleğe yazılamadı (' + anahtar + '): ' + e);
  }
}

/** Personel ve görev önbelleğini temizler (tabloda değişiklik yapılınca). */
function onbellekTemizle_() {
  CacheService.getScriptCache().removeAll([CONFIG.ONBELLEK_ANAHTAR.PERSONEL, CONFIG.ONBELLEK_ANAHTAR.GOREVLER]);
}

/** Sayfayı getirir; yoksa başlıklarıyla oluşturur. */
function sayfaGetir_(ad, basliklar) {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sayfa = ss.getSheetByName(ad);
  if (!sayfa) {
    sayfa = ss.insertSheet(ad);
    basliklariYaz_(sayfa, basliklar);
  }
  return sayfa;
}

/** Başlık satırını yazar ve biçimlendirir. */
function basliklariYaz_(sayfa, basliklar) {
  sayfa.getRange(1, 1, 1, basliklar.length)
    .setValues([basliklar])
    .setFontWeight('bold')
    .setBackground(CONFIG.RENK.BASLIK_ARKA)
    .setFontColor(CONFIG.RENK.BASLIK_YAZI)
    .setVerticalAlignment('middle');
  sayfa.setFrozenRows(1);
  sayfa.setRowHeight(1, 32);
}

/**
 * Verilen satır numaralarını, ardışık blokları tek deleteRows çağrısıyla ve
 * aşağıdan yukarıya doğru silerek kaldırır (satır kayması olmaz).
 */
function satirlariSil_(sayfa, satirNolari) {
  if (!satirNolari.length) return;
  // Sheets "dondurulmamış tüm satırları silme" işlemine izin vermez → gerekirse boş satır ekle
  if (satirNolari.length >= sayfa.getMaxRows() - sayfa.getFrozenRows()) {
    sayfa.insertRowsAfter(sayfa.getMaxRows(), 1);
  }
  const sirali = satirNolari.slice().sort(function (a, b) { return b - a; });
  let blokSon = sirali[0];
  let blokBas = sirali[0];
  for (let i = 1; i <= sirali.length; i++) {
    if (i < sirali.length && sirali[i] === blokBas - 1) {
      blokBas = sirali[i];
    } else {
      sayfa.deleteRows(blokBas, blokSon - blokBas + 1);
      if (i < sirali.length) { blokSon = sirali[i]; blokBas = sirali[i]; }
    }
  }
}

/** Kullanıcıya olduğu gibi gösterilebilecek bir hata oluşturur. */
function kullaniciHatasi_(mesaj) {
  const h = new Error(mesaj);
  h.kullaniciyaGoster = true;
  return h;
}

/**
 * Sunucu hatasını istemciye uygun hale getirir: kullanıcı hataları aynen
 * iletilir, teknik hatalar günlüğe yazılıp genel bir mesajla gizlenir.
 */
function istemciHatasi_(hata, genelMesaj) {
  if (hata && hata.kullaniciyaGoster) return new Error(hata.message);
  console.error(hata && hata.stack ? hata.stack : hata);
  return new Error(genelMesaj);
}


/* ════════════════════════════════════════════════════════════════════
 *  KURULUM
 * ════════════════════════════════════════════════════════════════════ */

/**
 * İlk kurulum. Apps Script düzenleyicisinden bir kez çalıştırın.
 *  • Eksik sayfaları başlıklarıyla oluşturur (mevcut veriye DOKUNMAZ).
 *  • Personel ve Gorevler boşsa örnek veri ekler (örnek personele rastgele kod üretir).
 *  • Doğrulama listeleri, hücre biçimleri ve e-tablo saat dilimini ayarlar.
 * Tekrar çalıştırmak güvenlidir.
 */
function kurulum() {
  yoneticiKontrol_();
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  ss.setSpreadsheetTimeZone(CONFIG.SAAT_DILIMI); // tarih hücreleri İstanbul saatine göre görünsün
  const rapor = [];

  // ── Personel ──
  const personel = kurulumSayfasi_(ss, CONFIG.SAYFA.PERSONEL, CONFIG.BASLIK.PERSONEL, rapor);
  personel.getRange('A:A').setNumberFormat(CONFIG.BICIM.METIN);
  if (personel.getLastRow() < 2) {
    const mevcut = new Set();
    const ornek = [
      [kodUret_(mevcut), 'Ayşe Yılmaz', 'Evet', ''],
      [kodUret_(mevcut), 'Mehmet Demir', 'Evet', '']
    ];
    personel.getRange(2, 1, ornek.length, ornek[0].length).setValues(ornek);
    rapor.push('Örnek personel eklendi: ' + ornek.map(function (r) { return r[1] + ' (' + r[0] + ')'; }).join(', '));
  }
  listeDogrulamasi_(personel.getRange('C2:C'), ['Evet', 'Hayır'], false);
  personel.setColumnWidth(1, 90).setColumnWidth(2, 180).setColumnWidth(3, 70).setColumnWidth(4, 420);

  // ── Gorevler ──
  const gorevler = kurulumSayfasi_(ss, CONFIG.SAYFA.GOREVLER, CONFIG.BASLIK.GOREVLER, rapor);
  if (gorevler.getLastRow() < 2) {
    const ornek = [
      ['Kasayı aç, bozuk para kontrolü', 'Açılış', '', 'Evet', 1],
      ['Vitrin dolabı ısısını kontrol et', 'Açılış', '', 'Evet', 2],
      ['Masa ve tezgâhları sil', 'Açılış', '', 'Evet', 3],
      ['Profiterol ve sos stoklarını kontrol et', 'Gün İçi', '', 'Evet', 1],
      ['Lavabo / tuvalet temizliği', 'Gün İçi', 'Mehmet Demir', 'Evet', 2],
      ['Kasa sayımı ve gün sonu raporu', 'Kapanış', 'Ayşe Yılmaz', 'Evet', 1],
      ['Yerleri paspasla', 'Kapanış', '', 'Evet', 2],
      ['Cihazları kapat, ışıkları kontrol et', 'Kapanış', '', 'Evet', 3]
    ];
    gorevler.getRange(2, 1, ornek.length, ornek[0].length).setValues(ornek);
    rapor.push(ornek.length + ' örnek görev eklendi.');
  }
  listeDogrulamasi_(gorevler.getRange('B2:B'), CONFIG.KATEGORI_SIRASI, true);
  listeDogrulamasi_(gorevler.getRange('D2:D'), ['Evet', 'Hayır'], false);
  gorevler.setColumnWidth(1, 300).setColumnWidth(2, 110).setColumnWidth(3, 220)
          .setColumnWidth(4, 70).setColumnWidth(5, 60);

  // ── Kayitlar ──
  const kayitlar = kurulumSayfasi_(ss, CONFIG.SAYFA.KAYITLAR, CONFIG.BASLIK.KAYITLAR, rapor);
  kayitlar.getRange('A2:A').setNumberFormat(CONFIG.BICIM.ZAMAN);
  kayitlar.getRange('B2:B').setNumberFormat(CONFIG.BICIM.TARIH);
  kayitlar.getRange('C2:C').setNumberFormat(CONFIG.BICIM.METIN);
  listeDogrulamasi_(kayitlar.getRange('G2:G'), [CONFIG.DURUM.TAMAM, CONFIG.DURUM.YAPILMADI], false);
  kayitlar.setColumnWidth(1, 140).setColumnWidth(2, 95).setColumnWidth(3, 95).setColumnWidth(4, 150)
          .setColumnWidth(5, 280).setColumnWidth(6, 90).setColumnWidth(7, 100).setColumnWidth(8, 280);

  // ── Ozet ──
  if (!ss.getSheetByName(CONFIG.SAYFA.OZET)) {
    ozetSayfasiHazirla_();
    rapor.push('"' + CONFIG.SAYFA.OZET + '" sayfası oluşturuldu.');
  }

  // Boş varsayılan sayfayı (Sayfa1 / Sheet1) kaldır
  ['Sayfa1', 'Sheet1'].forEach(function (ad) {
    const s = ss.getSheetByName(ad);
    if (s && s.getLastRow() === 0 && s.getLastColumn() === 0 && ss.getSheets().length > 1) {
      ss.deleteSheet(s);
    }
  });

  onbellekTemizle_();
  rapor.push('Kurulum tamamlandı. Sonraki adım: Web uygulaması olarak dağıtın ve "Görev Takip > Personel Linklerini Oluştur" menüsünü kullanın.');
  bildir_('Kurulum', rapor.join('\n'));
}

/** Sayfa yoksa oluşturur, varsa ve başlık satırı boşsa başlıkları yazar. */
function kurulumSayfasi_(ss, ad, basliklar, rapor) {
  let sayfa = ss.getSheetByName(ad);
  if (!sayfa) {
    sayfa = ss.insertSheet(ad);
    basliklariYaz_(sayfa, basliklar);
    rapor.push('"' + ad + '" sayfası oluşturuldu.');
  } else if (sayfa.getRange(1, 1).getValue() === '') {
    basliklariYaz_(sayfa, basliklar);
    rapor.push('"' + ad + '" sayfasına başlıklar yazıldı.');
  }
  return sayfa;
}

/** Bir aralığa açılır liste doğrulaması ekler. */
function listeDogrulamasi_(aralik, degerler, gecersizeIzinVer) {
  aralik.setDataValidation(
    SpreadsheetApp.newDataValidation()
      .requireValueInList(degerler, true)
      .setAllowInvalid(!!gecersizeIzinVer)
      .build()
  );
}

/**
 * Tahmin edilmesi zor, kısa ve okunaklı bir personel kodu üretir.
 * Karışabilen karakterler (0/o, 1/l/i) ve "e" harfi (Sheets'in "12e4" gibi
 * değerleri sayıya çevirmesini önlemek için) kullanılmaz; ilk karakter harftir.
 * 6 karakter ≈ 530 milyon olasılık.
 */
function kodUret_(mevcutKodlar, uzunluk) {
  uzunluk = uzunluk || CONFIG.KOD_UZUNLUGU;
  const harfler = 'abcdfghjkmnpqrstuvwxyz';
  const tumu = harfler + '23456789';
  for (let deneme = 0; deneme < 100; deneme++) {
    const bayt = Utilities.computeDigest(
      Utilities.DigestAlgorithm.SHA_256,
      Utilities.getUuid() + ':' + Math.random() + ':' + Date.now()
    );
    let kod = harfler.charAt((bayt[0] & 0xff) % harfler.length);
    for (let i = 1; i < uzunluk; i++) {
      kod += tumu.charAt((bayt[i] & 0xff) % tumu.length);
    }
    if (!mevcutKodlar.has(kod)) {
      mevcutKodlar.add(kod);
      return kod;
    }
  }
  throw new Error('Benzersiz kod üretilemedi.');
}


/* ════════════════════════════════════════════════════════════════════
 *  BÖLÜM 2 — YÖNETİCİ ÖZELLİKLERİ
 *  (E-tablo menüsü, personel link üretici, özet sayfası)
 * ════════════════════════════════════════════════════════════════════ */

/** E-tablo açıldığında "Görev Takip" menüsünü ekler (basit tetikleyici). */
function onOpen() {
  try {
    SpreadsheetApp.getUi()
      .createMenu('Görev Takip')
      .addItem('Personel Linklerini Oluştur', 'menuLinkleriOlustur')
      .addItem('Özeti Güncelle', 'menuOzetiGuncelle')
      .addSeparator()
      .addItem('Yönetici Linki Oluştur', 'menuYoneticiLinkiOlustur')
      .addItem('Tüm Yönetici Linklerini İptal Et', 'menuYoneticiLinkleriniIptalEt')
      .addSeparator()
      .addItem('Web Uygulaması Adresini Değiştir', 'menuWebAdresiniDegistir')
      .addItem('Önbelleği Temizle', 'menuOnbellegiTemizle')
      .addItem('Kurulumu Çalıştır', 'kurulum')
      .addToUi();
  } catch (e) {
    // Web uygulaması bağlamında UI yoktur; yok say.
  }
}

/**
 * Basit tetikleyici: Personel/Gorevler sayfası düzenlenince önbelleği
 * temizler (değişiklik uygulamaya hemen yansısın); Ozet sayfasında tarih
 * hücresi değişince özeti yeniden hesaplar.
 */
function onEdit(e) {
  try {
    if (!e || !e.range || typeof e.range.getSheet !== 'function') return;
    const ad = e.range.getSheet().getName();
    if (ad === CONFIG.SAYFA.PERSONEL || ad === CONFIG.SAYFA.GOREVLER) {
      onbellekTemizle_();
    } else if (ad === CONFIG.SAYFA.OZET && e.range.getA1Notation() === 'B2') {
      ozetHesapla_();
    }
  } catch (hata) {
    console.error('onEdit hatası: ' + hata);
  }
}

/**
 * [MENÜ] Personel sayfasındaki herkese kişisel link yazar.
 * Kodu boş olan personele otomatik olarak yeni kod üretilir.
 */
function menuLinkleriOlustur() {
  yoneticiKontrol_();
  const ui = SpreadsheetApp.getUi();
  const url = webAppAdresi_(ui, false);
  if (!url) return;

  const sayfa = sayfaGetir_(CONFIG.SAYFA.PERSONEL, CONFIG.BASLIK.PERSONEL);
  const son = sayfa.getLastRow();
  if (son < 2) {
    ui.alert('Personel sayfasında kayıt yok. Önce personel ekleyin.');
    return;
  }

  const S = CONFIG.SUTUN.PERSONEL;
  const veriler = sayfa.getRange(2, 1, son - 1, CONFIG.BASLIK.PERSONEL.length).getValues();
  const mevcut = new Set(veriler.map(function (r) { return kodNormalize_(r[S.KOD]); }).filter(String));
  let yeniKod = 0, link = 0, pasif = 0;

  const kodlar = [], linkler = [];
  veriler.forEach(function (r) {
    const ad = String(r[S.AD] || '').trim();
    let kod = kodNormalize_(r[S.KOD]);
    if (ad && !kod) { kod = kodUret_(mevcut); yeniKod++; }
    kodlar.push([kod || r[S.KOD]]);
    if (ad && kod) {
      linkler.push([url + '?k=' + encodeURIComponent(kod)]);
      link++;
      if (!evetMi_(r[S.AKTIF])) pasif++;
    } else {
      linkler.push(['']);
    }
  });

  sayfa.getRange(2, S.KOD + 1, kodlar.length, 1).setNumberFormat(CONFIG.BICIM.METIN).setValues(kodlar);
  sayfa.getRange(2, S.LINK + 1, linkler.length, 1).setValues(linkler);
  onbellekTemizle_();

  ui.alert('Personel Linkleri',
    link + ' personel için link oluşturuldu.' +
    (yeniKod ? '\n' + yeniKod + ' kişiye yeni kod üretildi.' : '') +
    (pasif ? '\n' + pasif + ' kişi "Aktif: Hayır" durumunda; linkleri çalışmaz.' : '') +
    '\n\nLinkleri yalnızca ilgili kişiyle, kişiye özel olarak paylaşın.',
    ui.ButtonSet.OK);
}

/** [MENÜ] Kayıtlı web uygulaması adresini değiştirir. */
function menuWebAdresiniDegistir() {
  yoneticiKontrol_();
  const url = webAppAdresi_(SpreadsheetApp.getUi(), true);
  if (url) bildir_('Web Uygulaması Adresi', 'Kaydedildi:\n' + url + '\n\nLinkleri güncellemek için "Personel Linklerini Oluştur"u tekrar çalıştırın.');
}

/** [MENÜ] Önbelleği temizler. */
function menuOnbellegiTemizle() {
  yoneticiKontrol_();
  onbellekTemizle_();
  bildir_('Önbellek', 'Personel ve görev önbelleği temizlendi. Değişiklikler uygulamaya hemen yansıyacak.');
}

/** [MENÜ] Ozet sayfasını B2'deki tarihe göre yeniden hesaplar. */
function menuOzetiGuncelle() {
  yoneticiKontrol_();
  if (!SpreadsheetApp.getActiveSpreadsheet().getSheetByName(CONFIG.SAYFA.OZET)) ozetSayfasiHazirla_();
  ozetHesapla_();
  SpreadsheetApp.getActiveSpreadsheet().getSheetByName(CONFIG.SAYFA.OZET).activate();
}

/**
 * Web uygulamasının /exec adresini döndürür. Kayıtlı değilse (veya
 * zorla=true ise) kullanıcıdan ister ve Script Properties'e kaydeder.
 * ScriptApp.getService().getUrl() bazı durumlarda /dev adresi döndürdüğü
 * için adres yöneticiden bir kez alınıp saklanır.
 */
function webAppAdresi_(ui, zorla) {
  const ozellikler = PropertiesService.getScriptProperties();
  const kayitli = ozellikler.getProperty(CONFIG.WEBAPP_URL_OZELLIK);
  if (kayitli && !zorla) return kayitli;

  let tahmin = '';
  try { tahmin = ScriptApp.getService().getUrl() || ''; } catch (e) { /* dağıtım yok */ }
  if (!/\/exec$/.test(tahmin)) tahmin = '';

  const yanit = ui.prompt(
    'Web Uygulaması Adresi',
    'Dağıt > Dağıtımları yönet ekranındaki "Web uygulaması" URL\'sini yapıştırın.\n' +
    '(…/exec ile bitmelidir; /dev adresi personelde ÇALIŞMAZ.)' +
    (tahmin ? '\n\nBulunan adres:\n' + tahmin + '\n(Boş bırakırsanız bu adres kullanılır.)' : ''),
    ui.ButtonSet.OK_CANCEL
  );
  if (yanit.getSelectedButton() !== ui.Button.OK) return null;

  const url = (yanit.getResponseText().trim() || tahmin).split('?')[0];
  if (!/^https:\/\/script\.google\.com\/(a\/macros\/[^\/]+\/|macros\/)s\/[A-Za-z0-9_-]+\/exec$/.test(url)) {
    ui.alert('Geçersiz adres', 'Adres "https://script.google.com/macros/s/…/exec" biçiminde olmalıdır.', ui.ButtonSet.OK);
    return null;
  }
  ozellikler.setProperty(CONFIG.WEBAPP_URL_OZELLIK, url);
  return url;
}


/* ────────────────────────────────────────────────────────────────────
 *  Özet sayfası (script tabanlı)
 *
 *  Neden formül değil de script? Tarih karşılaştırması, Türkçe yerel
 *  ayardaki ; / , ayırıcı farkları, QUERY'nin tarih sözdizimi ve
 *  Kayitlar sayfasında satır silinmesi formülleri kırılgan yapar. Script
 *  her çalıştığında aynı mantıkla (aynı saat dilimiyle) hesaplar.
 * ──────────────────────────────────────────────────────────────────── */

/** Ozet sayfasını oluşturur ve sabit alanlarını biçimlendirir. */
function ozetSayfasiHazirla_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sayfa = ss.getSheetByName(CONFIG.SAYFA.OZET);
  if (!sayfa) sayfa = ss.insertSheet(CONFIG.SAYFA.OZET);

  sayfa.getRange('A1').setValue('Günlük Görev Özeti').setFontSize(16).setFontWeight('bold').setFontColor(CONFIG.RENK.BASLIK_ARKA);
  sayfa.getRange('A2').setValue('Tarih:').setFontWeight('bold').setHorizontalAlignment('right');
  sayfa.getRange('B2')
    .setValue(Utilities.parseDate(gunAnahtari_(), CONFIG.SAAT_DILIMI, 'yyyy-MM-dd'))
    .setNumberFormat(CONFIG.BICIM.TARIH)
    .setBackground('#EEF2FF').setFontWeight('bold')
    .setDataValidation(SpreadsheetApp.newDataValidation().requireDate().setAllowInvalid(false).build());
  sayfa.getRange('C2').setValue('← Tarihi değiştirin; özet otomatik güncellenir (veya menü: Görev Takip > Özeti Güncelle).')
    .setFontColor('#64748B').setFontStyle('italic');
  sayfa.setColumnWidth(1, 180).setColumnWidth(2, 100).setColumnWidth(3, 110).setColumnWidth(4, 80)
       .setColumnWidth(5, 80).setColumnWidth(6, 130).setColumnWidth(7, 320);
  sayfa.setFrozenRows(5);
  ozetHesapla_();
  return sayfa;
}

/**
 * Ozet!B2'deki tarih için personel bazında tamamlanan / toplam / yüzde
 * hesaplar ve 5. satırdan itibaren tabloya yazar. Kayıt göndermemiş aktif
 * personel de "Kayıt yok" olarak listelenir.
 */
function ozetHesapla_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sayfa = ss.getSheetByName(CONFIG.SAYFA.OZET);
  if (!sayfa) return;

  // Seçilen tarih (boş/geçersizse bugün)
  let gun = hucreGunAnahtari_(sayfa.getRange('B2').getValue());
  if (!gun) {
    gun = gunAnahtari_();
    sayfa.getRange('B2').setValue(Utilities.parseDate(gun, CONFIG.SAAT_DILIMI, 'yyyy-MM-dd'));
  }

  const liste = gunOzeti_(gun, kayitlariOku_());

  // Eski tabloyu temizle
  const basSatir = 5;
  const sutunSayisi = 7;
  const maks = sayfa.getMaxRows();
  if (maks >= basSatir) {
    sayfa.getRange(basSatir, 1, maks - basSatir + 1, sutunSayisi).clear();
  }
  sayfa.clearConditionalFormatRules();

  // Başlık + satırlar + toplam
  const tablo = [['Personel', 'Kod', 'Tamamlanan', 'Toplam', 'Yüzde', 'Son Kayıt', 'Not']];
  let genelTamam = 0, genelToplam = 0;
  liste.forEach(function (o) {
    genelTamam += o.tamam;
    genelToplam += o.toplam;
    tablo.push([
      o.ad, o.kod, o.tamam, o.toplam,
      o.toplam ? o.tamam / o.toplam : '',
      o.kayitYok ? 'Kayıt yok' : (o.son || ''),
      o.not
    ]);
  });
  tablo.push(['TOPLAM', '', genelTamam, genelToplam, genelToplam ? genelTamam / genelToplam : '', '', '']);

  sayfa.getRange(basSatir, 1, tablo.length, sutunSayisi).setValues(tablo);
  basliklariBicimle_(sayfa.getRange(basSatir, 1, 1, sutunSayisi));
  const veriAralik = sayfa.getRange(basSatir + 1, 1, tablo.length - 1, sutunSayisi);
  veriAralik.setVerticalAlignment('middle');
  sayfa.getRange(basSatir + 1, 2, tablo.length - 1, 1).setNumberFormat(CONFIG.BICIM.METIN);
  sayfa.getRange(basSatir + 1, 5, tablo.length - 1, 1).setNumberFormat('0%').setHorizontalAlignment('center');
  sayfa.getRange(basSatir + 1, 6, tablo.length - 1, 1).setNumberFormat('HH:mm');
  sayfa.getRange(basSatir + tablo.length - 1, 1, 1, sutunSayisi).setFontWeight('bold').setBackground('#F1F5F9');

  // Yüzde sütununa kırmızı → sarı → yeşil renk skalası
  if (liste.length) {
    const yuzdeAralik = sayfa.getRange(basSatir + 1, 5, liste.length, 1);
    sayfa.setConditionalFormatRules([
      SpreadsheetApp.newConditionalFormatRule()
        .setGradientMinpointWithValue('#FEE2E2', SpreadsheetApp.InterpolationType.NUMBER, '0')
        .setGradientMidpointWithValue('#FEF3C7', SpreadsheetApp.InterpolationType.NUMBER, '0.5')
        .setGradientMaxpointWithValue('#D1FAE5', SpreadsheetApp.InterpolationType.NUMBER, '1')
        .setRanges([yuzdeAralik])
        .build()
    ]);
  }

  sayfa.getRange('A3').setValue('Son hesaplama: ' + Utilities.formatDate(new Date(), CONFIG.SAAT_DILIMI, 'dd.MM.yyyy HH:mm'))
    .setFontColor('#64748B').setFontSize(9);
}

/** Kayitlar sayfasının tüm veri satırlarını (başlık hariç) okur. */
function kayitlariOku_() {
  const sayfa = sayfaGetir_(CONFIG.SAYFA.KAYITLAR, CONFIG.BASLIK.KAYITLAR);
  const son = sayfa.getLastRow();
  return son >= 2 ? sayfa.getRange(2, 1, son - 1, CONFIG.BASLIK.KAYITLAR.length).getValues() : [];
}

/**
 * Verilen gün için personel bazında özet üretir (Ozet sayfası ve yönetici
 * paneli ortak kullanır). Kayıt göndermemiş aktif personel kayitYok:true
 * olarak eklenir. Liste ada göre sıralıdır.
 * Her öğe: { kod, ad, tamam, toplam, son(Date|null), not, gorevler:[{ad,kategori,tamam}], kayitYok }
 */
function gunOzeti_(gun, veriler) {
  const S = CONFIG.SUTUN.KAYITLAR;
  const tamamN = metinNormalize_(CONFIG.DURUM.TAMAM);
  const ozet = {};
  veriler.forEach(function (r) {
    if (hucreGunAnahtari_(r[S.TARIH]) !== gun) return;
    const kod = kodNormalize_(r[S.KOD]);
    if (!ozet[kod]) ozet[kod] = { ad: String(r[S.AD] || ''), kod: kod, tamam: 0, toplam: 0, son: null, not: '', gorevler: [] };
    const o = ozet[kod];
    const tamam = metinNormalize_(r[S.DURUM]) === tamamN;
    o.toplam++;
    if (tamam) o.tamam++;
    o.gorevler.push({ ad: String(r[S.GOREV] || ''), kategori: String(r[S.KATEGORI] || ''), tamam: tamam });
    if (r[S.ZAMAN] instanceof Date && (!o.son || r[S.ZAMAN] > o.son)) o.son = r[S.ZAMAN];
    const not = String(r[S.NOT] || '').trim();
    if (not && !o.not) o.not = not;
  });

  const harita = personelHaritasi_();
  Object.keys(harita).forEach(function (kod) {
    if (harita[kod].aktif && !ozet[kod]) {
      ozet[kod] = { ad: harita[kod].ad, kod: kod, tamam: 0, toplam: 0, son: null, not: '', gorevler: [], kayitYok: true };
    }
  });

  return Object.keys(ozet).map(function (k) { return ozet[k]; })
    .sort(function (a, b) { return a.ad.localeCompare(b.ad, 'tr'); });
}

/** Özet tablosunun başlık satırını biçimlendirir. */
function basliklariBicimle_(aralik) {
  aralik.setFontWeight('bold').setBackground(CONFIG.RENK.BASLIK_ARKA).setFontColor(CONFIG.RENK.BASLIK_YAZI);
}


/* ────────────────────────────────────────────────────────────────────
 *  Yönetici yardımcıları
 * ──────────────────────────────────────────────────────────────────── */

/**
 * Yönetici işlemlerinin web uygulaması üzerinden (anonim ziyaretçi
 * tarafından google.script.run ile) tetiklenmesini engeller.
 * E-tablo menüsünden veya düzenleyiciden çalıştırıldığında etkin kullanıcı
 * ile aktif kullanıcı aynı kişidir; web uygulamasında ise ziyaretçi ya
 * anonimdir (boş) ya da betik sahibinden farklıdır.
 */
function yoneticiKontrol_() {
  let aktif = '', etkin = '';
  try {
    aktif = Session.getActiveUser().getEmail();
    etkin = Session.getEffectiveUser().getEmail();
  } catch (e) { /* yetkisiz bağlam */ }
  if (!aktif || aktif !== etkin) {
    throw new Error('Bu işlem yalnızca e-tablo üzerinden (Görev Takip menüsü) veya Apps Script düzenleyicisinden çalıştırılabilir.');
  }
}

/** E-tablo arayüzü varsa uyarı kutusu gösterir, yoksa günlüğe yazar. */
function bildir_(baslik, mesaj) {
  try {
    const ui = SpreadsheetApp.getUi();
    ui.alert(baslik, mesaj, ui.ButtonSet.OK);
  } catch (e) {
    console.log(baslik + ':\n' + mesaj);
  }
}


/* ════════════════════════════════════════════════════════════════════
 *  BÖLÜM 3 — YÖNETİCİ PANELİ (web: …/exec?y=YONETICI_KODU)
 *
 *  • Yönetici kodları tabloda DEĞİL, Script Properties'te saklanır;
 *    e-tabloyu görebilen personel bile yönetici kodunu göremez.
 *  • Kodlar 24 karakterdir (tahmin edilemez) ve menüden üretilir/iptal edilir.
 *  • İstemciye açık her yönetici fonksiyonu kodu baştan doğrular.
 * ════════════════════════════════════════════════════════════════════ */

/** Yönetici paneli sayfasını üretir; kod geçersizse uyarı ekranı gösterilir. */
function yoneticiSayfasi_(hamKod) {
  const yonetici = yoneticiDogrula_(hamKod);
  const simdi = new Date();
  const baslangic = yonetici
    ? { gecerli: true, yKod: yonetici.kod, ad: yonetici.ad, gun: gunAnahtari_(simdi), tarihMetni: tarihMetni_(simdi) }
    : { gecerli: false };

  const sablon = HtmlService.createTemplateFromFile('yonetici');
  sablon.baslangicJson = guvenliJson_(baslangic);
  return sablon.evaluate()
    .setTitle('Yönetici Paneli')
    .addMetaTag('viewport', 'width=device-width, initial-scale=1, viewport-fit=cover')
    .addMetaTag('mobile-web-app-capable', 'yes')
    .addMetaTag('apple-mobile-web-app-capable', 'yes')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.DEFAULT);
}

/** Kayıtlı yönetici kodlarını { kod: { ad, olusturma } } olarak döndürür. */
function yoneticiKodlari_() {
  try {
    return JSON.parse(PropertiesService.getScriptProperties().getProperty(CONFIG.YONETICI_OZELLIK) || '{}');
  } catch (e) {
    return {};
  }
}

/** Yönetici kodunu doğrular; geçerliyse { kod, ad } döner. */
function yoneticiDogrula_(hamKod) {
  const kod = kodNormalize_(hamKod);
  if (!/^[a-z0-9]{16,64}$/.test(kod)) return null;
  const y = yoneticiKodlari_()[kod];
  return y ? { kod: kod, ad: y.ad || 'Yönetici' } : null;
}

/** Yönetici fonksiyonlarının ortak girişi: kod geçersizse kullanıcı hatası fırlatır. */
function yoneticiGerekli_(yKod) {
  const y = yoneticiDogrula_(yKod);
  if (!y) throw kullaniciHatasi_('Yönetici bağlantınız geçersiz veya iptal edilmiş.');
  return y;
}

/**
 * [İSTEMCİYE AÇIK — YÖNETİCİ] Seçilen günün personel bazlı özeti ve
 * o güne kadarki son 7 günün genel tamamlanma eğilimi.
 */
function yoneticiOzetGetir(yKod, gun) {
  try {
    yoneticiGerekli_(yKod);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(String(gun || ''))) gun = gunAnahtari_();
    const secilen = Utilities.parseDate(gun, CONFIG.SAAT_DILIMI, 'yyyy-MM-dd');
    const veriler = kayitlariOku_();

    const bugunMu = gun === gunAnahtari_();
    const liste = gunOzeti_(gun, veriler).map(function (o) {
      // Bugün henüz kayıt göndermeyenlerin atanmış görevleri "bekliyor" olarak gösterilir.
      // Geçmiş günlerde atamalar değişmiş olabileceği için tahmin yapılmaz.
      const bekleyen = (o.kayitYok && bugunMu)
        ? atanmisGorevler_({ kod: o.kod, ad: o.ad }, false).map(function (g) { return { ad: g.ad, kategori: g.kategori }; })
        : [];
      return {
        kod: o.kod, ad: o.ad, tamam: o.tamam, toplam: o.toplam, not: o.not,
        son: o.son ? Utilities.formatDate(o.son, CONFIG.SAAT_DILIMI, 'HH:mm') : null,
        kayitYok: !!o.kayitYok, gorevler: o.gorevler, bekleyen: bekleyen
      };
    });

    // Son N günün genel oranı (tek geçişte)
    const gunler = [];
    const sayac = {};
    for (let i = CONFIG.TREND_GUN - 1; i >= 0; i--) {
      // Öğle saatine göre kaydır: yaz saati geçişlerinde gün atlamasın
      const g = gunAnahtari_(new Date(secilen.getTime() + 12 * 3600000 - i * 86400000));
      gunler.push(g);
      sayac[g] = { tamam: 0, toplam: 0 };
    }
    const S = CONFIG.SUTUN.KAYITLAR;
    const tamamN = metinNormalize_(CONFIG.DURUM.TAMAM);
    veriler.forEach(function (r) {
      const g = hucreGunAnahtari_(r[S.TARIH]);
      if (!sayac[g]) return;
      sayac[g].toplam++;
      if (metinNormalize_(r[S.DURUM]) === tamamN) sayac[g].tamam++;
    });
    const kisaGun = ['Pzt', 'Sal', 'Çar', 'Per', 'Cum', 'Cmt', 'Paz'];
    const trend = gunler.map(function (g) {
      const d = Utilities.parseDate(g, CONFIG.SAAT_DILIMI, 'yyyy-MM-dd');
      const hg = Number(Utilities.formatDate(d, CONFIG.SAAT_DILIMI, 'u'));
      return {
        gun: g,
        etiket: kisaGun[hg - 1],
        gunNo: Number(Utilities.formatDate(d, CONFIG.SAAT_DILIMI, 'd')),
        tamam: sayac[g].tamam,
        toplam: sayac[g].toplam
      };
    });

    return { gun: gun, bugun: gunAnahtari_(), tarihMetni: tarihMetni_(secilen), personeller: liste, trend: trend };
  } catch (hata) {
    throw istemciHatasi_(hata, 'Özet yüklenirken bir sorun oluştu.');
  }
}

/**
 * [İSTEMCİYE AÇIK — YÖNETİCİ] Tüm görevleri (pasifler dahil), personel
 * listesini ve kategorileri döndürür. Önbellek kullanılmaz.
 */
function yoneticiGorevleriGetir(yKod) {
  try {
    yoneticiGerekli_(yKod);
    return yoneticiGorevVerisi_();
  } catch (hata) {
    throw istemciHatasi_(hata, 'Görev listesi yüklenirken bir sorun oluştu.');
  }
}

/**
 * [İSTEMCİYE AÇIK — YÖNETİCİ] Görev ekler (satir boşsa) veya günceller.
 * @param {Object} g { satir, eskiAd, ad, kategori, sira, aktif, herkes, atananKodlar[] }
 *   satir + eskiAd birlikte gönderilir: biri tabloyu elle değiştirdiyse
 *   yanlış satırın üzerine yazılmasını önler.
 */
function yoneticiGorevKaydet(yKod, g) {
  try {
    yoneticiGerekli_(yKod);
    if (!g || typeof g !== 'object') throw kullaniciHatasi_('Geçersiz istek.');

    const ad = hucreMetni_(g.ad, 200);
    if (!ad) throw kullaniciHatasi_('Görev adı boş olamaz.');
    const kategori = hucreMetni_(g.kategori, 50) || CONFIG.VARSAYILAN_KATEGORI;
    const siraSayi = Number(g.sira);
    const sira = (g.sira === '' || g.sira == null || isNaN(siraSayi)) ? '' : Math.round(siraSayi);

    // Atananlar: yalnızca var olan personel kabul edilir; tabloya okunur olsun diye AD yazılır
    const personel = personelListesi_();
    let atanan = '';
    if (!g.herkes) {
      const istenen = new Set((Array.isArray(g.atananKodlar) ? g.atananKodlar : []).map(kodNormalize_));
      const adlar = personel.filter(function (p) { return istenen.has(p.kod); }).map(function (p) { return p.ad; });
      if (!adlar.length) throw kullaniciHatasi_('En az bir kişi seçin ya da "Herkes" seçeneğini işaretleyin.');
      atanan = adlar.join(', ');
    }
    const satirDegeri = [ad, kategori, atanan, g.aktif === false ? 'Hayır' : 'Evet', sira];

    const kilit = LockService.getScriptLock();
    if (!kilit.tryLock(CONFIG.KILIT_BEKLEME_MS)) throw kullaniciHatasi_('Sistem şu anda yoğun. Lütfen tekrar deneyin.');
    try {
      const sayfa = sayfaGetir_(CONFIG.SAYFA.GOREVLER, CONFIG.BASLIK.GOREVLER);
      const son = sayfa.getLastRow();
      const mevcut = son >= 2 ? sayfa.getRange(2, 1, son - 1, 2).getValues() : [];
      const satir = g.satir ? Number(g.satir) : null;

      if (satir) satirDogrula_(mevcut, satir, g.eskiAd);

      // Aynı kategoride aynı adlı başka görev olmasın (kayıt kimliği "Kategori::Görev")
      const yeniId = gorevId_(metinNormalize_(kategori), metinNormalize_(ad));
      mevcut.forEach(function (r, i) {
        if (i + 2 === satir) return;
        const id = gorevId_(metinNormalize_(String(r[1] || '').trim() || CONFIG.VARSAYILAN_KATEGORI), metinNormalize_(r[0]));
        if (id === yeniId) throw kullaniciHatasi_('"' + kategori + '" kategorisinde bu adla bir görev zaten var.');
      });

      let hedef = satir;
      if (!hedef) {
        hedef = son + 1;
        if (sayfa.getMaxRows() < hedef) sayfa.insertRowsAfter(sayfa.getMaxRows(), 1);
      }
      sayfa.getRange(hedef, 1, 1, satirDegeri.length).setValues([satirDegeri]);
      SpreadsheetApp.flush();
    } finally {
      kilit.releaseLock();
    }

    onbellekTemizle_();
    return yoneticiGorevVerisi_();
  } catch (hata) {
    throw istemciHatasi_(hata, 'Görev kaydedilirken bir sorun oluştu.');
  }
}

/** [İSTEMCİYE AÇIK — YÖNETİCİ] Görevi Gorevler sayfasından siler. */
function yoneticiGorevSil(yKod, satir, eskiAd) {
  try {
    yoneticiGerekli_(yKod);
    const kilit = LockService.getScriptLock();
    if (!kilit.tryLock(CONFIG.KILIT_BEKLEME_MS)) throw kullaniciHatasi_('Sistem şu anda yoğun. Lütfen tekrar deneyin.');
    try {
      const sayfa = sayfaGetir_(CONFIG.SAYFA.GOREVLER, CONFIG.BASLIK.GOREVLER);
      const son = sayfa.getLastRow();
      const mevcut = son >= 2 ? sayfa.getRange(2, 1, son - 1, 1).getValues() : [];
      satirDogrula_(mevcut, Number(satir), eskiAd);
      satirlariSil_(sayfa, [Number(satir)]);
      SpreadsheetApp.flush();
    } finally {
      kilit.releaseLock();
    }
    onbellekTemizle_();
    return yoneticiGorevVerisi_();
  } catch (hata) {
    throw istemciHatasi_(hata, 'Görev silinirken bir sorun oluştu.');
  }
}

/** Panelin görev ekranı için tablodan taze veri hazırlar. */
function yoneticiGorevVerisi_() {
  const personel = personelListesi_();
  const adIleKod = {};
  personel.forEach(function (p) { adIleKod[metinNormalize_(p.ad)] = p.kod; adIleKod[p.kod] = p.kod; });

  const sayfa = sayfaGetir_(CONFIG.SAYFA.GOREVLER, CONFIG.BASLIK.GOREVLER);
  const S = CONFIG.SUTUN.GOREVLER;
  const son = sayfa.getLastRow();
  const gorevler = [];
  if (son >= 2) {
    sayfa.getRange(2, 1, son - 1, CONFIG.BASLIK.GOREVLER.length).getValues().forEach(function (r, i) {
      const ad = String(r[S.AD] || '').trim();
      if (!ad) return;
      const ham = String(r[S.ATANAN] || '').split(/[,;\n]/).map(function (x) { return x.trim(); }).filter(String);
      const kodlar = [], bilinmeyen = [];
      ham.forEach(function (x) {
        const k = adIleKod[metinNormalize_(x)] || adIleKod[kodNormalize_(x)];
        if (k) { if (kodlar.indexOf(k) === -1) kodlar.push(k); } else bilinmeyen.push(x);
      });
      const siraSayi = Number(r[S.SIRA]);
      gorevler.push({
        satir: i + 2,
        ad: ad,
        kategori: String(r[S.KATEGORI] || '').trim() || CONFIG.VARSAYILAN_KATEGORI,
        herkes: ham.length === 0,
        atananKodlar: kodlar,
        bilinmeyen: bilinmeyen,
        aktif: evetMi_(r[S.AKTIF]),
        sira: (r[S.SIRA] === '' || isNaN(siraSayi)) ? '' : siraSayi
      });
    });
  }

  // Kategori sırası: önce CONFIG.KATEGORI_SIRASI, sonra tablodaki ilk görünüş sırası
  const kategoriler = CONFIG.KATEGORI_SIRASI.slice();
  gorevler.forEach(function (g) { if (kategoriler.indexOf(g.kategori) === -1) kategoriler.push(g.kategori); });

  return { gorevler: gorevler, personeller: personel, kategoriler: kategoriler };
}

/** Personel sayfasını önbelleksiz okur: [{ kod, ad, aktif }] (ada göre sıralı). */
function personelListesi_() {
  const sayfa = sayfaGetir_(CONFIG.SAYFA.PERSONEL, CONFIG.BASLIK.PERSONEL);
  const S = CONFIG.SUTUN.PERSONEL;
  const son = sayfa.getLastRow();
  const liste = [];
  const gorulen = {};
  if (son >= 2) {
    sayfa.getRange(2, 1, son - 1, 3).getValues().forEach(function (r) {
      const kod = kodNormalize_(r[S.KOD]);
      const ad = String(r[S.AD] || '').trim();
      if (kod && ad && !gorulen[kod]) {
        gorulen[kod] = true;
        liste.push({ kod: kod, ad: ad, aktif: evetMi_(r[S.AKTIF]) });
      }
    });
  }
  return liste.sort(function (a, b) { return a.ad.localeCompare(b.ad, 'tr'); });
}

/** Düzenlenecek satırın hâlâ beklenen görevi içerdiğini doğrular. */
function satirDogrula_(mevcut, satir, eskiAd) {
  const i = satir - 2;
  if (!(i >= 0 && i < mevcut.length) || String(mevcut[i][0] || '').trim() !== String(eskiAd || '').trim()) {
    throw kullaniciHatasi_('Görev listesi siz düzenlerken değişmiş. Liste yenilendi; lütfen tekrar deneyin.');
  }
}

/** Hücreye yazılacak metni temizler: uzunluk sınırı + formül enjeksiyonu koruması. */
function hucreMetni_(deger, maks) {
  let s = (typeof deger === 'string' ? deger : '').replace(/[\u0000-\u001F\u007F]/g, ' ').replace(/\s+/g, ' ').trim();
  if (s.length > maks) s = s.slice(0, maks);
  if (/^[=+\-@]/.test(s)) s = "'" + s;
  return s;
}

/** [MENÜ] Yeni bir yönetici linki üretir ve kopyalanabilir pencerede gösterir. */
function menuYoneticiLinkiOlustur() {
  yoneticiKontrol_();
  const ui = SpreadsheetApp.getUi();
  const url = webAppAdresi_(ui, false);
  if (!url) return;

  const yanit = ui.prompt('Yönetici Linki Oluştur', 'Bu link kimin için? (ör. Okan Bey)', ui.ButtonSet.OK_CANCEL);
  if (yanit.getSelectedButton() !== ui.Button.OK) return;
  const ad = hucreMetni_(yanit.getResponseText(), 60).replace(/^'/, '') || 'Yönetici';

  const kodlar = yoneticiKodlari_();
  const kod = kodUret_(new Set(Object.keys(kodlar)), CONFIG.YONETICI_KOD_UZUNLUGU);
  kodlar[kod] = { ad: ad, olusturma: Utilities.formatDate(new Date(), CONFIG.SAAT_DILIMI, 'dd.MM.yyyy HH:mm') };
  PropertiesService.getScriptProperties().setProperty(CONFIG.YONETICI_OZELLIK, JSON.stringify(kodlar));

  const link = url + '?y=' + kod;
  const html = HtmlService.createHtmlOutput(
    '<div style="font-family:Arial,sans-serif;font-size:14px;line-height:1.5">' +
    '<p><b>' + htmlKacis_(ad) + '</b> için yönetici linki:</p>' +
    '<input id="l" readonly value="' + htmlKacis_(link) + '" style="width:100%;padding:8px;font-size:13px" onclick="this.select()">' +
    '<p style="margin-top:12px"><button onclick="var l=document.getElementById(\'l\');l.select();document.execCommand(\'copy\');this.textContent=\'Kopyalandı ✓\'" ' +
    'style="padding:8px 16px;background:#4F46E5;color:#fff;border:0;border-radius:6px;cursor:pointer">Linki Kopyala</button></p>' +
    '<p style="color:#b91c1c">Bu link personel ve görev verilerini yönetmeye izin verir. Kimseyle paylaşmayın. ' +
    'Sızarsa menüden <i>Tüm Yönetici Linklerini İptal Et</i> deyip yenisini oluşturun.</p></div>'
  ).setWidth(520).setHeight(260);
  ui.showModalDialog(html, 'Yönetici Linki');
}

/** [MENÜ] Bütün yönetici linklerini geçersiz kılar. */
function menuYoneticiLinkleriniIptalEt() {
  yoneticiKontrol_();
  const ui = SpreadsheetApp.getUi();
  const kodlar = yoneticiKodlari_();
  const adet = Object.keys(kodlar).length;
  if (!adet) { ui.alert('Kayıtlı yönetici linki yok.'); return; }
  const adlar = Object.keys(kodlar).map(function (k) { return '• ' + kodlar[k].ad + ' (' + kodlar[k].olusturma + ')'; }).join('\n');
  const onay = ui.alert('Yönetici Linklerini İptal Et',
    adet + ' yönetici linki iptal edilecek:\n' + adlar + '\n\nDevam edilsin mi?', ui.ButtonSet.YES_NO);
  if (onay !== ui.Button.YES) return;
  PropertiesService.getScriptProperties().deleteProperty(CONFIG.YONETICI_OZELLIK);
  ui.alert('Tüm yönetici linkleri iptal edildi. Gerekirse yeni link oluşturun.');
}

/** Metni HTML içine güvenle yazmak için kaçış uygular. */
function htmlKacis_(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
