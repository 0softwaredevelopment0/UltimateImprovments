package com.ultimateimprovements.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для авто-обнаружения модулей плагина.
 * <p>
 * Класс должен extends {@link com.ultimateimprovements.module.PluginModule}.
 * После добавления аннотации модуль автоматически регистрируется в ModuleManager —
 * не нужно править PluginStartup.
 * <p>
 * Пример:
 * <pre>{@code
 * @ModuleInfo(name = "MyFeature", path = "features/my", essential = false)
 * public class MyFeatureModule extends PluginModule { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInfo {
    /** Отображаемое имя модуля. По умолчанию — имя класса без "Module". */
    String name() default "";

    /** Путь в иерархии модулей (например "energy/generation/basic"). */
    String path() default "";

    /** Является ли модуль критическим для работы плагина. */
    boolean essential() default false;
}
