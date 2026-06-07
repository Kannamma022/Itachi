package com.example.data

data class AnimeCharacter(
    val name: String,
    val series: String,
    val emoji: String,
    val auraColor: Long, // Hex color for glowing animations
    val auraName: String, // Dynamic anime aura/power system name
    val quote: String, // Famous signature catchphrase / quote
    val soundFrequencyStart: Double, // Real-time synthesized aura-charge sound starting pitch
    val soundFrequencyEnd: Double, // Sound ending pitch
    val tag: String // Title/Category
) {
    companion object {
        val PRESETS = listOf(
            AnimeCharacter(
                name = "Satoru Gojo",
                series = "Jujutsu Kaisen",
                emoji = "🤞🌌",
                auraColor = 0xFF8B5CF6, // Purple
                auraName = "Limitless Infinity Void",
                quote = "Throughout heaven and earth, I alone am the honored one. 🤞",
                soundFrequencyStart = 350.0,
                soundFrequencyEnd = 1600.0,
                tag = "Special Grade Sorcerer"
            ),
            AnimeCharacter(
                name = "Naruto Uzumaki",
                series = "Naruto Shippuden",
                emoji = "🦊🍥",
                auraColor = 0xFFF97316, // Orange
                auraName = "Nine-Tails Sage Chakra Mode",
                quote = "I won't run away, I never go back on my word! That's my nindo! 🦊",
                soundFrequencyStart = 280.0,
                soundFrequencyEnd = 1100.0,
                tag = "Seventh Hokage"
            ),
            AnimeCharacter(
                name = "Monkey D. Luffy",
                series = "One Piece",
                emoji = "👒🍖",
                auraColor = 0xFFFFD700, // Gold / Yellow
                auraName = "Sun God Nika Gear 5",
                quote = "I'm gonna be the King of the Pirates! Shishishi! 👒",
                soundFrequencyStart = 450.0,
                soundFrequencyEnd = 1500.0,
                tag = "Liberation Warrior"
            ),
            AnimeCharacter(
                name = "Anya Forger",
                series = "Spy x Family",
                emoji = "🥜✨",
                auraColor = 0xFFEC4899, // Pink
                auraName = "Minds-eye Telepathy Spark",
                quote = "Anya likes peanuts! Waku waku! 🥜",
                soundFrequencyStart = 680.0,
                soundFrequencyEnd = 1900.0,
                tag = "Starlight Anya"
            ),
            AnimeCharacter(
                name = "Son Goku",
                series = "Dragon Ball Z",
                emoji = "☄️🐉",
                auraColor = 0xFF06B6D4, // cyan
                auraName = "Ultra Instinct Autonomous Mode",
                quote = "And this... is to go even further beyond! HAAAA! ☄️",
                soundFrequencyStart = 180.0,
                soundFrequencyEnd = 1450.0,
                tag = "Divine Saiyan"
            ),
            AnimeCharacter(
                name = "Tanjiro Kamado",
                series = "Demon Slayer",
                emoji = "🌊⚔️",
                auraColor = 0xFFEF4444, // Red
                auraName = "Hinokami Kagura / Sun Breath",
                quote = "No matter how many people you lose, you must carry on living. ⚔️",
                soundFrequencyStart = 310.0,
                soundFrequencyEnd = 980.0,
                tag = "Demon Slayer Corps"
            ),
            AnimeCharacter(
                name = "Nezuko Kamado",
                series = "Demon Slayer",
                emoji = "🎋🌸",
                auraColor = 0xFFF472B6, // Soft pink
                auraName = "Explosive Blood Demon Art",
                quote = "Mmh, mmh! (Protects humans with her life) 🎋",
                soundFrequencyStart = 500.0,
                soundFrequencyEnd = 1350.0,
                tag = "Sunprotected Demon"
            ),
            AnimeCharacter(
                name = "Roronoa Zoro",
                series = "One Piece",
                emoji = "⚔️🟢",
                auraColor = 0xFF10B981, // Emerald Green
                auraName = "Three-Sword Style: King of Hell",
                quote = "If I die here, then I'm a man who could only make it this far. ⚔️",
                soundFrequencyStart = 240.0,
                soundFrequencyEnd = 820.0,
                tag = "Master Swordsman"
            ),
            AnimeCharacter(
                name = "Ryomen Sukuna",
                series = "Jujutsu Kaisen",
                emoji = "💀🖤",
                auraColor = 0xFF991B1B, // Dark Crimson Purple
                auraName = "Malevolent Shrine Cleave",
                quote = "Stand proud. You are strong. But know your place, fool. 💀",
                soundFrequencyStart = 150.0,
                soundFrequencyEnd = 620.0,
                tag = "King of Curses"
            ),
            AnimeCharacter(
                name = "Sailor Moon",
                series = "Pretty Guardian",
                emoji = "🌙🎀",
                auraColor = 0xFFFF69B4, // Hot Pink
                auraName = "Silver Crystal Moon Power",
                quote = "In the name of the Moon, I will punish you! 🌙🎀",
                soundFrequencyStart = 600.0,
                soundFrequencyEnd = 1800.0,
                tag = "Moon Guardian"
            )
        )
    }
}
