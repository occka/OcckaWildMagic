package wildmagic.classdata;

import java.util.List;

public final class SorcererAbilities {
	private SorcererAbilities() {
	}

	public static List<AbilityDefinition> create() {
		return List.of(
				new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),
				new AbilityDefinition("bard_misty_step", "Туманный шаг", "Телепорт вперёд до 25 блоков, не сквозь стены.", 4, 0, 25),
				new AbilityDefinition("bard_slow_zone", "Замедление", "Создаёт зону 4x4x4 с замедлением, слабостью и усталостью на 15с.", 6, 0, 130),
				new AbilityDefinition("bard_dimension_door", "Переносящая дверь", "Телепортирует заклинателя и союзника рядом до 150 блоков вперёд или сквозь стены.", 7, 0, 140),
				new AbilityDefinition("bard_greater_invisibility", "Высшая невидимость", "Заклинатель и союзники рядом невидимы 25с, не снимается атакой.", 7, 0, 140),
				new AbilityDefinition("bard_haste", "Скороход", "Скорость III всем в радиусе 5 блоков на 1.5 минуты.", 8, 0, 30),
				new AbilityDefinition("bard_heat_metal", "Раскалённый металл", "2-12 урона огнём цели в броне medium/heavy, поджигает.", 8, 0, 40),
				new AbilityDefinition("bard_shatter", "Дребезги", "Сфера радиусом 5 блоков: все внутри получают 4-14 физ. урона.", 8, 0, 50)
		);
	}
}
