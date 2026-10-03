# AstroWall 🌍🌙

Gerçek NASA görüntüleriyle her saat kendini yenileyen Dünya / Ay duvar kağıdı.

## Derleme
GitHub'a yükle → **Actions** sekmesi derler → **Releases**'ten `app-debug.apk` indir.

## Özellikler
- **Dünya**: NASA EPIC (DSCOVR) uydusundan canlı tam disk fotoğrafı
- **Ay**: NASA SVS saatlik Ay karesi (LRO verisi) + Türkçe evre bilgisi
- Saf siyah zemin, yıldız veya süs yok: yalnızca gezegen
- Açılışta zaman atlamalı dönen küre; gece tarafında şehir ışıkları
- Ana ekran / kilit ekranı için ayrı seçim (Dünya + Ay birlikte de olur)

## Dokular (`app/src/main/assets/`)
`earth.jpg`, `moon.jpg` ve `earth_lights.jpg` internet yokken / açılış animasyonunda kullanılır.
Bunlar uygulama içinde üretilmiş yer tutucu dokulardır. Daha gerçekçi görünüm için kendi
2:1 (equirectangular) NASA dokularınla aynı adlarla değiştirebilirsin
(ör. Blue Marble → `earth.jpg`, LRO Ay haritası → `moon.jpg`, Black Marble → `earth_lights.jpg`).
