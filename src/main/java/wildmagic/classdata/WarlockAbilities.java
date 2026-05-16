package wildmagic.classdata;

import java.util.List;

public final class WarlockAbilities {
	private WarlockAbilities() {
	}

	public static List<AbilityDefinition> create() {
		return List.of(
				new AbilityDefinition("warlock_mystic_charge", "Мистический заряд", "Красный луч без маны: 2-8 маг. урона, КД 3с. С 6 ур. слегка отталкивает, с 10 ур. урон 5-12 и КД 2с.", 1, 3, 0),
				new AbilityDefinition("warlock_armor_of_agathys", "Доспех Агатиса", "На 1 минуту даёт поглощение и один раз отвечает атакующему физ. уроном. На 1/6/10 ур.: 5/10/15 HP и урона, цена 70/90/110 маны.", 1, 0, 70),
				new AbilityDefinition("warlock_poison_spray", "Ядовитые брызги", "Конус зелёных брызг до 5 блоков: отравление 10с и 5 физ. урона всем задетым.", 1, 0, 40),
				new AbilityDefinition("warlock_undead_body", "Живой мертвец", "Пассивка: иммунитет к голоду, не нужен воздух и постоянное ночное зрение.", 1, 0, 0, true),
				new AbilityDefinition("warlock_pact_blade", "Договор клинка", "Призывает личный неразрушимый золотой меч с остротой и поджиганием. Мана 120.", 3, 0, 120),
				new AbilityDefinition("warlock_darkness", "Тьма", "Область 7x7x4 на 15с вокруг колдуна: тьма и слепота для всех, кроме кастующего и колдунов. Мана 80.", 3, 0, 80),
				new AbilityDefinition("warlock_undead_touch", "Касание нежити", "Пассивка: атака пустой рукой накладывает иссушение на 2с.", 5, 0, 0, true),
				new AbilityDefinition("warlock_vampiric_touch", "Прикосновение вампира", "Ближнее заклинание: 8 физ. урона и 8 HP лечения. Мана 50.", 7, 0, 50),
				new AbilityDefinition("warlock_counterspell", "Контрзаклинание", "Розовый луч до 30 блоков: обнуляет ману цели и завершает эффекты её заклинаний.", 7, 0, 90),
				new AbilityDefinition("warlock_circle_of_death", "Круг смерти", "Зелёный круг радиусом 10 на 5с: сушит растения, накладывает иссушение 3с и тьму 10с каждую секунду. Мана 160.", 9, 0, 160),
				new AbilityDefinition("warlock_create_undead", "Сотворение нежити", "Создаёт 2 зомби в железной броне с каменными топорами и 1 скелета, дружелюбных к колдуну. Мана 160.", 9, 0, 160),
				new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),
				new AbilityDefinition("bard_misty_step", "Туманный шаг", "Телепорт вперёд до 25 блоков, не сквозь стены.", 4, 0, 25),
				new AbilityDefinition("bard_silence", "Тишина", "Сфера радиусом 5 блоков: внутри нельзя использовать заклинания.", 4, 0, 120),
				new AbilityDefinition("bard_dispel", "Рассеивание магии", "Снимает все эффекты с цели в прицеле до 20 блоков.", 6, 0, 120),
				new AbilityDefinition("bard_heat_metal", "Раскалённый металл", "2-12 урона огнём цели в броне medium/heavy, поджигает.", 8, 0, 40),
				new AbilityDefinition("bard_shatter", "Дребезги", "Сфера радиусом 5 блоков: все внутри получают 4-14 физ. урона.", 8, 0, 50),
				new AbilityDefinition("bard_force_cage", "Силовая клетка", "Создаёт клетку вокруг цели на 25с. Внутри нельзя использовать магию.", 12, 300, 180)
		);
	}
}
