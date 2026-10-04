# Katı Mod

Zip'i boş bir repo klasörüne aç (`.github` klasörü repo kökünde kalmalı), push et; Actions APK'yı üretir.
Telefonda uygulamayı aç, üç izin düğmesini sırayla etkinleştir, sonra uygulamalara günlük dakika ata.

- GuardService: süre dolunca uygulamayı kapatıp yemin ekranını açar
- OathActivity: cümle aynen yazılırsa 10 dk izin (Prefs.kt'de değişir)
- NotifBlocker: süresi dolan uygulamaların bildirimlerini siler
