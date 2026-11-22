#!/bin/sh
# voice-loop.sh — «Алиса» → пауза музыки → радуга → пли м → спиннер (слух)
# → взрыв → жёлтый (думает/говорит) → платформа обратно → музыка продолжается.

unset LD_LIBRARY_PATH LD_PRELOAD
export PATH=/opt/bin:/opt/sbin:/usr/bin:/bin:/usr/sbin:/sbin

# Один экземпляр: второй запуск сразу выходит.
LOCK=/tmp/voice-loop.lock
if ! mkdir "$LOCK" 2>/dev/null; then
    OLD=$(cat "$LOCK/pid" 2>/dev/null)
    if [ -n "$OLD" ] && kill -0 "$OLD" 2>/dev/null; then
        exit 0
    fi
    rm -rf "$LOCK"
    mkdir "$LOCK" 2>/dev/null || exit 0
fi
echo $$ > "$LOCK/pid"
trap 'rm -rf "$LOCK"' EXIT

LOG=/data/quasar/daemons_logs/maind.log
PLIM=/opt/share/plim.wav
LEDDIR=/system/vendor/quasar/ledpatterns
LEDUTIL=/system/vendor/quasar/led_util
MUSIC=/opt/bin/music.sh

. /opt/bin/voice-funcs.sh

log() {
    echo "$(date '+%H:%M:%S') $*" >> /data/quasar/voice-loop.log
}

kill_led() {
    for p in $(ps | grep led_util | grep -v grep | awk '{print $1}'); do
        kill "$p" 2>/dev/null
    done
}

led() {
    kill_led
    "$LEDUTIL" "$1" >/dev/null 2>&1 &
    sleep 0.2
}

stop_platform() {
    killall quasar_launcher2 2>/dev/null
    sleep 1
    for p in $(ps | grep maind | grep -v grep | awk '{print $1}'); do kill -9 "$p" 2>/dev/null; done
    sleep 1
}

start_platform() {
    /etc/init.d/S99onboot.sh start >/dev/null 2>&1
    sleep 10
}

music_playing() {
    [ -f /tmp/music.pid ] || return 1
    kill -0 "$(cat /tmp/music.pid 2>/dev/null)" 2>/dev/null
}

play_station() {
    touch /tmp/music.hold
    "$MUSIC" play "$1" >/dev/null 2>&1
    sleep 1
    NAME=$(cat /tmp/music.next 2>/dev/null)
    [ -n "$NAME" ] && /opt/bin/speak.sh "Станция $NAME" >/dev/null 2>&1
    rm -f /tmp/music.hold
    AFTER=1
    DONE=1
}

# Убирает из фразы «включи/найди/поставь … песню/трек …», оставляя название.
clean_query() {
    CQ="$1"
    case "$CQ" in
        "включи "*) CQ=${CQ#"включи "} ;;
        "Включи "*) CQ=${CQ#"Включи "} ;;
        "поставь "*) CQ=${CQ#"поставь "} ;;
        "Поставь "*) CQ=${CQ#"Поставь "} ;;
        "найди "*) CQ=${CQ#"найди "} ;;
        "Найди "*) CQ=${CQ#"Найди "} ;;
        "запусти "*) CQ=${CQ#"запусти "} ;;
        "Запусти "*) CQ=${CQ#"Запусти "} ;;
    esac
    case "$CQ" in
        "пожалуйста "*) CQ=${CQ#"пожалуйста "} ;;
        "Пожалуйста "*) CQ=${CQ#"Пожалуйста "} ;;
        "мне "*) CQ=${CQ#"мне "} ;;
        "Мне "*) CQ=${CQ#"Мне "} ;;
    esac
    case "$CQ" in
        "включи "*) CQ=${CQ#"включи "} ;;
        "Включи "*) CQ=${CQ#"Включи "} ;;
        "поставь "*) CQ=${CQ#"поставь "} ;;
        "Поставь "*) CQ=${CQ#"Поставь "} ;;
        "найди "*) CQ=${CQ#"найди "} ;;
        "Найди "*) CQ=${CQ#"Найди "} ;;
        "запусти "*) CQ=${CQ#"запусти "} ;;
        "Запусти "*) CQ=${CQ#"Запусти "} ;;
    esac
    case "$CQ" in
        "песню "*) CQ=${CQ#"песню "} ;;
        "Песню "*) CQ=${CQ#"Песню "} ;;
        "песня "*) CQ=${CQ#"песня "} ;;
        "Песня "*) CQ=${CQ#"Песня "} ;;
        "песни "*) CQ=${CQ#"песни "} ;;
        "Песни "*) CQ=${CQ#"Песни "} ;;
        "трек "*) CQ=${CQ#"трек "} ;;
        "Трек "*) CQ=${CQ#"Трек "} ;;
        "композицию "*) CQ=${CQ#"композицию "} ;;
        "Композицию "*) CQ=${CQ#"Композицию "} ;;
        "композиция "*) CQ=${CQ#"композиция "} ;;
        "Композиция "*) CQ=${CQ#"Композиция "} ;;
        "песню") CQ="" ;;
        "Песню") CQ="" ;;
        "песня") CQ="" ;;
        "Песня") CQ="" ;;
        "песни") CQ="" ;;
        "Песни") CQ="" ;;
        "трек") CQ="" ;;
        "Трек") CQ="" ;;
        "композицию") CQ="" ;;
        "Композицию") CQ="" ;;
        "композиция") CQ="" ;;
        "Композиция") CQ="" ;;
    esac
    CQ=$(printf '%s' "$CQ" | sed 's/[.,!?]*$//; s/^ *//; s/ *$//')
    printf '%s' "$CQ"
}

