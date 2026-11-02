# SmartTube и сетевой обход

## Что стоит на ТВ

- `com.teamsmart.videomanager.tv` — SmartTube stable 22.38;
- `com.liskovsoft.smarttubetv.beta` — SmartTube beta 19.52;
- `io.github.romanvht.byedpi` — ByeByeDPI;
- SmartTube не грузит видео без обхода, потому что провайдер режет запросы.

## Что уже проверено

### VPN-режим — не работает на этой прошивке

При включении VPN ByeByeDPI падает:

```text
ActivityNotFoundException:
Unable to find explicit activity class
{com.android.vpndialogs/com.android.vpndialogs.ConfirmDialog}
```

Пакета `com.android.vpndialogs` нет в системе. Без него любое VPN-приложение
не может показать системный диалог согласия. Root на ТВ нет, восстановить
системный компонент нельзя.

### Proxy-режим ByeByeDPI — работает

В настройках ByeByeDPI:

- Режим → **Proxy** (не VPN);
- автозапуск и автоподключение включены.

После перезагрузки ТВ поднимается локальный SOCKS5:

```text
127.0.0.1:1080 LISTEN
```

Проверка с ПК через `adb forward`:

```text
curl --socks5-hostname 127.0.0.1:10800 https://www.youtube.com
HTTP/1.1 200 OK
```

### SmartTube умеет SOCKS

В исходниках SmartTube есть `ProxyManager` и диалог:

`Настройки → Общие → Интернет-цензура → Использовать веб-прокси`

Можно указать SOCKS `127.0.0.1:1080`. Встроенный тест Google проходит
(`HTTP/1.1 200 OK`), но основной интерфейс SmartTube всё равно иногда висит на
TLS-хендшейке YouTube. Это зависит от версии SmartTube и стратегии обхода.

## План v2: свой SOCKS5 с TLS-фрагментацией

Вместо VPN и внешнего ByeByeDPI Aurora TV будет запускать собственный
локальный прокси:

```text
SmartTube → SOCKS5 127.0.0.1:1080 → Aurora Proxy (TLS split) → YouTube
```

Что делает прокси:

1. Принимает SOCKS5 CONNECT.
2. Устанавливает TCP-соединение с нужным хостом.
3. Читает TLS ClientHello.
4. Отправляет его на сервер несколькими маленькими TCP-сегментами
   (split/disorder) с `TCP_NODELAY`.
5. Дальше прозрачно проксирует трафик.

Почему так:

- не нужен VPN и `VpnDialogs`;
- не нужен root;
- SmartTube уже поддерживает SOCKS;
- стратегию можно менять и тестировать прямо в Aurora TV.

Порт: `1080` для совместимости с текущей настройкой SmartTube. Если
ByeByeDPI уже слушает `1080`, v2 предложит остановить его или использовать
резервный `1081` и обновить настройку SmartTube.

## Что не обещаем

- обход DRM и платного контента;
- «работает у любого провайдера»;
- молчаливую установку APK (Android требует подтверждение).
