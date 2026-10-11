# Görev Takip — Kurulum Rehberi ve Test Kontrol Listesi

Personelin günlük açılış / gün içi / kapanış rutinlerini telefondan işaretleyip Google E-Tablolar'a kaydettiği Google Apps Script web uygulaması.

| Dosya | İçerik |
|---|---|
| `Code.gs` | Sunucu kodu. **Bölüm 1:** web uygulaması (doGet, doğrulama, kayıt). **Bölüm 2:** yönetici özellikleri (menü, link üretici, Ozet sayfası) |
| `index.html` | Tek dosya arayüz (HTML + CSS + JS, framework yok) |
| `yonetici.html` | Yönetici paneli arayüzü (özet + görev yönetimi) |
| `appsscript.json` | Proje bildirimi: saat dilimi `Europe/Istanbul`, V8, web uygulaması ayarları |

---

## Tasarım kararları

**Not sütunu, yalnızca günün ilk satırına yazılır.** Not tek bir görevle değil, günün tamamıyla ilgilidir. Her satıra kopyalansaydı 8 görevlik bir kayıtta aynı cümle 8 kez görünür, Kayitlar sayfası kalabalıklaşırdı. Ayrıca "Not boş değil" filtresi 8 kopya döndürürdü. İlk satıra yazıldığında bu filtre, **kişi başına günde tam bir satır** döndürür; yani günlük notların temiz bir listesini verir. Uygulama notu geri yüklerken günün dolu not hücresini okur. Özet sayfası da notu aynı yerden alır.

**Ozet sayfası script ile hesaplanır, formülle değil.** Bunun üç nedeni var:
- Türkçe yerel ayarda formül ayırıcısı `;` olur.
- `QUERY` ile tarih karşılaştırmanın sözdizimi kırılgandır.
- Kayitlar'da satırlar silinip yeniden yazıldığı için formül aralıkları kayabilir.

Script her çalıştığında aynı saat dilimi ve aynı mantıkla hesaplar. `Ozet!B2` hücresindeki tarih değişince otomatik yeniden hesaplanır. Menüden "Özeti Güncelle" ile de çalıştırılabilir.

**Personel kodu 6 karakterdir.** Örnekteki `7f3a` gibi 4 karakterli bir kodun 65 bin olasılığı vardır ve deneme-yanılma ile bulunabilir. Üretilen kodlarda ise yaklaşık 530 milyon olasılık var. Karışan karakterler (`0/o`, `1/l/i`) kullanılmaz. `e` harfi de kullanılmaz, çünkü Sheets `12e4` gibi bir kodu sayıya çevirir. Elle 4 karakterli kod yazmak da çalışır (3–32 karakter, harf ve rakam kabul edilir).

**Güvenlik**
- Kod, `doGet(e)` içinde `e.parameter.k` ile sunucuda okunur. Geçersizse sayfaya yalnızca `{gecerli:false}` gider.
- Personel listesi istemciye hiç gönderilmez. Her `google.script.run` çağrısında kod yeniden doğrulanır.
- İstemciden gelen görev kimlikleri, Gorevler sayfasından taze okunan atanmış aktif görevlerle karşılaştırılır. Uydurma kimlikler yok sayılır.
- Not alanında `=`, `+`, `-` veya `@` ile başlayan metin formül olarak çalışmasın diye başına `'` eklenir.
- Yönetici fonksiyonları (`kurulum`, menüler) web sayfasından tetiklenemez (`yoneticiKontrol_`).
- Adı `_` ile biten fonksiyonlar Apps Script tarafından istemciye kapalıdır.

**Atanan Personel** sütununa ad soyad (büyük/küçük harf duyarsız) **veya** personel kodu yazılabilir. Birden fazla kişi virgülle ayrılır: `Ayşe Yılmaz, Mehmet Demir`. Boş bırakılırsa görev herkese atanır.

**Önbellek:** Personel ve görev listeleri 5 dakika önbelleklenir. Personel veya Gorevler sayfasında bir hücreyi düzenlediğinizde önbellek otomatik temizlenir, değişiklik hemen yansır. Kayıt sırasında görev listesi her zaman tablodan doğrudan okunur.

---

## Kurulum (adım adım)