handle_wake() {
    log "wake"
    /opt/bin/aurora-voice-emit LISTENING ""
    rm -f /tmp/music.hold

    WAS=0
    if music_playing; then
        WAS=1
        "$MUSIC" pause >/dev/null 2>&1
    fi

    stop_platform

    led "$LEDDIR/rainbow_form.led"
    log "rainbow"

    for p in $(ps | grep aplay | grep -v grep | awk '{print $1}'); do kill "$p" 2>/dev/null; done
    /opt/bin/aplay -q "$PLIM" 2>/dev/null
    log "plim"

    led "$LEDDIR/spinner.led"
    log "record start"
    TXT=$(/opt/bin/stt.sh 7)
    if [ -f /tmp/stt8.wav ]; then
        log "record bytes: $(wc -c < /tmp/stt8.wav)"
    fi
    log "heard: $TXT"
    /opt/bin/aurora-voice-emit LISTENING "$TXT"

    led "$LEDDIR/burst.led"
    sleep 0.4
    led "$LEDDIR/yellow.led"
    /opt/bin/aurora-voice-emit THINKING "$TXT"

    AFTER=$WAS
    DONE=0

    if [ -n "$TXT" ]; then
        case "$TXT" in
            *включи*|*Включи*|*поставь*|*Поставь*|*переключ*|*Переключ*|*хочу*|*Хочу*)
                case "$TXT" in
                    *песн*|*Песн*|*трек*|*Трек*|*композиц*|*Композиц*) ;;
                    *)
                        case "$TXT" in
                            *европ*|*Европ*) play_station 1 ;;
                            *русск*|*Русск*) play_station 2 ;;
                            *дфм*|*Дфм*|*ДФМ*|*dfm*|*DFM*) play_station 3 ;;
                            *максим*|*Максим*|*maximum*|*Maximum*) play_station 4 ;;
                            *хит*|*Хит*|*hit*|*Hit*) play_station 5 ;;
                            *монте*|*Монте*|*monte*|*Monte*) play_station 6 ;;
                            *парадайз*|*Парадайз*|*paradise*|*Paradise*) play_station 7 ;;
                            *радио*рекорд*|*Радио*Рекорд*)
                                touch /tmp/music.hold
                                "$MUSIC" play 3 >/dev/null 2>&1
                                sleep 1
                                /opt/bin/speak.sh "Радио Рекорд недоступно, включаю DFM" >/dev/null 2>&1
                                rm -f /tmp/music.hold
                                AFTER=1
                                DONE=1
                                ;;
                        esac
                        ;;
                esac
                ;;
        esac
    fi

    if [ -n "$TXT" ] && [ "$DONE" = 0 ]; then
        case "$TXT" in
            *пауз*|*Пауз*|*стоп*|*Стоп*|*хватит*|*Хватит*|*замолч*|*Замолч*)
                "$MUSIC" pause >/dev/null 2>&1
                /opt/bin/speak.sh "Пауза" >/dev/null 2>&1
                AFTER=0
                ;;
            *выключи*|*Выключи*|*останови*|*Останови*)
                "$MUSIC" stop >/dev/null 2>&1
                /opt/bin/speak.sh "Выключаю" >/dev/null 2>&1
                AFTER=0
                ;;
            *волн*|*Волн*)
                rm -f /tmp/song.mode /tmp/song.queue /tmp/song.idx /tmp/song.current /tmp/song.playing
                rm -f /tmp/music.stop /tmp/music.pause
                touch /tmp/music.wave
                /opt/bin/speak.sh "Моя волна" >/dev/null 2>&1
                AFTER=1
                ;;
            *плейлист*|*Плейлист*|*любим*|*Любим*|*лайкнут*|*Лайкнут*)
                OUT=$(/opt/bin/song.sh liked)
                if [ -n "$OUT" ]; then
                    CNT=$(wc -l < /tmp/song.queue 2>/dev/null)
                    /opt/bin/speak.sh "Включаю мой плейлист. Треков: $CNT. Первый: $OUT" >/dev/null 2>&1
                    AFTER=1
                else
                    /opt/bin/speak.sh "В плейлисте пока пусто. Во время песни скажи лайк." >/dev/null 2>&1
                    AFTER=$WAS
                fi
                ;;
            *включи*песн*|*Включи*песн*|*поставь*песн*|*Поставь*песн*|*найди*песн*|*Найди*песн*|*включи*трек*|*Включи*трек*|*поставь*трек*|*Поставь*трек*|*найди*трек*|*Найди*трек*|*включи*композиц*|*Включи*композиц*|*найди*композиц*|*Найди*композиц*)
                Q=$(clean_query "$TXT")
                if [ -z "$Q" ]; then
                    /opt/bin/speak.sh "Какую песню включить?" >/dev/null 2>&1
                    AFTER=$WAS
                else
                    OUT=$(/opt/bin/song.sh "$Q")
                    log "song search: $Q -> $OUT"
                    if [ -n "$OUT" ]; then
                        /opt/bin/speak.sh "Включаю $OUT" >/dev/null 2>&1
                        AFTER=1
                    else
                        /opt/bin/speak.sh "Не нашла такую песню. Попробуй другое название." >/dev/null 2>&1
                        AFTER=$WAS
                    fi
                fi
                ;;
            *лайк*|*Лайк*|*нравится*|*Нравится*|*сохрани*|*Сохрани*)
                OUT=$(/opt/bin/music.sh like)
                if [ -n "$OUT" ]; then
                    /opt/bin/speak.sh "Добавила в плейлист: $OUT" >/dev/null 2>&1
                else
                    /opt/bin/speak.sh "Лайк работает, когда играет песня." >/dev/null 2>&1
                fi
                AFTER=$WAS
                ;;
            *мигалк*|*Мигалк*|*полиц*|*Полиц*|*включи*сирен*|*Включи*сирен*|*режим*сирен*|*Режим*сирен*|*режим*гаи*|*режим*ГАИ*|*Режим*гаи*|*Режим*ГАИ*)
                /opt/bin/speak.sh "Включаю мигалки" >/dev/null 2>&1
                /opt/bin/police.sh 12
                AFTER=$WAS
                ;;
            *следующ*|*Следующ*|*переключ*|*Переключ*|*дальше*|*Дальше*|*давай*еще*|*давай*ещё*)
                touch /tmp/music.hold
                "$MUSIC" next >/dev/null 2>&1
                sleep 1
                NAME=$(cat /tmp/music.next 2>/dev/null)
                MODE=$(cat /tmp/music.next.mode 2>/dev/null)
                if [ "$MODE" = "song" ]; then
                    [ -n "$NAME" ] && /opt/bin/speak.sh "Включаю $NAME" >/dev/null 2>&1
                else
                    [ -n "$NAME" ] && /opt/bin/speak.sh "Станция $NAME" >/dev/null 2>&1
                fi
                rm -f /tmp/music.hold
                AFTER=1
                ;;
            *предыдущ*|*Предыдущ*|*назад*|*Назад*)
                touch /tmp/music.hold
                "$MUSIC" prev >/dev/null 2>&1
                sleep 1
                NAME=$(cat /tmp/music.next 2>/dev/null)
                MODE=$(cat /tmp/music.next.mode 2>/dev/null)
                if [ "$MODE" = "song" ]; then
                    [ -n "$NAME" ] && /opt/bin/speak.sh "Включаю $NAME" >/dev/null 2>&1
                else
                    [ -n "$NAME" ] && /opt/bin/speak.sh "Станция $NAME" >/dev/null 2>&1
                fi
                rm -f /tmp/music.hold
                AFTER=1
                ;;
            *играет*|*Играет*|*станция*|*Станция*|*станцию*|*Станцию*|*станции*|*Станции*)
                ST=$("$MUSIC" status)
                case "$ST" in
                    playing:*)
                        /opt/bin/speak.sh "Сейчас играет ${ST#playing: }" >/dev/null 2>&1
                        ;;
                    paused:*)
                        /opt/bin/speak.sh "На паузе ${ST#paused: }" >/dev/null 2>&1
                        ;;
                    *)
                        /opt/bin/speak.sh "Сейчас ничего не играет" >/dev/null 2>&1
                        ;;
                esac
                ;;
            *радио*тише*|*Радио*тише*|*музык*тише*|*Музык*тише*|*тише*радио*|*Тише*радио*|*тише*музык*|*Тише*музык*)
                ST=$(text_steps "$TXT")
                OUT=$("$MUSIC" voldown "$ST")
                /opt/bin/speak.sh "Громкость радио $OUT" >/dev/null 2>&1
                AFTER=$WAS
                ;;
            *радио*громче*|*Радио*громче*|*музык*громче*|*Музык*громче*|*громче*радио*|*Громче*радио*|*громче*музык*|*Громче*музык*)
                ST=$(text_steps "$TXT")
                OUT=$("$MUSIC" volup "$ST")
                /opt/bin/speak.sh "Громкость радио $OUT" >/dev/null 2>&1
                AFTER=$WAS
                ;;
            *громче*|*Громче*|*увеличь*|*Увеличь*|*прибавь*|*Прибавь*|*больше*|*Больше*)
                volume_by + "$TXT"
                AFTER=$WAS
                ;;
            *тише*|*Тише*|*уменьш*|*Уменьш*|*убавь*|*Убавь*|*меньше*|*Меньше*)
                volume_by - "$TXT"
                AFTER=$WAS
                ;;
            *радио*|*Радио*)
                rm -f /tmp/song.mode /tmp/song.queue /tmp/song.idx /tmp/song.current /tmp/song.playing
                touch /tmp/music.wave
                /opt/bin/speak.sh "Включаю радио" >/dev/null 2>&1
                AFTER=1
                ;;
            *продолж*|*Продолж*|*играй*|*Играй*|*включи*|*Включи*|*музык*|*Музык*|*радио*|*Радио*)
                AFTER=1
                ;;
            *)
                ANS=$(/opt/bin/ask-only.sh "$TXT")
                log "answer: $ANS"
                /opt/bin/aurora-voice-emit ANSWERING "$ANS"
                if [ -n "$ANS" ]; then
                    /opt/bin/speak.sh "$ANS" >/dev/null 2>&1
                fi
                ;;
        esac
    else
        log "empty"
        /opt/bin/speak.sh "Не расслышала, повторите" >/dev/null 2>&1
    fi

    kill_led
    start_platform

    rm -f /tmp/music.hold

    if [ "$AFTER" = 1 ]; then
        "$MUSIC" start >/dev/null 2>&1
        "$MUSIC" resume >/dev/null 2>&1
    fi

    log "platform back"
    /opt/bin/aurora-voice-emit RESET
}

# maind.log ротируется (maind.log → maind.log.1), поэтому позиция в байтах
# становится недействительной: платформа видит «Алису», а цикл это событие проматывает.
# Надёжнее сравнивать саму строку события.
WAKE_MARK=/tmp/voice-loop.lastwake

while [ ! -f "$LOG" ]; do sleep 2; done

while true; do
    HIT=$(grep -a 'onPhraseSpotted' "$LOG" 2>/dev/null | tail -n 1)
    # событие могло попасть в уже ротированный файл прямо в момент смены лога
    if [ -z "$HIT" ] && [ -f "$LOG.1" ]; then
        HIT=$(grep -a 'onPhraseSpotted' "$LOG.1" 2>/dev/null | tail -n 1)
    fi
    if [ -z "$HIT" ]; then
        # лог очищен — старую метку забываем
        rm -f "$WAKE_MARK"
    else
        LAST=$(cat "$WAKE_MARK" 2>/dev/null)
        if [ "$HIT" != "$LAST" ]; then
            printf '%s\n' "$HIT" > "$WAKE_MARK"
            handle_wake
        fi
    fi
    sleep 1
done
