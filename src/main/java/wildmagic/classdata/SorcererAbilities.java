package wildmagic.classdata;

import java.util.List;

public final class SorcererAbilities {
    private SorcererAbilities() {}

    public static List<AbilityDefinition> create() {
        return List.of(
            new AbilityDefinition("sorcerer_dragon_breath", "Дыхание дракона", "Конус стихийного урона своей стихии на 6 блоков. Огонь — поджигает, Лёд — замедляет, Молния — оглушает, Яд — отравляет, Гром — отталкивает.", 1, 0, 60),
            new AbilityDefinition("sorcerer_elemental_burst", "Стихийный взрыв", "AOE взрыв своей стихии в радиусе 5 блоков вокруг чародея.", 3, 0, 80),
            new AbilityDefinition("sorcerer_draconic_wings", "Драконьи крылья", "Даёт полёт как элитра на 15 секунд.", 5, 0, 70),
            new AbilityDefinition("sorcerer_metamagic", "Метамагия", "Следующее заклинание не тратит ману.", 4, 0, 50),
            new AbilityDefinition("sorcerer_twinned_spell", "Сдвоенное заклинание", "Следующий луч или снаряд поражает двух ближайших целей.", 6, 0, 60),
            new AbilityDefinition("sorcerer_quicken_spell", "Ускоренное заклинание", "Сбрасывает КД всех слотов.", 8, 0, 90),
            new AbilityDefinition("sorcerer_wild_surge", "Дикий выброс", "Случайный мощный эффект — может быть как полезным так и вредным.", 9, 0, 0),
            new AbilityDefinition("sorcerer_dragon_hide", "Драконья чешуя", "Пассивка: +2 к броне всегда, независимо от снаряжения.", 1, 0, 0, true),
            new AbilityDefinition("sorcerer_elemental_affinity", "Стихийное родство", "Пассивка: заклинания своей стихии наносят +20% урона.", 7, 0, 0, true),
            new AbilityDefinition("bard_misty_step", "Туманный шаг", "Телепорт вперёд до 25 блоков, не сквозь стены.", 2, 0, 25),
            new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),
            new AbilityDefinition("warlock_poison_spray", "Ядовитые брызги", "Конус зелёных брызг до 5 блоков: отравление 10с и 5 физ. урона.", 3, 0, 40),
            new AbilityDefinition("bard_haste", "Скороход", "Скорость III всем в радиусе 5 блоков на 1.5 минуты.", 5, 0, 30),
            new AbilityDefinition("bard_dimension_door", "Переносящая дверь", "Телепортирует чародея и союзника рядом до 150 блоков вперёд или сквозь стены.", 7, 0, 140),
            new AbilityDefinition("bard_slow_zone", "Замедление", "Создаёт зону 4x4x4 с замедлением, слабостью и усталостью на 15с.", 6, 0, 130),
			new AbilityDefinition("bard_greater_invisibility", "Высшая невидимость", "Заклинатель и союзники рядом невидимы 25с, не снимается атакой.", 7, 0, 140),
			new AbilityDefinition("bard_heat_metal", "Раскалённый металл", "2-12 урона огнём цели в броне medium/heavy, поджигает.", 8, 0, 40)
        );
    }
}