### 1. E-tabloyu ve Apps Script projesini açın
1. [sheets.google.com](https://sheets.google.com) adresinde **Boş e-tablo** oluşturun ve bir ad verin (ör. *Görev Takip*).
2. Üst menüden **Uzantılar > Apps Script**'i seçin. Yeni sekmede e-tabloya bağlı proje açılır.
3. Sol üstteki *Adsız proje* yazısına tıklayıp projeye bir ad verin.

### 2. `appsscript.json` dosyasını görünür yapın
1. Sol kenar çubuğunda **⚙ Proje Ayarları**'na tıklayın.
2. **"appsscript.json" manifest dosyasını düzenleyicide göster** kutusunu işaretleyin.
3. Ayrıca **Saat dilimi** alanında *(GMT+03:00) İstanbul* göründüğünü kontrol edin. Manifest bunu zaten ayarlar.

### 3. Dosyaları oluşturun ve kodları yapıştırın
1. Sol kenar çubuğunda **‹ › Düzenleyici**'ye dönün.
2. **`Kod.gs`** (İngilizce arayüzde `Code.gs`) dosyasını açın, içindekileri silin ve `Code.gs` dosyasının **tamamını** yapıştırın. Dosya adının değişmesi gerekmez.
3. **Dosyalar** başlığının yanındaki **＋** > **HTML**'yi seçin, adı **`index`** yazın (`.html` uzantısını kendisi ekler). İçini silip `index.html` dosyasının tamamını yapıştırın.
   > Dosya adı tam olarak `index` olmalıdır. Kod `createTemplateFromFile('index')` ile bu adı arar.
4. **`appsscript.json`** dosyasını açın, içini silip `appsscript.json` dosyasının tamamını yapıştırın.
5. **💾 Projeyi kaydet** (Ctrl+S).

### 4. `kurulum()` fonksiyonunu çalıştırın ve izin verin
1. Düzenleyicinin üstündeki fonksiyon listesinden **`kurulum`**'u seçip **▶ Çalıştır**'a basın.
2. **Yetkilendirme gerekli** penceresinde **İzinleri incele**'ye tıklayın ve Google hesabınızı seçin.
3. **"Google bu uygulamayı doğrulamadı"** uyarısı çıkar. Bu, kendi yazdığınız betikler için **normaldir**:
   - Sol alttaki **Gelişmiş** bağlantısına tıklayın.
   - **"(Proje adı) projesine git (güvenli değil)"** bağlantısına tıklayın.
   - İzin listesini inceleyip **İzin ver**'e basın.
4. Yürütme günlüğünde "Kurulum tamamlandı" mesajını görürsünüz. E-tabloya dönün. **Personel**, **Gorevler**, **Kayitlar** ve **Ozet** sayfaları örnek veriyle oluşmuş olur.
5. E-tablo sekmesini **yenileyin** (F5). Üst menüde **Görev Takip** menüsü belirir.

> `@OnlyCurrentDoc` sayesinde betik yalnızca **bu e-tabloya** erişim ister, Drive'ınızdaki diğer dosyalara erişmez.

### 5. Web uygulaması olarak dağıtın
1. Apps Script düzenleyicisinde sağ üstteki **Dağıt > Yeni dağıtım**'ı seçin.
2. **Tür seçin** yanındaki ⚙ simgesinden **Web uygulaması**'nı seçin.
3. Ayarları şöyle yapın:
   - **Açıklama:** `v1` (isteğe bağlı)
   - **Uygulamayı şu kullanıcı olarak çalıştır:** **Ben** (e-posta adresiniz)
   - **Erişimi olanlar:** **Herkes**
4. **Dağıt**'a basın. İlk seferde yetkilendirme tekrar istenebilir; 4. adımdaki gibi onaylayın.
5. Gösterilen **Web uygulaması URL'sini** (`https://script.google.com/macros/s/…/exec`) kopyalayın.

> **Neden "Ben" ve "Herkes"?** Personelin Google hesabıyla giriş yapması gerekmez. Betik sizin yetkinizle e-tabloya yazar. E-tablonun kendisini personelle **paylaşmayın**; erişim yalnızca kişisel linkle olur.

### 6. Kod değişince YENİ SÜRÜM dağıtın
`/exec` linki **dağıtılmış sürümü** çalıştırır. Kodu kaydetmek tek başına personelin gördüğü uygulamayı **değiştirmez**. Her kod değişikliğinden sonra:

1. **Dağıt > Dağıtımları yönet**'i açın.
2. Etkin dağıtımı seçip **✏ Düzenle** (kalem) simgesine tıklayın.
3. **Sürüm** listesinden **Yeni sürüm**'ü seçin ve **Dağıt**'a basın.

Böylece URL **aynı kalır**, personel linkleri bozulmaz. "Yeni dağıtım" yaparsanız yeni bir URL oluşur ve linkleri yeniden üretmeniz gerekir.

| Link | Ne çalıştırır | Kimler açabilir |
|---|---|---|
| `…/exec` | Son **dağıtılmış sürüm** | Dağıtım ayarındaki herkes. **Personele bu verilir.** |
| `…/dev` | Kaydedilmiş **en son kod** (test) | Yalnızca projeyi düzenleme yetkisi olanlar |

Test etmek için **Dağıt > Dağıtımları test et** ekranındaki `/dev` URL'sinin sonuna `?k=KOD` ekleyerek açabilirsiniz.

### 7. Personel linklerini üretin ve paylaşın
1. **Personel** sayfasına çalışanları ekleyin: **B: Ad Soyad**, **C: Aktif** = `Evet`. **A: Kod** sütununu boş bırakabilirsiniz.
2. Üst menüden **Görev Takip > Personel Linklerini Oluştur**'u seçin.
3. İlk seferde web uygulaması adresi sorulur. 5. adımda kopyaladığınız **/exec** URL'sini yapıştırın. Adres saklanır; değiştirmek için **Görev Takip > Web Uygulaması Adresini Değiştir**'i kullanın.
4. Kodu boş olan herkese otomatik kod üretilir. **D: Kişisel Link** sütununa `…/exec?k=ab3cd4` biçiminde linkler yazılır.
5. Her linki **yalnızca ilgili kişiye**, birebir mesajla (WhatsApp, SMS vb.) gönderin.
   - Bir link sızarsa o kişinin **Kod** hücresini silin ve menüyü tekrar çalıştırın. Yeni kod ve link üretilir, eski link çalışmaz.
   - İşten ayrılan personel için **Aktif** = `Hayır` yapın. Link anında geçersiz olur.

**Görevleri düzenleme:** **Gorevler** sayfasında satır ekleyin veya silin. **Aktif** = `Hayır` ile görevi gizleyebilir, **Sıra** ile kategori içindeki sıralamayı belirleyebilirsiniz. Kategoriler *Açılış → Gün İçi → Kapanış* sırasıyla gösterilir. Bunların dışındaki kategoriler sona eklenir. Değişiklikler kod dağıtımı gerektirmez.

**Özet:** **Ozet** sayfasındaki **B2** hücresine bir tarih yazın. Personel bazında tamamlanan, toplam, yüzde, son kayıt saati ve not otomatik dolar. Kayıt göndermemiş aktif personel "Kayıt yok" olarak görünür.

### 8. Telefonda uygulama gibi kullanma ("Ana Ekrana Ekle")
- **Android (Chrome):** Linki açın, sağ üstteki **⋮** menüsünden **Ana ekrana ekle**'yi seçin ve **Ekle**'ye basın.
- **iPhone (Safari):** Linki **Safari**'de açın, alttaki **Paylaş** simgesine (□↑) dokunun, **Ana Ekrana Ekle**'yi seçin ve **Ekle**'ye basın.

Ana ekrandaki simge doğrudan kişisel linki açar. Personelin linki her gün yeniden araması gerekmez.

### 9. "Bu uygulama başka bir kullanıcı tarafından oluşturuldu" bandı
Sayfanın üstünde gri bir bant görünebilir: *"Bu uygulama, Google değil, başka bir kullanıcı tarafından oluşturuldu"*. Bu, Google'ın Apps Script web uygulamalarına eklediği **standart bir güvenlik uyarısıdır**. Kaldırılamaz ve bir hata değildir. Personele önceden söylemeniz yeterli. (Google Workspace hesaplarında, aynı alan içindeki kullanıcılara gösterilmeyebilir.)

### 10. Yönetici paneli (`…/exec?y=…`)
Telefondan açılan yönetici ekranında iki sekme vardır:

- **Özet**
  - Tarih seçilir (‹ › okları ya da tarihe dokunarak). Genel tamamlama oranı, kayıt gönderen ve "Kayıt yok" sayıları görünür.
  - **Son 7 günün** tamamlanma grafiği vardır; bir güne dokunarak o güne geçilir.
  - **Kişiye göre** görünümü: her kişinin kaç görevi eksik olduğu görünür. Karta dokununca o günün görev listesi (✓/✗) ve notu açılır. Kayıt göndermeyenler en üstte yer alır; bugün için yapmaları gereken görevler ⏳ ile listelenir.
  - **Göreve göre** görünümü: her görevin altında kim yaptı (✓), kim yapmadı (✗) ve kimden kayıt bekleniyor (⏳).
  - **Sadece yapılmayanlar** anahtarı açılınca yalnızca eksik kalan görevler ve kişiler gösterilir.
- **Görevler**
  - Tüm görevler kategori ve Sıra'ya göre listelenir. Kişiye göre filtrelenebilir; pasif görevler isteğe bağlı gösterilir.
  - **Yeni Görev** ile görev eklenir. Bir göreve dokunarak ad, kategori, sıra, aktiflik ve **kime atanacağı** ("Herkes" ya da seçili kişiler) değiştirilir veya görev silinir.
  - Değişiklik Gorevler sayfasına yazılır ve personel ekranına **hemen** yansır.

**Kurulum**
1. Apps Script'te **＋ > HTML** ile **`yonetici`** adlı bir dosya oluşturun ve `yonetici.html` içeriğini yapıştırın. `Code.gs`'i de güncel haliyle değiştirin, sonra kaydedin.
2. **Dağıt > Dağıtımları yönet > ✏ Düzenle > Sürüm: Yeni sürüm > Dağıt**. Personel linkleri değişmez.
3. E-tabloyu yenileyin (F5) ve **Görev Takip > Yönetici Linki Oluştur**'u seçin. Kimin için olduğunu yazın. Açılan penceredeki linki **Linki Kopyala** ile alın ve telefonunuzda açın; isterseniz ana ekrana ekleyin.

**Güvenlik:** Yönetici kodları 24 karakterdir ve e-tabloda değil, betiğin gizli ayarlarında (Script Properties) saklanır. E-tabloyu görebilen biri bile bu kodları göremez. Link sızarsa **Görev Takip > Tüm Yönetici Linklerini İptal Et** ile hepsini geçersiz kılıp yenisini oluşturun.

---

## Test Kontrol Listesi

Önce **Görev Takip > Personel Linklerini Oluştur** ile linkleri üretin. Testleri telefonda ve masaüstünde yapın.

### Kimlik doğrulama
- [ ] **Geçerli kod.** `…/exec?k=<geçerli kod>` açılınca:
  - Üstte "Hoş geldin, [Ad]" ve "8 Ekim 2026, Perşembe" biçiminde bugünün tarihi görünür.
  - Önce iskelet (yükleniyor) görünür, ardından görevler kategoriler altında listelenir.
- [ ] **Geçersiz kod.** `…/exec?k=yanlis1` açılınca:
  - "Geçersiz bağlantı. Lütfen yöneticinizden kişisel linkinizi isteyin." ekranı çıkar. Görev listesi ve isim seçme menüsü **yoktur**.
  - Tarayıcıda **Sayfa kaynağını görüntüle** ile bakıldığında hiçbir personel adı görünmez.
- [ ] **Kodsuz link.** `…/exec` (parametresiz) açılınca aynı uyarı ekranı çıkar.
- [ ] **Pasif personel.** Kişinin **Aktif** değeri `Hayır` yapılınca linki uyarı ekranına düşer. Önbellek düzenleme anında temizlendiği için hemen geçerli olur.
- [ ] **Büyük harf / boşluk.** Kod `?k=AB3CD4` gibi büyük harfle yazılsa da çalışır.

### Görev listesi
- [ ] Atanan Personel sütunu boş olan görevler herkeste görünür. İsim veya kod yazılı olanlar yalnızca o kişilerde görünür. Virgülle iki isim yazıldığında ikisinde de görünür.
- [ ] **Aktif** = `Hayır` görevler görünmez.
- [ ] Kategoriler Açılış → Gün İçi → Kapanış sırasıyla, görevler **Sıra** değerine göre listelenir.
- [ ] Hiç görevi olmayan kişide "Size atanmış aktif görev bulunmuyor." mesajı çıkar.
- [ ] Satırın herhangi bir yerine dokununca kutu işaretlenir: yeşil tik ve üstü çizili yazı görünür. "5 / 8 görev tamamlandı" sayacı ve çubuk anında güncellenir.

### Kayıt ve günlük güncelleme mantığı
- [ ] **İlk kayıt.** Birkaç görevi işaretleyin, not yazın ve **Rutinleri Kaydet**'e basın.
  - Buton devre dışı kalır ve dönen bir animasyon gösterir.
  - Sonra yeşil "Tebrikler, bugünkü görevleriniz kaydedildi!" bildirimi çıkar.
- [ ] **Kayitlar sayfasını kontrol edin:**
  - Kişiye atanmış **tüm** görevler yazılmıştır (Tamamlandı / Yapılmadı).
  - A sütunu `08.10.2026 14:32` ve B sütunu `08.10.2026` biçimindedir. Hücreye tıklayınca formül çubuğunda tarih değeri görünür, düz metin değildir.
  - Not yalnızca ilk satırdadır.
- [ ] **Aynı gün ikinci kayıt.**
  - Sayfayı yenileyin: daha önce işaretlenenler işaretli, not dolu gelir. "Bugün son kayıt: HH:mm" görünür.
  - Bir işareti değiştirip tekrar kaydedin. Kayitlar'daki satır sayısı **artmaz**, eski satırlar güncel durumla değişir.
- [ ] **Hiç işaret yokken kaydet.** Tüm işaretleri kaldırıp kaydedince önce onay penceresi çıkar. **Vazgeç** kaydetmez. **Yine de Kaydet** tümünü "Yapılmadı" olarak yazar.
- [ ] **Yeni gün.**
  - Ertesi gün (veya Kayitlar'da bugünün satırlarının B sütununu dünün tarihiyle değiştirerek) açın: liste boş başlar. Dünün kayıtları silinmez.
  - Sayfa gece 00:00'dan önce açılıp sonra kaydedilirse "Yeni bir gün başladı" bildirimi çıkar ve liste yenilenir. Dünün işaretleri bugüne yazılmaz.
- [ ] **Sahte görev denemesi (isteğe bağlı, teknik).** Tarayıcı konsolundan `google.script.run.gorevleriKaydet('<kod>', ['Açılış::Uydurma'], '', '<yyyy-MM-dd>')` çağrılınca uydurma görev Kayitlar'a yazılmaz.
- [ ] **Yönetici fonksiyonu denemesi (isteğe bağlı, teknik).** Web uygulamasının konsolundan `google.script.run.kurulum()` çağrısı hata verir, e-tabloda değişiklik olmaz.

### Eşzamanlılık
- [ ] **İki kişinin aynı anda kaydı.** İki farklı telefonda iki ayrı personel linkini açın ve **Kaydet**'e aynı anda basın.
  - İki kayıt da eksiksiz yazılır. Satırlar karışmaz veya üst üste yazılmaz.
  - Kişi başına satır sayısı, atanmış görev sayısına eşittir.
- [ ] **Aynı kişi, iki cihaz.** Aynı link iki cihazda açılıp sırayla kaydedilince son kaydedilen durum geçerli olur. Mükerrer satır oluşmaz.

### Bağlantı kesintisi
- [ ] **Kaydederken çevrimdışı.** Telefonu **Uçak modu**'na alıp **Kaydet**'e basın.
  - Kırmızı "İnternet bağlantısı kurulamadı…" kutusu ve **Tekrar Dene** butonu çıkar.
  - İşaretlemeler ve not **kaybolmaz**.
- [ ] **Tekrar deneme.** Bağlantıyı açıp **Tekrar Dene**'ye basın. Kayıt başarılı olur ve yeşil bildirim çıkar.
- [ ] **Yüklenirken kesinti.** Sayfa yüklenirken bağlantı koparsa "Görevler yüklenemedi" kutusu ve **Tekrar Dene** butonu çıkar. Butona basınca liste yüklenir.
- [ ] **Yavaş bağlantı.** Sunucu 45 sn içinde yanıt vermezse "Sunucu yanıt vermedi" uyarısı çıkar. Tekrar denemek güvenlidir, mükerrer kayıt oluşmaz.

### Yönetici paneli
- [ ] **Görev Takip > Yönetici Linki Oluştur** ile üretilen link açılınca "Yönetici Paneli" ekranı gelir. `?y=` değeri değiştirilince "Geçersiz yönetici bağlantısı" ekranı çıkar.
- [ ] Bir personelin `?k=` kodu, `?y=` olarak denenince panel **açılmaz**.
- [ ] Özet > **Göreve göre** görünümünde her görevin altında kimin yaptığı (✓), kimin yapmadığı (✗) ve bugün henüz kayıt göndermeyenler (⏳) listelenir. **Sadece yapılmayanlar** açılınca tamamen bitmiş görevler ve kişiler gizlenir.
- [ ] Bugün kayıt göndermeyen kişinin kartı açılınca o gün yapması gereken görevler ⏳ ile görünür.
- [ ] Özet sekmesinde tarih değiştirilince rakamlar ve grafik güncellenir. Kayıt göndermeyen kişi "Kayıt yok" olarak üstte görünür.
- [ ] Yeni görev sadece bir kişiye atanınca yalnızca o kişinin ekranında görünür, diğerinde görünmez. Gorevler sayfasının C sütununa kişinin adı yazılır.
- [ ] Aynı kategoride aynı adla ikinci görev eklenmek istenince uyarı çıkar.
- [ ] Görevi "Aktif" kapalı kaydedince personel ekranından kalkar. Sil'e basınca Gorevler sayfasından silinir, geçmiş Kayitlar silinmez.
- [ ] **Tüm Yönetici Linklerini İptal Et** sonrası eski yönetici linki çalışmaz.

### Yönetici (e-tablo)
- [ ] **Görev Takip > Personel Linklerini Oluştur.** Kodu boş olana kod üretilir ve D sütununa `/exec?k=…` linki yazılır.
- [ ] **Ozet!B2**'ye bugünün tarihini yazın. Tablo personel bazında Tamamlanan / Toplam / Yüzde ile dolar. Yüzde sütunu kırmızı → yeşil renklenir.
- [ ] Gorevler sayfasında bir görev ekleyince değişiklik, uygulama yenilendiğinde hemen görünür. Yeni sürüm dağıtmak gerekmez.
- [ ] `Code.gs` değiştirildikten sonra **Dağıtımları yönet > Düzenle > Yeni sürüm** yapılmadan `/exec` linkinin eski davranışı sürdürdüğü, yapıldıktan sonra güncellendiği doğrulanır.

---

## Sorun giderme

| Belirti | Çözüm |
|---|---|
| "Script function not found: doGet" | Kod kaydedilmemiş veya dağıtım eski. Kaydedip **Yeni sürüm** dağıtın. |
| "Exception: No HTML file named index" | HTML dosyasının adı tam olarak `index` olmalıdır. |
| Görev Takip menüsü görünmüyor | E-tablo sekmesini yenileyin. Yine yoksa `kurulum`'u düzenleyiciden bir kez çalıştırıp izin verin. |
| Tarih bir gün kaymış görünüyor | **Dosya > Ayarlar > Saat dilimi** değeri *(GMT+03:00) İstanbul* olmalıdır. `kurulum()` bunu otomatik ayarlar. |
| Personel eklendi ama linki "geçersiz" diyor | Aktif = `Evet` mi? Kod hücresinde boşluk veya yanlış karakter var mı? **Görev Takip > Önbelleği Temizle**'yi deneyin. |
| **"Google Drive: Dosya şu anda açılamıyor. Lütfen adresi kontrol edin"** | 1) Linki **gizli sekmede** (Chrome: ⋮ > Yeni gizli sekme) açın. Açılıyorsa sorun, tarayıcıda **birden fazla Google hesabıyla** oturum açık olmasıdır (bilinen bir Google hatası). Adreste `/u/1/` görünürse bu yüzdendir. Personel genellikle tek hesaplı olduğu için onlarda sorun olmaz. 2) **Dağıt > Dağıtımları yönet**'te **Erişimi olanlar: Herkes** olmalıdır. "Yalnızca ben" seçiliyse bu hata çıkar. 3) Link `…/exec?k=…` biçiminde olmalıdır. WhatsApp'ta link bölünmüşse veya sonu eksik kopyalanmışsa da bu hata çıkar. |
| Linkler `/dev` ile bitiyor ve personelde çalışmıyor | **Görev Takip > Web Uygulaması Adresini Değiştir** ile `/exec` adresini girip linkleri yeniden oluşturun. |
