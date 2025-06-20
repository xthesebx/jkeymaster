package com.tulskiy.keymaster.windows;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.tulskiy.keymaster.common.HotKey;
import com.tulskiy.keymaster.common.HotKeyListener;
import com.tulskiy.keymaster.common.MediaKey;
import com.tulskiy.keymaster.common.Provider;

import javax.swing.*;
import java.util.*;

public class WindowsProvider extends Provider implements WinUser.HOOKPROC {
  private static WinUser.HHOOK hHook;
  private static final User32 USER32 = User32.INSTANCE;
  private static final Set<Integer> pressedKeys = new HashSet<>();
  public List<HotKey> hotKeys = new ArrayList<>();
  HashMap<Object, Integer> hotKeyMap = new HashMap<>();
  boolean running;
  boolean hotkeyPressed;

  @Override
  protected void init() {
    new Thread(this::startHook).start();
  }

  @Override
  public void reset() {
    stopHook();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public void register(KeyStroke keyCode, HotKeyListener listener) {
    HotKey hotKey = new HotKey(keyCode, listener);
    hotKeys.add(hotKey);
    hotKeyMap.put(hotKey.keyStroke, hotKeys.size());
  }

  @Override
  public void register(MediaKey mediaKey, HotKeyListener listener) {
    HotKey hotKey = new HotKey(mediaKey, listener);
    hotKeys.add(hotKey);
    hotKeyMap.put(hotKey.mediaKey, hotKeys.size());
  }

  @Override
  public void unregister(KeyStroke keyCode) {
    hotKeys.remove((int) hotKeyMap.get(keyCode));
  }

  @Override
  public void unregister(MediaKey mediaKey) {
    hotKeys.remove((int) hotKeyMap.get(mediaKey));
  }

  public interface LowLevelKeyboardProc extends WinUser.HOOKPROC {
    WinDef.LRESULT callback(int nCode, WinDef.WPARAM wParam, WinUser.KBDLLHOOKSTRUCT info);
  }

  private final LowLevelKeyboardProc keyboardHook = new LowLevelKeyboardProc() {
    @Override
    public WinDef.LRESULT callback(int nCode, WinDef.WPARAM wParam, WinUser.KBDLLHOOKSTRUCT info) {
        if (nCode >= 0) {
          int vkCode = info.vkCode;

          boolean keyDown = (wParam.intValue() == WinUser.WM_KEYDOWN || wParam.intValue() == WinUser.WM_SYSKEYDOWN);
          boolean keyUp = (wParam.intValue() == WinUser.WM_KEYUP || wParam.intValue() == WinUser.WM_SYSKEYUP);

          if (keyDown) {

            pressedKeys.add(vkCode);
            hotKeys.forEach(hotkey -> {
              if (pressedKeys.contains(KeyMap.getCode(hotkey))) {
                fireEvent(hotkey);
                hotkeyPressed = true;
              }
            });
          } else if (keyUp) {
            pressedKeys.remove(vkCode);
          }
        }
        return USER32.CallNextHookEx(hHook, nCode, wParam, new WinDef.LPARAM(Pointer.nativeValue(info.getPointer())));
      }

  };

  public void startHook() {
    running = true;
    WinDef.HMODULE hMod = Kernel32.INSTANCE.GetModuleHandle(null);
    Thread t = new Thread(() -> {
      hHook = USER32.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, keyboardHook, hMod, 0);

      WinUser.MSG msg = new WinUser.MSG();
      USER32.GetMessage(msg, null, 0, 0);
      USER32.TranslateMessage(msg);
      USER32.DispatchMessage(msg);
    });
    t.start();
  }

  public void stopHook() {
    if (hHook != null) {
      USER32.UnhookWindowsHookEx(hHook);
      running = false;
    }
  }
}