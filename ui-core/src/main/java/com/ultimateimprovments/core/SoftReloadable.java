package com.ultimateimprovments.core;

/**
 * Optional in-place restart hook for UI-* addon main classes.
 *
 * @deprecated {@code /ui reload} now performs a soft hot-reload via
 * {@link HotReloadEngine}: onDisable → reloadConfig → onEnable on the same
 * instance. This interface is no longer dispatched by any command; addon main
 * classes keep implementing it only as a no-cost marker.
 */
@Deprecated
public interface SoftReloadable {

    /** Restarts this addon's systems in place (no plugin disable/enable). */
    void softReload();
}
