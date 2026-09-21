package com.example.jetpackinvaders20

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jetpackinvaders20.ui.theme.JetpackInvaders20Theme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class AppScreen { COVER, HANGAR, GAME, GAME_OVER }
enum class PowerUpType(val label: String, val color: Color) {
    TRIPLE_SHOT("3X", Color(0xFF00E5FF)),
    SHIELD("DEF", Color(0xFFFFB703)),
    RAPID_FIRE("SPD", Color(0xFF16F40E)),
    HEAL("HP", Color(0xFFFF3366)),
    FREEZE("ICE", Color(0xFF00F5D4))
}
enum class BossType { MOTHERSHIP, DREADNOUGHT, LEVIATHAN, OVERLORD }

enum class ShipPerk {
    BALANCED, RAPID_ASSAULT, HEAVY_SHIELD, DOUBLE_DAMAGE, TIME_WARP
}

data class ShipSkin(
    val id: String,
    val name: String,
    val price: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val perk: ShipPerk,
    val perkTitle: String,
    val perkDescription: String
)

val availableSkins = listOf(
    ShipSkin("phoenix", "Phoenix-X", 0, Color(0xFF00E5FF), Color(0xFFFF5500), ShipPerk.BALANCED, "Célula Regenerativa", "Regenera +1 Vida a cada 600 pontos acumulados."),
    ShipSkin("spectre", "Void Spectre", 150, Color(0xFF9D4EDD), Color(0xFF00F5D4), ShipPerk.RAPID_ASSAULT, "Cadência Acelerada", "Dispara lasers com taxa hiper-rápida."),
    ShipSkin("titan", "Solar Titan", 320, Color(0xFFFFB703), Color(0xFFFF0055), ShipPerk.HEAVY_SHIELD, "Blindagem Cinética", "Inicia todos os setores protegido com escudo."),
    ShipSkin("nebula", "Dark Nebula", 550, Color(0xFF16F40E), Color(0xFFFFFFFF), ShipPerk.DOUBLE_DAMAGE, "Canhão Quântico", "Causa dano duplo (2x) por disparo."),
    ShipSkin("valkyrie", "Chrono Valkyrie", 800, Color(0xFFFF007F), Color(0xFF00E5FF), ShipPerk.TIME_WARP, "Fenda Temporal", "Reduz a velocidade de inimigos e tiros em 30%.")
)

