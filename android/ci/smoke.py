#!/usr/bin/env python3
"""
Проверка «Отбоя» на эмуляторе Android (запускается в GitHub Actions).

Ставит APK, проходит по экранам как человек, делает скриншоты и проверяет,
что приложение не падает. Затем ставит тестовый будильник, блокирует экран
и смотрит, что будильник сработал (или ждёт отбоя, если сейчас тревога).

Если передать третьим аргументом прошлую версию APK, сначала проверяется
автообновление: ставится прошлая версия, в «Ещё» нажимается «Обновить»,
и новая версия должна встать поверх.

Использование: smoke.py <apk> <папка для скриншотов> [прошлая версия apk]
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "ua.otboi.app"
APK, OUT = sys.argv[1], sys.argv[2]
PREV = sys.argv[3] if len(sys.argv) > 3 and os.path.exists(sys.argv[3]) else None
os.makedirs(OUT, exist_ok=True)
LOG = open(os.path.join(OUT, "summary.txt"), "w", encoding="utf-8")
problems = []


def log(msg):
    line = time.strftime("%H:%M:%S ") + msg
    print(line, flush=True)
    LOG.write(line + "\n")
    LOG.flush()


def adb(*args, timeout=120):
    try:
        return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(args, 1, "", "timeout")


def shell(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def shot(name):
    path = os.path.join(OUT, name + ".png")
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    with open(path, "wb") as f:
        f.write(data)
    log(f"скриншот {name}.png ({len(data)} байт)")


def screen_size():
    m = re.search(r"(\d+)x(\d+)", shell("wm size").stdout)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


W, H = 1080, 2400


def dump():
    for _ in range(4):
        r = shell("uiautomator dump /sdcard/ui.xml", timeout=40)
        if "dumped" in (r.stdout + r.stderr):
            xml = adb("exec-out", "cat", "/sdcard/ui.xml").stdout
            try:
                return ET.fromstring(xml)
            except ET.ParseError:
                pass
        time.sleep(2)
    return None


def texts():
    root = dump()
    if root is None:
        return []
    return [n.get("text") for n in root.iter("node") if n.get("text")]


def find(text, exact=False):
    root = dump()
    if root is None:
        return None
    for n in root.iter("node"):
        t = n.get("text") or ""
        d = n.get("content-desc") or ""
        ok = (t == text or d == text) if exact else (text in t or text in d)
        if ok:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "")))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def clear_dialogs():
    """Системные окна «… isn't responding» после загрузки эмулятора закрывают экран — убираем их."""
    for _ in range(6):
        t = texts()
        joined = " | ".join(t)
        # Системное окно ANR — отдельная строка «<приложение> isn't responding» (эмулятор на английском).
        anr = [x for x in t if x.strip().endswith("isn't responding")]
        if anr:
            if any(n in x for x in anr for n in ("bylarik", "Отбой", "Otboi")):
                problems.append("приложение зависло (ANR)")
                log("!!! приложение не отвечает: " + " | ".join(anr))
            p = find("Wait", exact=True) or find("Подождать", exact=True) or find("Close app", exact=True)
            if not p:
                return
            shell(f"input tap {p[0]} {p[1]}")
            log("закрыл системное окно: " + joined[:80])
            time.sleep(3)
        elif "keeps stopping" in joined or "has stopped" in joined:
            log("системное окно об ошибке: " + joined[:120])
            if any(n in joined for n in ("bylarik", "Отбой", "Otboi")):
                problems.append("приложение остановилось")
            p = find("Close app", exact=True) or find("OK", exact=True)
            if p:
                shell(f"input tap {p[0]} {p[1]}")
            time.sleep(2)
        else:
            return


def tap(text, exact=False, tries=3, wait=2.5):
    for _ in range(tries):
        clear_dialogs()
        p = find(text, exact)
        if p:
            shell(f"input tap {p[0]} {p[1]}")
            log(f"нажал «{text}»")
            time.sleep(wait)
            return True
        time.sleep(2)
    log(f"НЕ НАШЁЛ «{text}»")
    return False


