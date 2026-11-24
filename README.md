# Tv Ch

گیرنده ترکیبی تلویزیون اینترنتی برای Android.

## امکانات این نسخه
- پخش مستقیم با Media3/ExoPlayer: HLS (M3U8)، MPEG-DASH (MPD)، RTSP، SmoothStreaming و فایل‌های مستقیم HTTP/HTTPS سازگار با رمزگشاهای دستگاه.
- حالت Web TV با WebView، JavaScript، DOM Storage و پخش رسانه بدون نیاز اجباری به لمس اولیه.
- ورود فایل محلی M3U/M3U8 و نمایش کانال‌ها به‌صورت دکمه؛ انتخاب هر کانال آن را در پخش‌کننده مستقیم باز می‌کند.
- حفظ سرویس ضبط HLS نسخه پایه؛ ضبط فقط برای جریان‌های روشن و سازگار و بدون دورزدن DRM.
- GitHub Actions برای push روی همه شاخه‌ها و اجرای دستی.

## مواردی که هنوز موتور اختصاصی می‌خواهند
این نسخه ادعای پشتیبانی کامل از همه پروتکل‌ها ندارد. RTMP، SRT، WebRTC، RTP/UDP multicast، Stalker/MAG و APIهای اختصاصی IPTV هنوز به آداپتورهای جداگانه و آزمون با سرویس واقعی نیاز دارند. Xtream Codes نیز در این نسخه رابط ورود اختصاصی ندارد؛ فایل M3U خروجی سرویس را می‌توان پس از دریافت به‌صورت فایل وارد کرد.
صفحات وب نیز فقط در صورت اجازه سایت، پشتیبانی WebView و سازگاری پخش‌کننده قابل پخش‌اند؛ استخراج یا دورزدن DRM/محدودیت دسترسی انجام نمی‌شود.

## ساخت
- JDK 17
- Android SDK 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

خروجی GitHub Actions: `Tv Ch-debug`.


## Recent interface additions

- English controls: LIVE, WEB TV, CHANNELS, PLAY, OPEN M3U, RECORD, BOOKMARKS, ADD BOOKMARK, and NTV LIVE.
- Bookmarks are stored locally on the device and can be opened again or cleared.
- RECORD currently supports clear HLS `.m3u8` streams only. DRM/encrypted streams and web-page player capture are not supported. Recordings are stored in the app's external files `Recordings` folder.
- NTV LIVE opens `https://news.ntv.co.jp/live` inside the app and attempts to start HTML5/common web players. Some embedded or cross-origin players may still require a tap on their own Play control.


## ساختار پوشه‌ها
- فایل‌های Kotlin: `app/src/main/`
- منابع Android: `app/res/values/`
- گردش‌کار GitHub Actions: `.github/workflows/android.yml`
- عمق مسیرهای اصلی پروژه حداکثر سه پوشه نگه داشته شده است.

## راهنمای MGit و GitHub Actions
- گردش‌کار با Push به همه شاخه‌ها اجرا می‌شود و از بخش Actions نیز قابل اجرای دستی است.
- اگر Commit در مخزن دیده می‌شود اما همگام‌سازی محلی/راه‌دور درست نیست، در MGit از منوی Pull گزینه **Force Pull** را اجرا کنید. توجه کنید Force Pull می‌تواند تغییرات محلیِ همگام‌نشده را جایگزین کند؛ پیش از آن تغییرات محلی مهم را ذخیره یا پشتیبان‌گیری کنید.


Repository name: `Tv Ch`
