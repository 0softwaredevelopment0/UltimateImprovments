package com.ultimateimprovments.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for auto-discovery of plugin modules.
 * <p>
 * The class must extend {@link com.ultimateimprovments.module.PluginModule}.
 * Once the annotation is added, the module is automatically registered in ModuleManager —
 * no need to edit PluginStartup.
 * <p>
 * Example:
 * <pre>{@code
 * @ModuleInfo(name = "MyFeature", path = "features/my", essential = false)
 * public class MyFeatureModule extends PluginModule { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInfo {
    /** Display name of the module. Defaults to the class name without "Module". */
    String name() default "";

    /** Path in the module hierarchy (e.g. "energy/generation/basic"). */
    String path() default "";

    /** Whether the module is critical for the plugin to work. */
    boolean essential() default false;
}