def scroll_to(text, swipes=10):
    clear_dialogs()
    for _ in range(swipes):
        p = find(text)
        if p and p[1] < H * 0.85:
            return p
        shell(f"input swipe {W // 2} {int(H * 0.75)} {W // 2} {int(H * 0.35)} 350")
        time.sleep(1.5)
    return find(text)


def crash_text():
    lc = adb("logcat", "-d", "-b", "crash").stdout
    blocks = [b for b in lc.split("FATAL EXCEPTION") if PKG in b]
    return ("FATAL EXCEPTION" + blocks[0])[:4000] if blocks else ""


def alive():
    return shell(f"pidof {PKG}").stdout.strip() != ""


def check(step):
    c = crash_text()
    if c:
        problems.append(f"{step}: приложение упало")
        log(f"!!! ПАДЕНИЕ на шаге «{step}»:\n{c}")
        with open(os.path.join(OUT, "crash.txt"), "w", encoding="utf-8") as f:
            f.write(c)
        return False
    return True


def ringing():
    acts = shell("dumpsys activity activities", timeout=60).stdout
    return re.search(r"[Rr]esumed\w*[:=].*RingActivity", acts) is not None


def notif_text():
    out = shell("dumpsys notification --noredact", timeout=60).stdout
    keep = []
    for block in out.split("NotificationRecord")[1:]:
        if f"pkg={PKG}" in block:
            for k in ("android.title", "android.text"):
                m = re.search(k + r"=String \((.*?)\)", block)
                if m:
                    keep.append(m.group(1))
    return " | ".join(keep)


def version():
    m = re.search(r"versionName=(\S+)", shell(f"dumpsys package {PKG}").stdout)
    return m.group(1) if m else ""


