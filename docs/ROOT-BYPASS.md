# Root + always-on VPN: эксперимент

> [!WARNING]
> **Не включайте always-on VPN без физического доступа к телевизору.**
> На Telefunken TF-LED40S19T2S эта настройка технически подняла `tun0`, но
> после перезагрузки ТВ потерял сеть и ADB. Снять настройку удалённо уже
> нельзя. 02.11.2026 always-on VPN был отключён, ByeByeDPI удалён, ТВ снова
> работает по Wi-Fi. Раздел оставлен как документация эксперимента, а не
> как рекомендация.

Эксперимент проводился 02.11.2026 на **Telefunken TF-LED40S19T2S, Android 7.0
(API 24), MT5863, userdebug**.

## Что выяснилось

- `ro.build.type=userdebug`, `ro.debuggable=1`;
- `adb root` даёт **root-shell** без разблокировки загрузчика;
- `ro.boot.flash.locked=1`, постоянного Magisk/root для приложений нет;
- в прошивке отсутствует `com.android.vpndialogs`, поэтому обычный VPN-диалог
  не работает;
- root-shell позволяет выдать пакету ByeByeDPI разрешение VPN и включить
  **always-on VPN**;
- настройка сохраняется после перезагрузки, система сама поднимает `tun0`;
- `/system` не изменяется — только Secure Settings;
- **но после перезагрузки сеть может не восстановиться, и ADB тоже пропадёт.**

## Что сделать (если очень нужно и ТВ под рукой)

1. Установить ByeByeDPI и настроить:
   - Режим → **VPN**;
   - стратегия (на этом ТВ работает стандартная OOB/desync);
   - автозапуск и автоподключение — включены.
2. Выполнить с ПК:

```powershell
.\scripts\enable-bypass.ps1 -TvIp 192.168.0.3
```

Скрипт делает:

```text
adb root
setVpnPackageAuthorization(io.github.romanvht.byedpi, user 0, true)
setAlwaysOnVpnPackage(user 0, io.github.romanvht.byedpi, lockdown=false)
```

3. Перезагрузить ТВ и проверить:

```text
settings get secure always_on_vpn_app
# io.github.romanvht.byedpi

dumpsys connectivity | grep tun0
```

4. Открыть SmartTube stable 22.38 и войти в аккаунт:
   QR-код / код на экране → Chrome/Firefox → вход.

В эксперименте SmartTube stable доходил до экрана входа, то есть сеть и TLS
работали. Дальше нужен Google-аккаунт: без него домашняя лента может быть
пустой.

**Но это нестабильная схема.** Если после перезагрузки VPN не поднимется,
телевизор останется без сети. Поэтому сейчас always-on VPN не используется.

## Откат

```powershell
.\scripts\disable-bypass.ps1 -TvIp 192.168.0.3
```

Или вручную:

```text
settings delete secure always_on_vpn_app
settings put secure always_on_vpn_lockdown 0
```

После перезагрузки VPN перестанет запускаться автоматически. Само приложение
ByeByeDPI и его настройки остаются.

## Ограничения

- Команды `service call connectivity 40/47` относятся к Android 7 (API 24).
  На других версиях номера транзакций другие — не применяйте вслепую.
- Это не постоянный root: `adb root` живёт до перезагрузки/переподключения.
  Для запуска VPN он нужен только один раз, дальше always-on работает сам.
- VPN пропускает весь трафик ТВ через ByeByeDPI. Если нужен только YouTube,
  лучше отдельный proxy-режим, но он менее автоматический.
- Разблокировка загрузчика и Magisk не требуются. Не пытайтесь ставить
  непроверенный boot.img: есть риск bootloop.
