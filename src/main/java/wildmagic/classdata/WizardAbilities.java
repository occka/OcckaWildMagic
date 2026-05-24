package wildmagic.classdata;

import java.util.List;

public final class WizardAbilities {
	private WizardAbilities() {
	}

	public static List<AbilityDefinition> create() {
		return List.of(
				new AbilityDefinition("wizard_fire_bolt", "Огненный снаряд", "Маленький огненный снаряд: 1-10 урона, с 6 ур. 2-12, с 10 ур. 5-20.", 1, 0, 20),
				new AbilityDefinition("wizard_magic_missile", "Магическая стрела", "3 самонаводящихся снаряда в конусе 25 блоков: 2 магического урона и поджигание. С 5 ур. 4 стрелы, с 9 ур. 5 стрел.", 2, 0, 70),
				new AbilityDefinition("wizard_fireball", "Фаербол", "Огненный снаряд-взрыв: 4-22 урона огнём и поджигание области радиусом 4 блока.", 5, 0, 60),
				new AbilityDefinition("wizard_gravity_well", "Гравитационный колодец", "Зона радиусом 6 блоков в точке прицела на 5с: притягивает, каждую секунду наносит 2 магического урона и слабость I.", 5, 0, 80),
				new AbilityDefinition("wizard_chain_lightning", "Цепная молния", "Молния по цели в конусе 30 блоков: 14 урона первой цели, затем перескакивает на 4 ближайших врагов по 8 урона.", 7, 0, 110),
				new AbilityDefinition("wizard_meteor_shower", "Метеоритный дождь", "Зона радиусом 25 блоков в точке прицела. 15с падают 3-5 метеоритов: 15-30 огненного урона в радиусе 7 и поджигание.", 9, 0, 160),
				new AbilityDefinition("wizard_fragile_scholar", "Хрупкий учёный", "Пассивка: у волшебника всего 4 сердца, он не кастует в броне выше light, зато только он использует стол зачарований.", 1, 0, 0, true),
				new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),
				new AbilityDefinition("bard_invisibility", "Невидимость", "Заклинатель становится невидимым на 60с. Прерывается при атаке или касте.", 3, 0, 120),
				new AbilityDefinition("bard_misty_step", "Туманный шаг", "Телепорт вперёд до 25 блоков, не сквозь стены.", 4, 0, 25),
				new AbilityDefinition("bard_silence", "Тишина", "Сфера радиусом 5 блоков: внутри нельзя использовать заклинания.", 4, 0, 120),
				new AbilityDefinition("bard_dispel", "Рассеивание магии", "Снимает все эффекты с цели в прицеле до 20 блоков.", 6, 0, 120),
				new AbilityDefinition("bard_slow_zone", "Замедление", "Создаёт зону 4x4x4 с замедлением, слабостью и усталостью на 15с.", 6, 0, 130),
				new AbilityDefinition("bard_dimension_door", "Переносящая дверь", "Телепортирует заклинателя и союзника рядом до 150 блоков вперёд или сквозь стены.", 7, 0, 140),
				new AbilityDefinition("bard_greater_invisibility", "Высшая невидимость", "Заклинатель и союзники рядом невидимы 25с, не снимается атакой.", 7, 0, 140),
				new AbilityDefinition("bard_haste", "Скороход", "Скорость III всем в радиусе 5 блоков на 1.5 минуты.", 8, 0, 30),
				new AbilityDefinition("bard_feather_fall", "Падение пёрышком", "Плавное падение II всем в радиусе 5 блоков на минуту.", 4, 0, 30),
				new AbilityDefinition("bard_heat_metal", "Раскалённый металл", "2-12 урона огнём цели в броне medium/heavy, поджигает.", 8, 0, 40),
				new AbilityDefinition("bard_shatter", "Дребезги", "Сфера радиусом 5 блоков: все внутри получают 4-14 физ. урона.", 8, 0, 50),
				new AbilityDefinition("bard_force_cage", "Силовая клетка", "Создаёт клетку вокруг цели на 25с. Внутри нельзя использовать магию.", 12, 300, 180)
		);
	}
}