def update_test():
    """Ставим прошлую версию и обновляемся из приложения до свежей (из релиза)."""
    r = adb("install", "-r", PREV, timeout=300)
    if "Success" not in r.stdout:
        log("прошлая версия не встала (" + (r.stdout + r.stderr).strip()[-120:] + ") — пропускаю проверку обновления")
        return
    old = version()
    log(f"ОБНОВЛЕНИЕ: стоит прошлая версия {old}")
    shell(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    shell(f"appops set {PKG} REQUEST_INSTALL_PACKAGES allow")
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(8)
    clear_dialogs()
    if not tap("Ещё", exact=True, wait=3):
        problems.append("обновление: не открылась вкладка «Ещё»")
        return
    p = scroll_to("Проверить обновления")
    if not p:
        problems.append("обновление: нет кнопки «Проверить обновления»")
        return
    shell(f"input tap {p[0]} {p[1]}")
    time.sleep(8)
    p = scroll_to("Доступна версия")
    shot("u1_dostupno_obnovlenie")
    if not p:
        problems.append(f"обновление: {old} не увидела новую версию")
        log("на экране: " + " / ".join(texts()[-20:]))
        return
    if not tap("Обновить", exact=True, wait=12):
        problems.append("обновление: нет кнопки «Обновить»")
        return
    shot("u2_ustanovshchik")
    log("окно установщика: " + " / ".join(texts()[:12]))
    for label in ("Update", "Обновить", "Install", "Установить"):
        if find(label, exact=True):
            tap(label, exact=True, wait=20)
            break
    new = version()
    log(f"ОБНОВЛЕНИЕ: было {old}, стало {new}")
    if not new or new == old:
        problems.append(f"обновление не установилось (осталась {old})")
    check("автообновление")
    shell(f"pm clear {PKG}")


def main():
    global W, H
    adb("wait-for-device")
    W, H = screen_size()
    log(f"экран {W}x{H}")
    # Время как в Украине.
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    shell("setprop persist.sys.timezone Europe/Kiev")
    shell("service call alarm 3 s16 Europe/Kiev")
    log("время на эмуляторе: " + shell("date").stdout.strip())

    shell("settings put system screen_off_timeout 600000")
    log("жду, пока система успокоится после загрузки")
    time.sleep(40)
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(5)
    clear_dialogs()

    if PREV:
        update_test()
    else:
        log("прошлой версии нет — проверку автообновления пропускаю")

    r = adb("install", "-r", APK, timeout=300)
    log("установка: " + (r.stdout + r.stderr).strip().splitlines()[-1])
    if "Success" not in r.stdout:
        problems.append("APK не установился")
        return
    ver = re.search(r"versionName=(\S+)", shell(f"dumpsys package {PKG}").stdout)
    log("версия: " + (ver.group(1) if ver else "?"))

    shell(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    shell(f"cmd deviceidle whitelist +{PKG}")
    shell(f"appops set {PKG} USE_FULL_SCREEN_INTENT allow")
    shell("settings put system screen_off_timeout 600000")
    adb("logcat", "-c")
    adb("logcat", "-b", "crash", "-c")

    # 1. Первый запуск — должен открыть выбор области.
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(8)
    clear_dialogs()
    shot("01_pervyi_zapusk")
    if not check("первый запуск") or not alive():
        problems.append("после запуска процесса нет")
        return

    # 2. Область → район → весь район.
    tap("Вінницька обл.", wait=6)
    shot("02_rayony")
    tap("Вінницький р-н", wait=3)
    shot("03_gromady")
    tap("Весь район", wait=6)
    check("выбор места")
    shot("04_glavnyi_ekran")
    log("на главном экране: " + " / ".join(texts()[:25]))

    # 3. Вкладки.
    tap("Новости", exact=True, wait=8)
    shot("05_novosti")
    check("новости")
    tap("Ещё", exact=True, wait=3)
    shot("06_eshche")
    check("ещё")

    if scroll_to("Экономия заряда"):
        shot("06b_ekonomiya")
    else:
        problems.append("нет переключателя «Экономия заряда»")

    # 4. Тестовый будильник при заблокированном экране.
    p = scroll_to("Тестовый будильник через 1 минуту")
    if not p:
        problems.append("не нашёл кнопку теста")
        return
    shell(f"input tap {p[0]} {p[1]}")
    log("поставил тестовый будильник")
    time.sleep(2)
    shot("07_test_postavlen")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    shell("input keyevent KEYCODE_SLEEP")
    log("экран выключен, жду срабатывания")
    rang = False
    for _ in range(26):  # до ~130 с
        time.sleep(5)
        if ringing():
            rang = True
            break
    time.sleep(3)
    log("уведомление: " + notif_text())
    if rang:
        log("БУДИЛЬНИК ЗВОНИТ — экран будильника поверх блокировки")
        shot("08_zvonit")
        tap("Выключить", exact=True, wait=3)
        shot("09_vyklyuchen")
    else:
        # Возможно, сейчас тревога и будильник ждёт отбоя — это тоже правильно.
        n = notif_text()
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        time.sleep(2)
        shell(f"am start -n {PKG}/.ui.MainActivity")
        time.sleep(5)
        shot("08_posle_testa")
        if "ждёт отбоя" in n or "ждёт отбоя" in " ".join(texts()):
            log("сейчас тревога — будильник правильно ждёт отбоя")
            tap("Будильники", exact=True, wait=3)
            shot("09_zhdyot_otboya")
            p2 = scroll_to("Разбудить сейчас")
            if p2:
                shell(f"input tap {p2[0]} {p2[1]}")
                log("нажал «Разбудить сейчас»")
            for _ in range(6):
                time.sleep(2)
                if ringing():
                    break
            if ringing():
                shot("10_zvonit_po_knopke")
                tap("Выключить", exact=True, wait=3)
            else:
                problems.append("кнопка «Разбудить сейчас» не включила звонок")
        else:
            problems.append("тестовый будильник не сработал")
    check("тестовый будильник")
    shot("11_konets")


try:
    main()
except Exception as e:  # noqa: BLE001
    problems.append(f"скрипт проверки: {e!r}")
    log(f"ошибка скрипта: {e!r}")
finally:
    try:
        with open(os.path.join(OUT, "logcat.txt"), "w", encoding="utf-8") as f:
            f.write(adb("logcat", "-d", "-v", "time", timeout=60).stdout[-400_000:])
    except Exception:  # noqa: BLE001
        pass
    log("ИТОГ: " + ("всё работает" if not problems else "ПРОБЛЕМЫ: " + "; ".join(problems)))
    LOG.close()
    sys.exit(1 if problems else 0)
