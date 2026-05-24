package wildmagic.classdata;

import java.util.List;

public final class SorcererAbilities {
    private SorcererAbilities() {
    }

    public static List<AbilityDefinition> create() {
        return List.of(
                // Lv1
                new AbilityDefinition("sorcerer_dragon_breath", "Дыхание дракона",
                        "Конус стихийного урона своей стихии на 6 блоков. Огонь — поджигает, Лёд — замедляет, Молния — замедляет добычу, Яд — отравляет, Гром — отталкивает.",
                        1, 0, 60),
                new AbilityDefinition("wizard_fire_bolt", "Огненный снаряд",
                        "Маленький огненный снаряд: 1-10 урона, с 6 ур. 2-12, с 10 ур. 5-20.", 1, 0, 20),
                new AbilityDefinition("sorcerer_leap", "Прыжок", "Прыгучесть III на 45 секунд.", 1, 0, 60),
                new AbilityDefinition("sorcerer_dragon_hide", "Драконья чешуя",
                        "Пассивка: +2 к броне всегда, независимо от снаряжения.", 1, 0, 0, true),

                // Lv2
                new AbilityDefinition("sorcerer_elemental_dash", "Стихийный рывок",
                        "Рывок на 10 блоков вперёд. Дополнительный эффект зависит от выбранной стихии. 30 маны.", 2, 0, 30),
                new AbilityDefinition("sorcerer_ice_dagger", "Ледяной кинжал",
                        "Снаряд: 2-6 урона и замедление III на 4с при попадании. 25 маны.", 2, 0, 25),
                new AbilityDefinition("sorcerer_pseudo_life", "Псевдожизнь",
                        "2 полоски поглощения на 2 минуты. 100 маны.", 2, 0, 100),
                new AbilityDefinition("bard_misty_step", "Туманный шаг",
                        "Телепорт вперёд до 25 блоков, не сквозь стены.", 2, 0, 25),
                new AbilityDefinition("bard_sound_wave", "Звуковая волна",
                        "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),

                // Lv3
                new AbilityDefinition("sorcerer_repair", "Починка",
                        "Чинит броню на игроке и предмет в руках на 20 прочности. 120 маны.", 3, 0, 120),
                new AbilityDefinition("sorcerer_mage_armor", "Доспехи мага", "+2 брони на 30 секунд. 120 маны.", 3, 0,
                        120),

                // Lv4
                new AbilityDefinition("sorcerer_metamagic", "Метамагия", "Следующее заклинание не тратит ману.", 4, 0,
                        50),
                new AbilityDefinition("warlock_poison_spray", "Ядовитые брызги",
                        "Конус зелёных брызг до 5 блоков: отравление 10с и 5 физ. урона.", 3, 0, 40),
                new AbilityDefinition("sorcerer_silent_spell", "Молчаливое заклинание", "Следующее заклинание можно использовать в зоне тишины. 40 маны.", 4, 0, 40),

                // Lv5
                new AbilityDefinition("sorcerer_draconic_wings", "Драконьи крылья",
                        "Элитра-полёт в направлении взгляда 25с. Прекращается при касании земли. По истечении — плавное падение 15с.",
                        5, 0, 70),
                new AbilityDefinition("bard_haste", "Скороход", "Скорость III всем в радиусе 5 блоков на 1.5 минуты.",
                        5, 0, 30),
                new AbilityDefinition("sorcerer_elemental_affinity", "Стихийное родство",
                        "Пассивка: огонь — сопротивление огню; молния — ускорение добычи II; лёд — 1 ед урона ответкой; яд — иммунитет + яд при атаке; гром — взрыв при смерти.",
                        5, 0, 0, true),

                // Lv6
                new AbilityDefinition("sorcerer_storm_jump", "Грозовой прыжок",
                        "Взлёт на 25 блоков и управляемое падение. При приземлении 8-15 урона и эффект выбранной стихии. 55 маны.", 6, 0, 55),
                new AbilityDefinition("bard_slow_zone", "Замедление",
                        "Создаёт зону 4x4x4 с замедлением, слабостью и усталостью на 15с.", 6, 0, 130),
                new AbilityDefinition("bard_greater_invisibility", "Высшая невидимость",
                        "Заклинатель и союзники рядом невидимы 25с, не снимается атакой.", 6, 0, 140),

                // Lv7
                new AbilityDefinition("sorcerer_elemental_burst", "Стихийный взрыв",
                        "Мощный стихийный эффект в радиусе 10 блоков. Огонь — поджигает, Яд — слепота+замедление, Лёд — заморозка, Молния — 3 удара молнией, Гром — сильный откат.",
                        7, 0, 80),
                new AbilityDefinition("bard_dimension_door", "Переносящая дверь",
                        "Телепортирует чародея и союзника рядом до 150 блоков вперёд или сквозь стены.", 7, 0, 140),
                new AbilityDefinition("bard_heat_metal", "Раскалённый металл",
                        "2-12 урона огнём цели в броне medium/heavy, поджигает.", 7, 0, 40),

                // Lv9
                new AbilityDefinition("sorcerer_wild_surge", "Дикий выброс",
                        "Случайный мощный эффект — может быть как полезным так и вредным. КД 300с.", 9, 300, 0),

                // Lv10
                new AbilityDefinition("sorcerer_draconic_resilience", "Драконья стойкость",
                        "Пассивка: сопротивление I постоянно; регенерация в огне (огонь не наносит урон).", 10, 0, 0,
                        true));
    }
}