class Bullet(var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = -24f, val isEnemy: Boolean = false, val damage: Int = 1)
class Enemy(
    var x: Float,
    var y: Float,
    val color: Color,
    val points: Int = 10,
    var swayOffset: Float = Random.nextFloat() * 6.28f,
    var isBoss: Boolean = false,
    var bossType: BossType = BossType.MOTHERSHIP,
    var hp: Int = 1,
    val maxHp: Int = 1,
    val era: Int = 0,
    val waveVariation: Int = 0
)
class PowerUp(var x: Float, var y: Float, val type: PowerUpType)
class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Float = 1f, val color: Color, val size: Float = 4f)
class Star(var x: Float, var y: Float, val speed: Float, val size: Float, val alpha: Float)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("jetpack_invaders_prefs", Context.MODE_PRIVATE)

        setContent {
            JetpackInvaders20Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var currentScreen by remember { mutableStateOf(AppScreen.COVER) }

                    var totalCoins by remember { mutableIntStateOf(prefs.getInt("saved_coins", 50)) }
                    /*
                    // MODO DEV (DESATIVADO TEMPORARIAMENTE)
                    var isDevModeActive by remember { mutableStateOf(prefs.getBoolean("dev_mode_active", false)) }
                    */
                    val isDevModeActive = false

                    val savedEquippedId = prefs.getString("equipped_skin", "phoenix") ?: "phoenix"
                    var currentSkin by remember { mutableStateOf(availableSkins.firstOrNull { it.id == savedEquippedId } ?: availableSkins[0]) }

                    val savedUnlockedSet = prefs.getStringSet("unlocked_skins", setOf("phoenix")) ?: setOf("phoenix")
                    val unlockedSkins = remember { mutableStateListOf<String>().apply { addAll(savedUnlockedSet) } }

                    var finalScore by remember { mutableIntStateOf(0) }
                    var finalWave by remember { mutableIntStateOf(1) }

                    fun persistData() {
                        prefs.edit()
                            .putInt("saved_coins", totalCoins)
                            /* .putBoolean("dev_mode_active", isDevModeActive) */
                            .putString("equipped_skin", currentSkin.id)
                            .putStringSet("unlocked_skins", unlockedSkins.toSet())
                            .apply()
                    }

                    /*
                    val onToggleDevMode = {
                        isDevModeActive = !isDevModeActive
                        if (isDevModeActive) {
                            totalCoins += 99999
                        }
                        persistData()
                    }
                    */

                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            AppScreen.COVER -> CoverScreen(
                                coins = totalCoins,
                                /* isDevMode = isDevModeActive, */
                                onStartClick = { currentScreen = AppScreen.GAME },
                                onHangarClick = { currentScreen = AppScreen.HANGAR }
                                /* onDevModeClick = onToggleDevMode */
                            )
                            AppScreen.HANGAR -> HangarScreen(
                                coins = totalCoins,
                                /* isDevMode = isDevModeActive, */
                                selectedSkin = currentSkin,
                                unlockedSkins = unlockedSkins,
                                onSelectSkin = { skin ->
                                    currentSkin = skin
                                    persistData()
                                },
                                onBuySkin = { skin ->
                                    if (totalCoins >= skin.price && !unlockedSkins.contains(skin.id)) {
                                        totalCoins -= skin.price
                                        unlockedSkins.add(skin.id)
                                        currentSkin = skin
                                        persistData()
                                    }
                                },
                                /* onDevModeClick = onToggleDevMode, */
                                onBack = { currentScreen = AppScreen.COVER }
                            )
                            AppScreen.GAME -> DynamicGamePlayScreen(
                                equippedSkin = currentSkin,
                                isDevGodMode = isDevModeActive,
                                onAddCoins = { gained ->
                                    totalCoins += gained
                                    persistData()
                                },
                                onGameOver = { score, wave ->
                                    finalScore = score
                                    finalWave = wave
                                    currentScreen = AppScreen.GAME_OVER
                                }
                            )
                            AppScreen.GAME_OVER -> GameOverScreen(
                                score = finalScore,
                                wave = finalWave,
                                onRestart = { currentScreen = AppScreen.GAME },
                                onBackToMenu = { currentScreen = AppScreen.COVER }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TELA DE ENTRADA
// -------------------------------------------------------------
@Composable
fun CoverScreen(
    coins: Int,
    /* isDevMode: Boolean, */
    onStartClick: () -> Unit,
    onHangarClick: () -> Unit
    /* onDevModeClick: () -> Unit */
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF020617), Color(0xFF0F172A), Color(0xFF030712))))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /*
                Button(
                    onClick = onDevModeClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDevMode) Color(0xFF16A34A) else Color(0xFFE11D48)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isDevMode) "🛠️ DEV: GOD MODE (∞)" else "🛠️ MODO DEV (+99k)",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
                */

                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(20.dp)) {
                    Text("🪙 $coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 14.sp)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("JETPACK", fontSize = 46.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF), letterSpacing = 8.sp, fontFamily = FontFamily.Monospace)
                Text("INVADERS", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF0055), letterSpacing = 6.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier.size(150.dp).background(Color(0xFF1E293B).copy(alpha = 0.5f), RoundedCornerShape(32.dp)).border(2.dp, Color(0xFF00E5FF).copy(alpha = 0.6f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👾", fontSize = 72.sp)
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStartClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(0.85f).height(54.dp)
                ) {
                    Text("INICIAR BATALHA", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF030712))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onHangarClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(0.85f).height(50.dp)
                ) {
                    Text("HANGAR DE NAVES 🚀", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TELA DO HANGAR
// -------------------------------------------------------------
@Composable
fun HangarScreen(
    coins: Int,
    /* isDevMode: Boolean, */
    selectedSkin: ShipSkin,
    unlockedSkins: List<String>,
    onSelectSkin: (ShipSkin) -> Unit,
    onBuySkin: (ShipSkin) -> Unit,
    /* onDevModeClick: () -> Unit, */
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF030712)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))) {
                Text("⬅ Voltar", color = Color.White)
            }

            /*
            Button(
                onClick = onDevModeClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDevMode) Color(0xFF16A34A) else Color(0xFFE11D48)
                )
            ) {
                Text(if (isDevMode) "GOD MODE ON" else "DEV MODE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            */

            Text("🪙 $coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("HANGAR DE NAVES", color = Color(0xFF00E5FF), fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text("Naves salvas no dispositivo", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            items(availableSkins) { skin ->
                val isUnlocked = unlockedSkins.contains(skin.id)
                val isEquipped = selectedSkin.id == skin.id

                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isEquipped) Color(0xFF1E293B) else Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().border(
                        width = if (isEquipped) 2.dp else 1.dp,
                        color = if (isEquipped) skin.primaryColor else Color.DarkGray,
                        shape = RoundedCornerShape(16.dp)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(skin.name, color = skin.primaryColor, fontWeight = FontWeight.Black, fontSize = 19.sp)

                            if (isUnlocked) {
                                Button(
                                    onClick = { onSelectSkin(skin) },
                                    enabled = !isEquipped,
                                    colors = ButtonDefaults.buttonColors(containerColor = skin.primaryColor)
                                ) {
                                    Text(if (isEquipped) "USANDO" else "EQUIPAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Button(
                                    onClick = { onBuySkin(skin) },
                                    enabled = coins >= skin.price,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                                ) {
                                    Text("${skin.price} 🪙 COMPRAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF030712).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("HABILIDADE: ${skin.perkTitle.uppercase()}", color = Color(0xFFFFB703), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(skin.perkDescription, color = Color.LightGray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GAMEPLAY COMPLETO
// -------------------------------------------------------------
@Composable
fun DynamicGamePlayScreen(
    equippedSkin: ShipSkin,
    isDevGodMode: Boolean,
    onAddCoins: (Int) -> Unit,
    onGameOver: (Int, Int) -> Unit
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Pré-calcula os layouts dos textos dos PowerUps UMA ÚNICA VEZ (Fim dos travamentos de texto!)
    val powerUpTextLayouts = remember(textMeasurer) {
        PowerUpType.values().associateWith { type ->
            textMeasurer.measure(
                text = type.label,
                style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }

        val shipWidth = with(density) { 54.dp.toPx() }
        val shipHeight = with(density) { 42.dp.toPx() }
        val enemySize = with(density) { 34.dp.toPx() }
        val shipY = screenHeight - 160f

        var score by remember { mutableIntStateOf(0) }
        var coinMilestone by remember { mutableIntStateOf(0) }
        var regenMilestone by remember { mutableIntStateOf(0) }
        var lives by remember { mutableIntStateOf(if (isDevGodMode) 999 else 3) }
        var currentWave by remember { mutableIntStateOf(1) }
        var shipX by remember { mutableFloatStateOf(screenWidth / 2f - shipWidth / 2f) }

        var hasShield by remember { mutableStateOf(equippedSkin.perk == ShipPerk.HEAVY_SHIELD) }
        var tripleShotTimeLeft by remember { mutableLongStateOf(0L) }
        var rapidFireTimeLeft by remember { mutableLongStateOf(0L) }
        var freezeTimeLeft by remember { mutableLongStateOf(0L) }
        var shakeIntensity by remember { mutableFloatStateOf(0f) }

        val bullets = remember { mutableListOf<Bullet>() }
        val enemies = remember { mutableListOf<Enemy>() }
        val powerUps = remember { mutableListOf<PowerUp>() }
        val particles = remember { mutableListOf<Particle>() }
        val stars = remember {
            List(35) {
                Star(
                    x = Random.nextFloat() * screenWidth,
                    y = Random.nextFloat() * screenHeight,
                    speed = Random.nextFloat() * 2.5f + 1f,
                    size = Random.nextFloat() * 2f + 1f,
                    alpha = Random.nextFloat() * 0.4f + 0.2f
                )
            }
        }

        var frameTick by remember { mutableLongStateOf(0L) }
        var enemyDir by remember { mutableFloatStateOf(1f) }

        val currentEra = (currentWave - 1) / 5
        val backgroundBrush = remember(currentEra) {
            when (currentEra % 4) {
                0 -> Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF0B192C), Color(0xFF020617)))
                1 -> Brush.verticalGradient(listOf(Color(0xFF220901), Color(0xFF3F0713), Color(0xFF0F0104)))
                2 -> Brush.verticalGradient(listOf(Color(0xFF021B14), Color(0xFF063025), Color(0xFF02120C)))
                else -> Brush.verticalGradient(listOf(Color(0xFF1E0738), Color(0xFF2E0854), Color(0xFF0D021A)))
            }
        }

        // Reuso de Path da nave para evitar GC pause
        val reusableShipPath = remember { Path() }

        fun spawnWave(wave: Int) {
            enemies.clear()
            bullets.clear()
            powerUps.clear()

            if (equippedSkin.perk == ShipPerk.HEAVY_SHIELD) {
                hasShield = true
            }

            val isBossWave = wave % 5 == 0

            if (isBossWave) {
                val bossType = when ((wave / 5) % 4) {
                    1 -> BossType.MOTHERSHIP
                    2 -> BossType.DREADNOUGHT
                    3 -> BossType.LEVIATHAN
                    else -> BossType.OVERLORD
                }
                val bossHp = 45 + (wave * 15)
                enemies.add(
                    Enemy(
                        x = screenWidth / 2f - 75f,
                        y = 90f,
                        color = when (bossType) {
                            BossType.MOTHERSHIP -> Color(0xFFFF0055)
                            BossType.DREADNOUGHT -> Color(0xFFFFB703)
                            BossType.LEVIATHAN -> Color(0xFF00F5D4)
                            BossType.OVERLORD -> Color(0xFFFF007F)
                        },
                        points = 1000 * (wave / 5),
                        isBoss = true,
                        bossType = bossType,
                        hp = bossHp,
                        maxHp = bossHp,
                        era = currentEra,
                        waveVariation = wave % 5
                    )
                )
            } else {
                val colorPalettes = when (currentEra % 4) {
                    0 -> listOf(Color(0xFF00E5FF), Color(0xFF4CC9F0), Color(0xFF4361EE))
                    1 -> listOf(Color(0xFFFF0055), Color(0xFFFF5400), Color(0xFFFFB703))
                    2 -> listOf(Color(0xFF16F40E), Color(0xFF00F5D4), Color(0xFF52B788))
                    else -> listOf(Color(0xFFB5179E), Color(0xFF7209B7), Color(0xFF9D4EDD))
                }

                val cols = 5
                val rows = 3 + (wave / 6).coerceAtMost(2)
                val spacing = enemySize + 18f
                val startX = (screenWidth - (cols * spacing)) / 2f

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        enemies.add(
                            Enemy(
                                x = startX + c * spacing,
                                y = 80f + r * spacing,
                                color = colorPalettes[(c + r) % colorPalettes.size],
                                points = 15 + wave * 5,
                                era = currentEra,
                                waveVariation = wave % 5
                            )
                        )
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            spawnWave(currentWave)
            var lastPlayerShotTime = 0L
            var lastEnemyShotTime = 0L

            while (isDevGodMode || lives > 0) {
                withFrameNanos { timeNow ->
                    if (shakeIntensity > 0f) shakeIntensity = (shakeIntensity - 0.7f).coerceAtLeast(0f)

                    if (score - coinMilestone >= 200) {
                        coinMilestone = score
                        onAddCoins(10)
                    }

                    if (!isDevGodMode && equippedSkin.perk == ShipPerk.BALANCED && score - regenMilestone >= 600) {
                        regenMilestone = score
                        lives = (lives + 1).coerceAtMost(5)
                    }

                    for (i in stars.indices) {
                        val s = stars[i]
                        s.y += s.speed
                        if (s.y > screenHeight) {
                            s.y = 0f
                            s.x = Random.nextFloat() * screenWidth
                        }
                    }

                    if (enemies.isEmpty()) {
                        currentWave++
                        spawnWave(currentWave)
                        return@withFrameNanos
                    }

                    val baseInterval = if (equippedSkin.perk == ShipPerk.RAPID_ASSAULT) 120_000_000L else 170_000_000L
                    val isTriple = timeNow < tripleShotTimeLeft
                    val isRapid = timeNow < rapidFireTimeLeft
                    val finalShotInterval = if (isRapid) (baseInterval * 0.55f).toLong() else baseInterval
                    val damagePerShot = if (equippedSkin.perk == ShipPerk.DOUBLE_DAMAGE) 2 else 1

                    if (timeNow - lastPlayerShotTime > finalShotInterval) {
                        val originX = shipX + shipWidth / 2f
                        if (isTriple) {
                            bullets.add(Bullet(originX, shipY - 10f, vx = 0f, vy = -26f, damage = damagePerShot))
                            bullets.add(Bullet(originX, shipY - 10f, vx = -7f, vy = -24f, damage = damagePerShot))
                            bullets.add(Bullet(originX, shipY - 10f, vx = 7f, vy = -24f, damage = damagePerShot))
                        } else {
                            bullets.add(Bullet(originX - 3f, shipY - 10f, vx = 0f, vy = -26f, damage = damagePerShot))
                        }
                        lastPlayerShotTime = timeNow
                    }

                    val timeScale = if (equippedSkin.perk == ShipPerk.TIME_WARP) 0.70f else 1.0f
                    val isFrozen = timeNow < freezeTimeLeft

                    val enemyInterval = (1_100_000_000L - (currentWave * 50_000_000L)).coerceAtLeast(340_000_000L)
                    if (!isFrozen && timeNow - lastEnemyShotTime > (enemyInterval / timeScale).toLong() && enemies.isNotEmpty()) {
                        val boss = enemies.firstOrNull { it.isBoss }
                        if (boss != null) {
                            when (boss.bossType) {
                                BossType.MOTHERSHIP -> {
                                    bullets.add(Bullet(boss.x + 30f, boss.y + 70f, vx = -4f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                    bullets.add(Bullet(boss.x + 75f, boss.y + 80f, vx = 0f, vy = 13f * timeScale, isEnemy = true))
                                    bullets.add(Bullet(boss.x + 120f, boss.y + 70f, vx = 4f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                }
                                BossType.DREADNOUGHT -> {
                                    for (angle in 0 until 6) {
                                        val rad = (angle * 60f) * (Math.PI.toFloat() / 180f)
                                        bullets.add(Bullet(boss.x + 75f, boss.y + 40f, vx = (cos(rad) * 6.5f) * timeScale, vy = (sin(rad).coerceAtLeast(0.3f) * 8.5f) * timeScale, isEnemy = true))
                                    }
                                }
                                BossType.LEVIATHAN -> {
                                    bullets.add(Bullet(boss.x + 45f, boss.y + 80f, vx = -1.5f * timeScale, vy = 15f * timeScale, isEnemy = true))
                                    bullets.add(Bullet(boss.x + 105f, boss.y + 80f, vx = 1.5f * timeScale, vy = 15f * timeScale, isEnemy = true))
                                }
                                BossType.OVERLORD -> {
                                    val sway = sin(boss.swayOffset) * 6f
                                    bullets.add(Bullet(boss.x + 75f, boss.y + 80f, vx = sway * timeScale, vy = 12f * timeScale, isEnemy = true))
                                    bullets.add(Bullet(boss.x + 25f, boss.y + 70f, vx = -5f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                    bullets.add(Bullet(boss.x + 125f, boss.y + 70f, vx = 5f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                }
                            }
                        } else {
                            val shooter = enemies.random()
                            bullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 0f, vy = (11f + currentWave * 0.35f) * timeScale, isEnemy = true))
                        }
                        lastEnemyShotTime = timeNow
                    }

                    // Move Bullets
                    val bIter = bullets.iterator()
                    while (bIter.hasNext()) {
                        val b = bIter.next()
                        b.x += b.vx
                        b.y += b.vy
                        if (b.y < -30f || b.y > screenHeight + 30f || b.x < 0f || b.x > screenWidth) {
                            bIter.remove()
                        }
                    }

                    // Move Power-Ups
                    val pIter = powerUps.iterator()
                    while (pIter.hasNext()) {
                        val p = pIter.next()
                        p.y += 4f
                        val shipRect = Rect(shipX, shipY, shipX + shipWidth, shipY + shipHeight)
                        if (shipRect.contains(Offset(p.x, p.y))) {
                            when (p.type) {
                                PowerUpType.TRIPLE_SHOT -> tripleShotTimeLeft = timeNow + 8_000_000_000L
                                PowerUpType.SHIELD -> hasShield = true
                                PowerUpType.RAPID_FIRE -> rapidFireTimeLeft = timeNow + 7_000_000_000L
                                PowerUpType.HEAL -> if (!isDevGodMode) lives = (lives + 1).coerceAtMost(5)
                                PowerUpType.FREEZE -> freezeTimeLeft = timeNow + 4_500_000_000L
                            }
                            pIter.remove()
                        } else if (p.y > screenHeight) {
                            pIter.remove()
                        }
                    }

                    // Move Enemies
                    if (!isFrozen) {
                        val isBossPresent = enemies.any { it.isBoss }
                        if (isBossPresent) {
                            val boss = enemies.first { it.isBoss }
                            boss.x += ((3.0f + currentWave * 0.15f) * timeScale) * enemyDir
                            boss.y = 90f + sin(boss.swayOffset) * 22f
                            boss.swayOffset += 0.045f * timeScale
                            if (boss.x <= 15f || boss.x >= screenWidth - 165f) enemyDir *= -1f
                        } else {
                            var wallHit = false
                            val speedX = (2.4f + currentWave * 0.3f) * timeScale

                            for (i in enemies.indices) {
                                val e1 = enemies[i]
                                e1.x += speedX * enemyDir
                                e1.swayOffset += 0.03f * timeScale
                                e1.y += (0.18f + sin(e1.swayOffset) * 0.45f) * timeScale

                                if (e1.y >= shipY + shipHeight) e1.y = 70f
                                if (e1.x <= 12f || e1.x >= screenWidth - enemySize - 12f) wallHit = true

                                for (j in i + 1 until enemies.size) {
                                    val e2 = enemies[j]
                                    val dx = e2.x - e1.x
                                    val dy = e2.y - e1.y
                                    val dist = sqrt(dx * dx + dy * dy)
                                    val minDistance = enemySize + 8f

                                    if (dist < minDistance && dist > 0f) {
                                        val overlap = (minDistance - dist) * 0.5f
                                        val nx = dx / dist
                                        val ny = dy / dist
                                        e1.x -= nx * overlap
                                        e1.y -= ny * overlap
                                        e2.x += nx * overlap
                                        e2.y += ny * overlap
                                    }
                                }
                            }

                            if (wallHit) {
                                enemyDir *= -1f
                                for (i in enemies.indices) enemies[i].y += 18f
                            }
                        }
                    }

                    // Colisão com Nave
                    val shipRect = Rect(shipX, shipY, shipX + shipWidth, shipY + shipHeight)
                    val enemyIter = enemies.iterator()
                    while (enemyIter.hasNext()) {
                        val e = enemyIter.next()
                        val w = if (e.isBoss) 150f else enemySize
                        val h = if (e.isBoss) 85f else enemySize
                        if (shipRect.overlaps(Rect(e.x, e.y, e.x + w, e.y + h))) {
                            shakeIntensity = 14f
                            if (!isDevGodMode) {
                                if (hasShield) hasShield = false else lives--
                            }
                            if (!e.isBoss) enemyIter.remove()

                            if (particles.size < 25) {
                                repeat(8) {
                                    particles.add(Particle(shipX + shipWidth / 2f, shipY + shipHeight / 2f, (Random.nextFloat() - 0.5f) * 10f, (Random.nextFloat() - 0.5f) * 10f, color = Color(0xFFFF0055)))
                                }
                            }
                            if (!isDevGodMode && lives <= 0) {
                                onGameOver(score, currentWave)
                                return@withFrameNanos
                            }
                            break
                        }
                    }

                    // Projéteis Inimigos na Nave
                    val bCheckIter = bullets.iterator()
                    while (bCheckIter.hasNext()) {
                        val b = bCheckIter.next()
                        if (b.isEnemy && shipRect.contains(Offset(b.x, b.y))) {
                            bCheckIter.remove()
                            shakeIntensity = 9f
                            if (!isDevGodMode) {
                                if (hasShield) hasShield = false else lives--
                            }
                            if (particles.size < 25) {
                                repeat(6) { particles.add(Particle(b.x, b.y, (Random.nextFloat() - 0.5f) * 8f, (Random.nextFloat() - 0.5f) * 8f, color = Color(0xFFFF5555))) }
                            }
                            if (!isDevGodMode && lives <= 0) {
                                onGameOver(score, currentWave)
                                return@withFrameNanos
                            }
                            break
                        }
                    }

                    // Tiros do Jogador em Inimigos
                    val playerBulletsIter = bullets.iterator()
                    while (playerBulletsIter.hasNext()) {
                        val b = playerBulletsIter.next()
                        if (b.isEnemy) continue

                        var bulletHit = false
                        val targetsIter = enemies.iterator()
                        while (targetsIter.hasNext()) {
                            val e = targetsIter.next()
                            val w = if (e.isBoss) 150f else enemySize
                            val h = if (e.isBoss) 85f else enemySize

                            if (Rect(e.x, e.y, e.x + w, e.y + h).contains(Offset(b.x, b.y))) {
                                bulletHit = true
                                e.hp -= b.damage

                                if (e.hp <= 0) {
                                    targetsIter.remove()
                                    score += e.points

                                    if (particles.size < 30) {
                                        repeat(if (e.isBoss) 16 else 5) {
                                            particles.add(Particle(e.x + w / 2f, e.y + h / 2f, (Random.nextFloat() - 0.5f) * 9f, (Random.nextFloat() - 0.5f) * 9f, color = e.color, size = if (e.isBoss) 5f else 3f))
                                        }
                                    }

                                    val canDrop = if (e.isBoss) true else (Random.nextFloat() < 0.08f && powerUps.isEmpty())
                                    if (canDrop) {
                                        powerUps.add(PowerUp(e.x + w / 2f, e.y + h / 2f, PowerUpType.values().random()))
                                    }
                                }
                                break
                            }
                        }

                        if (bulletHit) playerBulletsIter.remove()
                    }

                    // Partículas
                    val partIter = particles.iterator()
                    while (partIter.hasNext()) {
                        val p = partIter.next()
                        p.x += p.vx
                        p.y += p.vy
                        p.alpha -= 0.05f
                        if (p.alpha <= 0f) partIter.remove()
                    }

                    frameTick++
                }
            }
            if (!isDevGodMode) {
                onGameOver(score, currentWave)
            }
        }

        val offX = if (shakeIntensity > 0f) (Random.nextFloat() - 0.5f) * shakeIntensity * 1.5f else 0f
        val offY = if (shakeIntensity > 0f) (Random.nextFloat() - 0.5f) * shakeIntensity * 1.5f else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .offset { IntOffset(offX.toInt(), offY.toInt()) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        shipX = (down.position.x - shipWidth / 2f).coerceIn(10f, screenWidth - shipWidth - 10f)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    shipX = (change.position.x - shipWidth / 2f).coerceIn(10f, screenWidth - shipWidth - 10f)
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _obs = frameTick

                for (i in stars.indices) {
                    val s = stars[i]
                    drawCircle(Color.White.copy(alpha = s.alpha), radius = s.size, center = Offset(s.x, s.y))
                }

                // Power-Ups usando layouts pré-medidos (Zero lag!)
                for (i in powerUps.indices) {
                    val pu = powerUps[i]
                    drawCircle(pu.type.color.copy(alpha = 0.35f), radius = 18f, center = Offset(pu.x, pu.y))
                    drawCircle(pu.type.color, radius = 14f, center = Offset(pu.x, pu.y))
                    drawCircle(Color.Black.copy(alpha = 0.45f), radius = 10f, center = Offset(pu.x, pu.y))

                    powerUpTextLayouts[pu.type]?.let { layout ->
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(pu.x - layout.size.width / 2f, pu.y - layout.size.height / 2f)
                        )
                    }
                }

                // Inimigos com formas nativas diretas
                for (i in enemies.indices) {
                    val e = enemies[i]
                    if (e.isBoss) {
                        val pulse = (sin(frameTick * 0.1f) * 3f).toFloat()
                        drawRoundRect(
                            color = e.color,
                            topLeft = Offset(e.x, e.y),
                            size = Size(150f, 75f),
                            cornerRadius = CornerRadius(16f, 16f)
                        )
                        drawCircle(Color.White, radius = 16f + pulse, center = Offset(e.x + 75f, e.y + 38f))
                        drawCircle(e.color, radius = 10f + pulse, center = Offset(e.x + 75f, e.y + 38f))
                    } else {
                        when (e.era % 4) {
                            0 -> {
                                drawRoundRect(e.color, topLeft = Offset(e.x, e.y + 4f), size = Size(enemySize, enemySize * 0.72f), cornerRadius = CornerRadius(6f, 6f))
                                drawCircle(Color.Black, enemySize * 0.14f, Offset(e.x + enemySize * 0.3f, e.y + enemySize * 0.35f))
                                drawCircle(Color.Black, enemySize * 0.14f, Offset(e.x + enemySize * 0.7f, e.y + enemySize * 0.35f))
                            }
                            1 -> {
                                drawRoundRect(e.color, topLeft = Offset(e.x, e.y), size = Size(enemySize, enemySize * 0.85f), cornerRadius = CornerRadius(14f, 4f))
                                drawCircle(Color.White, 3f, Offset(e.x + enemySize / 2f, e.y + enemySize * 0.45f))
                            }
                            2 -> {
                                drawCircle(e.color, radius = enemySize * 0.45f, center = Offset(e.x + enemySize / 2f, e.y + enemySize / 2f))
                                drawCircle(Color.Black, radius = enemySize * 0.2f, center = Offset(e.x + enemySize / 2f, e.y + enemySize / 2f))
                            }
                            else -> {
                                drawRect(e.color, topLeft = Offset(e.x + 4f, e.y + 4f), size = Size(enemySize - 8f, enemySize - 8f))
                                drawLine(Color.White, Offset(e.x + 6f, e.y + enemySize / 2f), Offset(e.x + enemySize - 6f, e.y + enemySize / 2f), strokeWidth = 2.5f)
                            }
                        }
                    }
                }

                // Tiros
                for (i in bullets.indices) {
                    val b = bullets[i]
                    val color = if (b.isEnemy) Color(0xFFFF3366) else equippedSkin.primaryColor
                    val bulletThickness = if (b.damage > 1) 8f else 5f
                    drawRoundRect(color, topLeft = Offset(b.x - bulletThickness / 2f, b.y), size = Size(bulletThickness, 18f), cornerRadius = CornerRadius(3f, 3f))
                }

                // Partículas
                for (i in particles.indices) {
                    val p = particles[i]
                    drawCircle(p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)), radius = p.size * p.alpha, center = Offset(p.x, p.y))
                }

                // Propulsor
                val flameHeight = 8f + (frameTick % 6) * 2f
                drawRoundRect(
                    color = equippedSkin.secondaryColor,
                    topLeft = Offset(shipX + shipWidth * 0.38f, shipY + shipHeight * 0.82f),
                    size = Size(shipWidth * 0.24f, flameHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )

                // Desenho da Nave (Reaproveitando o Path sem criar instâncias na memória)
                reusableShipPath.reset()
                reusableShipPath.moveTo(shipX + shipWidth / 2f, shipY)
                reusableShipPath.lineTo(shipX + shipWidth, shipY + shipHeight)
                reusableShipPath.lineTo(shipX + shipWidth * 0.75f, shipY + shipHeight * 0.8f)
                reusableShipPath.lineTo(shipX + shipWidth * 0.25f, shipY + shipHeight * 0.8f)
                reusableShipPath.lineTo(shipX, shipY + shipHeight)
                reusableShipPath.close()

                drawPath(reusableShipPath, color = equippedSkin.primaryColor)

                // Cockpit central
                drawCircle(Color(0xFF030712), radius = 6f, center = Offset(shipX + shipWidth / 2f, shipY + shipHeight * 0.45f))
                drawCircle(Color.White, radius = 2.5f, center = Offset(shipX + shipWidth / 2f, shipY + shipHeight * 0.45f))

                if (hasShield) {
                    drawCircle(Color(0xFFFFB703).copy(alpha = 0.4f), radius = shipWidth * 0.85f, center = Offset(shipX + shipWidth / 2f, shipY + shipHeight / 2f), style = Stroke(width = 3.5f))
                }
            }

            // HUD Superior
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)), shape = RoundedCornerShape(10.dp)) {
                        Text("SCORE: $score", color = Color(0xFF00E5FF), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)), shape = RoundedCornerShape(10.dp)) {
                        Text(if (currentWave % 5 == 0) "⚠️ BOSS WAVE" else "SETOR $currentWave (ERA ${currentEra + 1})", color = if (currentWave % 5 == 0) Color(0xFFFF0055) else Color(0xFFFFB703), fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.85f)), shape = RoundedCornerShape(10.dp)) {
                        Text(
                            text = if (isDevGodMode) "⚡ GOD MODE (∞)" else "❤️ ".repeat(lives),
                            color = if (isDevGodMode) Color(0xFF00F5D4) else Color.White,
                            fontWeight = if (isDevGodMode) FontWeight.Black else FontWeight.Normal,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                val currentBoss = enemies.firstOrNull { it.isBoss }
                if (currentWave % 5 == 0 && currentBoss != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF020617).copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "BOSS: ${currentBoss.bossType.name}",
                                    color = currentBoss.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${currentBoss.hp}/${currentBoss.maxHp} HP",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (currentBoss.hp.toFloat() / currentBoss.maxHp).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = currentBoss.color,
                                trackColor = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TELA DE GAME OVER
// -------------------------------------------------------------
@Composable
fun GameOverScreen(score: Int, wave: Int, onRestart: () -> Unit, onBackToMenu: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF020617)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("MISSÃO ABORTADA", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF0055), letterSpacing = 4.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(0.85f)) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCORE FINAL", fontSize = 13.sp, color = Color.Gray)
                    Text("$score", fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("SETOR ALCANÇADO", fontSize = 13.sp, color = Color.Gray)
                    Text("SETOR $wave", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB703))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5D4)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.85f).height(52.dp)) {
                Text("REINICIAR", fontWeight = FontWeight.Black, color = Color(0xFF030712))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onBackToMenu, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.85f).height(52.dp)) {
                Text("MENU PRINCIPAL", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}