package com.mcplugin.infrastructure.commands.subcommands;

import com.mcplugin.infrastructure.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public final class CilistCommand {

    private CilistCommand() {}

    public static void execute(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gold>  ✦ </gold><white>Кастомные предметы</white> <gray>(крафт только в сборщике)</gray>"));
        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(Component.empty());

        // 1. Свинцовый слиток
        sender.sendMessage(MessageUtil.parse("<yellow>1. Свинцовый слиток</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Незеритовый слиток</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Железо Железо</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Незерит Железо</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Железо Железо</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>Используется для крафта свинцового щита</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 2. Свинцовый щит
        sender.sendMessage(MessageUtil.parse("<yellow>2. Свинцовый щит</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Щит</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Свинец Свинец Свинец</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Свинец Щит Свинец</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Свинец Свинец Свинец</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>Защищает от радиации при держании в руке</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 3. Локатор существ
        sender.sendMessage(MessageUtil.parse("<yellow>3. Локатор существ</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Компас восстановления</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Факел Компас</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Редстоун Ред.блок Компаратор</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Бриз.палка Бриз.палка Крюк</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>Показывает расстояние до ближайшей сущности (action bar)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 4. Photon cannon
        sender.sendMessage(MessageUtil.parse("<yellow>4. Photon cannon</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Warped fungus on a stick</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Purpur Стекло Нез.звезда</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Эхо-осколок Сердце моря Стекло</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Бриз.палка Эхо-осколок Purpur</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>Стреляет эхо-осколками (патрон в оффхенд)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 5. Electro Shoker
        sender.sendMessage(MessageUtil.parse("<yellow>5. Electro Shoker</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Warped fungus on a stick</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Чёрн.бетон Жёлт.бетон Огн.стержень</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Жёлт.бетон Чёрн.бетон Бриз.палка</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Палка Нез.лом Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>Оглушает врагов электричеством (патрон — бриз.палка)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 6. Колба с антиматерией
        sender.sendMessage(MessageUtil.parse("<yellow>6. Колба с антиматерией</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Взрывное зелье</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Нез.лом Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Стекло Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Нез.лом Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>При броске создаёт мощный взрыв с радиацией</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 7. Multimeter
        sender.sendMessage(MessageUtil.parse("<yellow>7. Multimeter</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Часы</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Алмаз Железо</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Алмаз Часы Алмаз</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Алмаз Железо</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>ПКМ по кабелю/батарее — показать информацию об энергии</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 8. Измеритель здоровья
        sender.sendMessage(MessageUtil.parse("<yellow>8. Измеритель здоровья</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Бирка</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Лазурит Железо</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Лазурит Сердце моря Лазурит</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Лазурит Железо</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>ПКМ — проверить здоровье существа, на которое смотришь</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 9. Рудоискатель
        sender.sendMessage(MessageUtil.parse("<yellow>9. Рудоискатель</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Компас</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Редстоун Железо</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Алмаз Золото Алмаз</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Железо Редстоун Железо</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>ПКМ — сканировать чанк и показать количество руд</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 10. Мобоискатель
        sender.sendMessage(MessageUtil.parse("<yellow>10. Мобоискатель</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Подзорная труба</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Бриз.палка Нез.лом Бриз.палка</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Железо Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Бриз.палка Нез.лом Бриз.палка</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>ПКМ — сканировать чанк и показать количество мобов</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        // 11. Портативный радар
        sender.sendMessage(MessageUtil.parse("<yellow>11. Портативный радар</yellow>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Материал: </white><gray>Око Края</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Рецепт:</white>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Око края Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Око края Ред.блок Око края</gray>"));
        sender.sendMessage(MessageUtil.parse("   <gray>Нез.лом Око края Нез.лом</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <white>Описание: </white><gray>ПКМ — найти ближайшую сущность в 64 блоках (тип, координаты, расстояние)</gray>"));
        sender.sendMessage(MessageUtil.parse(" <gray>▪</gray> <red>Только в сборщике</red>"));
        sender.sendMessage(Component.empty());

        sender.sendMessage(MessageUtil.parse("<gold>═══════════════════════════════════════════════════</gold>"));
        sender.sendMessage(MessageUtil.parse("<gray>💡 Установите верстак и используйте его как сборщик</gray>"));
        sender.sendMessage(MessageUtil.parse("<gray>   для крафта всех кастомных предметов!</gray>"));
        sender.sendMessage(Component.empty());
    }
}
