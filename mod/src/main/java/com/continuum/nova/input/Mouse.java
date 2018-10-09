package com.continuum.nova.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import com.continuum.nova.NovaRenderer;
import com.continuum.nova.system.MouseScrollEvent;
import com.continuum.nova.system.MouseButtonEvent;
import com.continuum.nova.system.MousePositionEvent;
import com.continuum.nova.system.NovaNative;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class Mouse {
    private static final Logger LOG = LogManager.getLogger(Mouse.class);

    private static boolean created;
    private static int lastX;
    private static int lastY;
    private static int x;
    private static int y;
    private static int absolute_x;
    private static int absolute_y;
    private static int dx;
    private static int dy;
    private static int dwheel;
    private static int buttonCount = -1;
    private static boolean hasWheel;
    private static String[] buttonName;
    private static final Map<String, Integer> buttonMap = new HashMap<>(16);
    private static final HashSet<Integer> buttonDownBuffer = new HashSet<>();
    private static boolean initialized;
    private static int eventButton;
    private static boolean eventState;
    private static int event_dwheel;
    private static long event_nanos;
    private static int grab_x;
    private static int grab_y;
    private static int last_event_raw_x;
    private static int last_event_raw_y;
    private static boolean isGrabbed;

    public static boolean isClipMouseCoordinatesToWindow() {
        return false;
    }

    public static void setClipMouseCoordinatesToWindow(boolean clip) {

    }

    public static void setCursorPosition(int new_x, int new_y) {

    }

    private static void initialize() {
        if (!initialized) {
            buttonName = new String[16];

            for (int i = 0; i < 16; ++i) {
                buttonName[i] = "BUTTON" + i;
                buttonMap.put(buttonName[i], Integer.valueOf(i));
            }
            initialized = true;
        }
    }

    private static void resetMouse() {
        dwheel = 0;
        dy = 0;
        dx = 0;
    }


    public static void create() {
        if (!created) {
            initialize();
            created = true;
        }
    }

    public static boolean isCreated() {
        return created;
    }

    public static void destroy() {

    }


    public static boolean isButtonDown(int button) {
        return buttonDownBuffer.contains(button);
    }

    public static String getButtonName(int button) {
        return button < buttonName.length && button >= 0 ? buttonName[button] : null;
    }

    public static int getButtonIndex(String buttonName) {
        Integer ret = buttonMap.get(buttonName);
        return ret == null ? -1 : ret.intValue();
    }

    public static boolean next() {
        lastX = x;
        lastY = y;
        MouseButtonEvent buttonEvent = NovaNative.getNextMouseButtonEvent();
        MousePositionEvent positionEvent = NovaNative.getNextMousePositionEvent();
        MouseScrollEvent scrollEvent = NovaNative.getNextMouseScrollEvent();
        if (buttonEvent.filled == 0 && positionEvent.filled == 0 && scrollEvent.filled == 0) {
            return false;
        }
        if (buttonEvent.filled == 1) {
            if (buttonEvent.action == 1) {
                buttonDownBuffer.add(buttonEvent.button);

            } else {
                buttonDownBuffer.remove(buttonEvent.button);
            }
            eventButton = buttonEvent.button;
            eventState = buttonEvent.action == 1;
            LOG.trace("button: " + buttonEvent.button + ";action: " + buttonEvent.action + ";mods: " + buttonEvent.mods + "; filled: " + buttonEvent.filled);

        } else {
            eventButton = -1;
            eventState = false;
        }
        if (positionEvent.filled == 1) {
            dx += positionEvent.xpos - x;
            dy += positionEvent.ypos - y;
            x = positionEvent.xpos;
            y = positionEvent.ypos;
            LOG.trace("dx: {} dy: {}", dx, dy);
        }
        if (scrollEvent.filled == 1) {
            event_dwheel = (int) scrollEvent.yoffset;
            LOG.trace("button: " + buttonEvent.button + ";action: " + buttonEvent.action + ";mods: " + buttonEvent.mods + "; filled: " + buttonEvent.filled);
        } else {
            event_dwheel = 0;
        }

        return true;
    }

    public static int getEventButton() {
        return eventButton;
    }

    public static boolean getEventButtonState() {
        return eventState;
    }


    public static int getEventX() {
        return x;
    }

    public static int getEventY() {
        return y;
    }

    public static int getEventDWheel() {
        return event_dwheel;
    }

    public static long getEventNanoseconds() {
        return event_nanos;
    }

    public static int getX() {
        return x;
    }

    public static int getY() {
        return y;
    }

    public static int getDX() {
        int result = dx;
        dx = 0;
        return result;
    }

    public static int getDY() {
        int result = dy;
        dy = 0;
        return result;
    }

    public static int getDWheel() {
        int result = dwheel;
        dwheel = 0;
        return result;
    }

    public static int getButtonCount() {
        return buttonCount;
    }

    public static boolean hasWheel() {
        return hasWheel;
    }

    public static boolean isGrabbed() {
        return isGrabbed;
    }

    public static void setGrabbed(boolean grab) {
        NovaNative.setMouseGrabbed(grab);
        isGrabbed = grab;
    }

    public static void updateCursor() {
        next();
    }

    public static boolean isInsideWindow() {
        return true;
    }
}
