#!/usr/bin/env python3
"""
Проверка «Отбоя» на эмуляторе Android (запускается в GitHub Actions).

Ставит APK, проходит по экранам как человек, делает скриншоты и проверяет,
что приложение не падает. Затем ставит тестовый будильник, блокирует экран
и смотрит, что будильник сработал (или ждёт отбоя, если сейчас тревога).

Режимы:
  smoke.py check  <apk> <папка> [<apk со старым номером>] — полная проверка новой версии (до выпуска);
      третий APK — та же сборка с номером 1: на ней проверяется окно «Вышла версия …» при входе
  smoke.py update <прошлая apk> <папка>  — проверка автообновления (после выпуска):
      ставится прошлая версия, в «Ещё» нажимается «Обновить», должна встать свежая из релиза.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "ua.otboi.app"
MODE, APK, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
OLD_APK = sys.argv[4] if len(sys.argv) > 4 else ""
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


def set_lang(tag):
    """Язык приложения (как «Язык приложения» в настройках Android 13+)."""
    shell(f"cmd locale set-app-locales {PKG} --locales {tag}")
    shell(f"am force-stop {PKG}")
    time.sleep(1)
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(6)
    clear_dialogs()
    log(f"язык приложения: {tag}")


def open_tab(name):
    return tap(name, exact=True, wait=3)


def settle():
    shell("settings put system screen_off_timeout 600000")
    log("жду, пока система успокоится после загрузки")
    time.sleep(40)
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(5)
    clear_dialogs()


def prepare():
    global W, H
    adb("wait-for-device")
    W, H = screen_size()
    log(f"экран {W}x{H}")
    adb("root")
    time.sleep(3)
    adb("wait-for-device")
    shell("setprop persist.sys.timezone Europe/Kiev")
    shell("service call alarm 3 s16 Europe/Kiev")
    log("время на эмуляторе: " + shell("date").stdout.strip())
    settle()


def grant():
    shell(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
    shell(f"cmd deviceidle whitelist +{PKG}")
    shell(f"appops set {PKG} USE_FULL_SCREEN_INTENT allow")
    shell(f"appops set {PKG} REQUEST_INSTALL_PACKAGES allow")


def test_alarm(button, wake_label, dismiss_label, prefix, main_tab, not_today):
    """Тестовый будильник при выключенном экране: звонит, или ждёт отбоя (если сейчас тревога)."""
    p = scroll_to(button)
    if not p:
        problems.append(f"нет кнопки «{button}»")
        return
    shell(f"input tap {p[0]} {p[1]}")
    log("поставил тестовый будильник")
    time.sleep(2)
    shell("input keyevent KEYCODE_HOME")
    time.sleep(1)
    shell("input keyevent KEYCODE_SLEEP")
    rang = False
    for _ in range(26):
        time.sleep(5)
        if ringing():
            rang = True
            break
    time.sleep(3)
    log("уведомление: " + notif_text())
    if rang:
        log("БУДИЛЬНИК ЗВОНИТ поверх блокировки")
        shot(f"{prefix}_zvonit")
        tap(dismiss_label, exact=True, wait=3)
        return
    n = notif_text()
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    time.sleep(2)
    shell(f"am start -n {PKG}/.ui.MainActivity")
    time.sleep(5)
    open_tab(main_tab)
    if "отбо" in n.lower() or "all-clear" in n.lower() or "відбо" in n.lower():
        log("сейчас тревога — будильник правильно ждёт отбоя")
        shot(f"{prefix}_zhdyot_otboya")
        p2 = scroll_to(wake_label)
        if p2:
            shell(f"input tap {p2[0]} {p2[1]}")
        for _ in range(6):
            time.sleep(2)
            if ringing():
                break
        if ringing():
            shot(f"{prefix}_zvonit")
            tap(dismiss_label, exact=True, wait=3)
        else:
            problems.append("кнопка «разбудить сейчас» не включила звонок")
            # Снимаем ожидание, чтобы не мешало дальше.
            p3 = scroll_to(not_today)
            if p3:
                shell(f"input tap {p3[0]} {p3[1]}")
                time.sleep(2)
    else:
        problems.append("тестовый будильник не сработал")


def wait_for(text, seconds=20, exact=True):
    for _ in range(max(1, seconds // 2)):
        p = find(text, exact)
        if p:
            return p
        time.sleep(2)
    return None


def prompt_test():
    """Окно «Вышла версия …» при входе. Копия с номером сборки 1 видит в релизах версию новее."""
    r = adb("install", "-r", OLD_APK, timeout=300)
    if "Success" not in r.stdout:
        problems.append("копия для проверки обновления не встала")
        return
    grant()
    adb("logcat", "-b", "crash", "-c")
    set_lang("ru-RU")
    p = wait_for("Позже", 24)
    shot("00a_ru_okno_obnovleniya")
    if not p:
        problems.append("при входе не появилось окно обновления")
        log("на экране: " + " / ".join(texts()[:20]))
        return
    log("окно обновления: " + " / ".join(texts()[:12]))
    shell(f"input tap {p[0]} {p[1]}")
    time.sleep(2)
    if find("Позже", exact=True):
        problems.append("«Позже» не закрыло окно обновления")
    # Снова зашли — после «Позже» окно не пристаёт, а обновление ждёт в «Ещё».
    shell(f"am force-stop {PKG}")
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(10)
    if find("Позже", exact=True):
        problems.append("окно обновления вернулось сразу после «Позже»")
    if open_tab("Ещё") and scroll_to("Вышла версия"):
        shot("00b_ru_obnovlenie_v_eshche")
    else:
        problems.append("после «Позже» обновления нет в «Ещё»")
    # «Обновить» в окне: скачивает и открывает системный установщик.
    shell(f"pm clear {PKG}")
    grant()
    set_lang("ru-RU")
    if wait_for("Позже", 24) and tap("Обновить", exact=True, wait=3):
        ok = None
        for _ in range(25):
            ok = find("Update", exact=True) or find("Install", exact=True) or find("update this app")
            if ok:
                break
            time.sleep(2)
        shot("00c_ustanovshchik")
        log("после «Обновить»: " + " / ".join(texts()[:12]))
        if not ok:
            problems.append("окно обновления: не открылся установщик")
        tap("Cancel", exact=True, wait=2) or shell("input keyevent KEYCODE_BACK")
    else:
        problems.append("окно обновления не появилось второй раз")
    check("окно обновления")
    adb("uninstall", PKG)
    log("копию для проверки обновления удалил")


def widget_test():
    """Кнопка «Добавить на главный экран» → окно launcher → виджет на главном экране."""
    open_tab("Ещё")
    p = scroll_to("Добавить на главный экран")
    if not p:
        shot("06a_vidzhet_net_knopki")
        problems.append("нет кнопки «Добавить на главный экран»")
        return
    shell(f"input tap {p[0]} {p[1]}")
    time.sleep(4)
    shot("06a_vidzhet_zapros")
    log("окно добавления виджета: " + " / ".join(texts()[:15]))
    for label in ("Add to home screen", "ADD TO HOME SCREEN", "Add automatically", "ADD AUTOMATICALLY", "Add", "ADD"):
        q = find(label, exact=True)
        if q:
            shell(f"input tap {q[0]} {q[1]}")
            log(f"нажал «{label}»")
            time.sleep(4)
            break
    else:
        log("НЕ НАШЁЛ кнопку добавления виджета")
    bound = "ua.otboi.app.widget.ClockWidget" in shell("dumpsys appwidget", timeout=60).stdout
    log("виджет добавлен в систему: " + ("да" if bound else "нет"))
    if not bound:
        problems.append("виджет не добавился")
    shell("input keyevent KEYCODE_HOME")
    time.sleep(5)
    shot("06b_vidzhet_na_glavnom")
    log("на главном экране телефона: " + " / ".join(texts()[:30]))
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(4)
    open_tab("Ещё")
    if scroll_to("Виджет уже на главном экране"):
        shot("06c_vidzhet_v_eshche")
    elif bound:
        problems.append("«Ещё» не видит добавленный виджет")
    check("виджет")


def check_mode():
    if OLD_APK:
        prompt_test()
    r = adb("install", "-r", APK, timeout=300)
    log("установка: " + (r.stdout + r.stderr).strip().splitlines()[-1])
    if "Success" not in r.stdout:
        problems.append("APK не установился")
        return
    log("версия: " + version())
    grant()
    adb("logcat", "-c")
    adb("logcat", "-b", "crash", "-c")

    # 1. Русский: первый запуск, выбор места, главный экран.
    set_lang("ru-RU")
    shot("01_ru_pervyi_zapusk")
    if not check("первый запуск") or not alive():
        problems.append("после запуска процесса нет")
        return
    tap("Вінницька обл.", wait=6)
    tap("Вінницький р-н", wait=3)
    tap("Весь район", wait=6)
    check("выбор места")
    # Включаем будильник 07:30, чтобы на главном экране был следующий подъём.
    p = find("Будильник 07:30", exact=True)
    if p:
        shell(f"input tap {p[0]} {p[1]}")
        time.sleep(3)
    else:
        log("НЕ НАШЁЛ переключатель будильника 07:30")
    time.sleep(6)  # погода
    shot("02_ru_glavnyi")
    log("на главном экране: " + " / ".join(texts()[:30]))
    if not any("ПОГОДА" in t for t in texts()):
        problems.append("нет карточки погоды")
    widget_test()
    open_tab("Будильники")

    # 2. Редактор: звук.
    if tap("Пн–Пт", wait=3):
        shot("03_ru_redaktor")
        if scroll_to("Плавно нарастает"):
            shot("04_ru_zvuk")
        else:
            problems.append("нет раздела звука в редакторе")
        p = find("Послушать", exact=True)
        if p:
            shell(f"input tap {p[0]} {p[1]}")
            time.sleep(2)
            check("прослушивание мелодии")
            p = find("Стоп", exact=True)
            if p:
                shell(f"input tap {p[0]} {p[1]}")
        shell("input keyevent KEYCODE_BACK")
        time.sleep(2)
    else:
        problems.append("не открылся редактор будильника")
    check("редактор")

    # 3. Новости и «Ещё».
    open_tab("Новости")
    time.sleep(5)
    shot("05_ru_novosti")
    open_tab("Ещё")
    shot("06_ru_eshche")
    if scroll_to("Тема"):
        shot("07_ru_temy")
    for theme, name in (("Светлая", "light"), ("Графит", "graphite"), ("Океан", "ocean"), ("Ночь", "night")):
        open_tab("Ещё")
        p = scroll_to(theme)
        if not p:
            problems.append(f"нет темы «{theme}»")
            continue
        shell(f"input tap {p[0]} {p[1]}")
        time.sleep(2)
        open_tab("Будильники")
        time.sleep(2)
        shot(f"08_tema_{name}")
        if name == "light":
            shell("input keyevent KEYCODE_HOME")
            time.sleep(3)
            shot("08b_vidzhet_svetlyi")
            shell(f"am start -W -n {PKG}/.ui.MainActivity")
            time.sleep(3)
    check("темы")
    open_tab("Ещё")
    if scroll_to("Удалить приложение"):
        shot("09_ru_udalit")
    else:
        problems.append("нет кнопки удаления")
    check("ещё")

    # 4. Тестовый будильник (русский).
    open_tab("Ещё")
    test_alarm("Тестовый будильник через минуту", "Разбудить сейчас", "Выключить", "10_ru", "Будильники", "Сегодня не будить")
    check("тестовый будильник")

    # 5. Украинский и английский: главный экран и «Ещё».
    set_lang("uk-UA")
    shot("11_uk_glavnyi")
    open_tab("Ще")
    shot("12_uk_shche")
    check("украинский")
    set_lang("en-US")
    open_tab("Alarms")
    time.sleep(3)
    shot("13_en_main")
    if tap("Mon–Fri", wait=3):
        shot("14_en_editor")
        if scroll_to("Gentle wake-up"):
            shot("15_en_sound")
        shell("input keyevent KEYCODE_BACK")
        time.sleep(2)
    else:
        problems.append("en: не открылся редактор")
    open_tab("More")
    shot("16_en_more")
    if scroll_to("Theme"):
        shot("17_en_themes")
    open_tab("Place")
    shot("18_en_place")
    open_tab("More")
    p = scroll_to("Light")
    if p:
        shell(f"input tap {p[0]} {p[1]}")
        time.sleep(2)
        open_tab("Alarms")
        time.sleep(2)
        shot("18b_en_light")
        open_tab("More")
        p = scroll_to("Night")
        if p:
            shell(f"input tap {p[0]} {p[1]}")
            time.sleep(2)
    open_tab("More")
    test_alarm("Test alarm in one minute", "Wake me now", "Dismiss", "19_en", "Alarms", "Don't wake me today")
    check("английский")
    shot("20_konets")


def update_mode():
    """Прошлая версия находит обновление в релизе и ставит его поверх."""
    r = adb("install", "-r", APK, timeout=300)
    if "Success" not in r.stdout:
        problems.append("прошлая версия не встала: " + (r.stdout + r.stderr).strip()[-120:])
        return
    old = version()
    log(f"ОБНОВЛЕНИЕ: стоит прошлая версия {old}")
    grant()
    set_lang("ru-RU")
    # С 1.4 приложение само предлагает обновиться при входе.
    if wait_for("Позже", 20):
        shot("u1_okno")
        if not tap("Обновить", exact=True, wait=12):
            problems.append("обновление: в окне нет кнопки «Обновить»")
            return
        installer_and_check(old)
        return
    if not open_tab("Ещё"):
        problems.append("обновление: не открылась вкладка «Ещё»")
        return
    p = scroll_to("Проверить обновления")
    if not p:
        problems.append("обновление: нет кнопки «Проверить обновления»")
        return
    shell(f"input tap {p[0]} {p[1]}")
    time.sleep(8)
    p = scroll_to("Вышла версия") or scroll_to("Доступна версия")
    shot("u1_dostupno")
    if not p:
        problems.append(f"обновление: {old} не увидела новую версию")
        log("на экране: " + " / ".join(texts()[-20:]))
        return
    if not tap("Обновить", exact=True, wait=12):
        problems.append("обновление: нет кнопки «Обновить»")
        return
    installer_and_check(old)


def installer_and_check(old):
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
    shell(f"am start -W -n {PKG}/.ui.MainActivity")
    time.sleep(6)
    shot("u3_posle")
    check("после обновления")


try:
    prepare()
    if MODE == "update":
        update_mode()
    else:
        check_mode()
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
