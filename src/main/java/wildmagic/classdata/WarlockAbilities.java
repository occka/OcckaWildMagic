package wildmagic.classdata;

import java.util.List;

public final class WarlockAbilities {
	private WarlockAbilities() {
	}

	public static List<AbilityDefinition> create() {
		return List.of(
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
