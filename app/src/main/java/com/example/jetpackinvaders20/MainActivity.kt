package com.example.jetpackinvaders20

import android.content.Context
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.jetpackinvaders20.ui.theme.JetpackInvaders20Theme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// -------------------------------------------------------------
// MOTOR DE ÁUDIO NATIVO (SEM LATÊNCIA)
// -------------------------------------------------------------
class ArcadeAudioEngine(private val isSoundEnabled: () -> Boolean) {
    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 16)
    } catch (_: Exception) { null }

    private var lastLaserToneTime = 0L

    fun playLaser() {
        if (!isSoundEnabled()) return
        val now = System.currentTimeMillis()
        if (now - lastLaserToneTime < 75L) return
        lastLaserToneTime = now
        try {
            toneGen?.stopTone()
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 20)
        } catch (_: Exception) {}
    }

    fun playExplosion() {
        if (!isSoundEnabled()) return
        try {
            toneGen?.stopTone()
            toneGen?.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 50)
        } catch (_: Exception) {}
    }

    fun playPowerUp() {
        if (!isSoundEnabled()) return
        try {
            toneGen?.stopTone()
            toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, 60)
        } catch (_: Exception) {}
    }

    fun playBossAlarm() {
        if (!isSoundEnabled()) return
        try {
            toneGen?.stopTone()
            toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 100)
        } catch (_: Exception) {}
    }

    fun playBomb() {
        if (!isSoundEnabled()) return
        try {
            toneGen?.stopTone()
            toneGen?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 80)
        } catch (_: Exception) {}
    }

    fun release() {
        try { toneGen?.release() } catch (_: Exception) {}
    }
}

enum class AppScreen { COVER, HANGAR, GAME, GAME_OVER }

enum class PowerUpType(val drawableRes: Int) {
    DAMAGE_2X(R.drawable.power_dmg),
    SHIELD(R.drawable.power_def),
    RAPID_FIRE(R.drawable.power_spd),
    HEAL(R.drawable.power_hp),
    FREEZE(R.drawable.power_ice)
}

enum class BossType(val displayName: String) {
    MOTHERSHIP("Nave-Mãe"),
    DREADNOUGHT("Orbital"),
    SERPENTOID("Serpentoid"),
    OMEGA("Ômega Supremo")
}

enum class ShipPerk { BALANCED, RAPID_ASSAULT, HEAVY_SHIELD, DOUBLE_DAMAGE, TIME_WARP }

enum class EnemyKind {
    NORMAL_INVADER,
    BEETLE_SHOOTER,
    ERA_FIRE_GLAIVE,
    ERA_FIRE_VESPA,
    ERA_FIRE_ARIETE,
    ERA_BIO_PARASITA,
    ERA_BIO_INSECTOID,
    ERA_BIO_CASULO,
    // Era 3: O Vazio
    ERA_VOID_PRISMA,
    ERA_VOID_TESSERACT,
    ERA_VOID_ENIGMA
}

data class ShipSkin(
    val id: String,
    val name: String,
    val price: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val drawableRes: Int,
    val previewRotation: Float = 90f,
    val gameRotation: Float = 0f,
    val perk: ShipPerk,
    val perkTag: String,
    val perkTitle: String,
    val perkDescription: String
)

val availableSkins = listOf(
    ShipSkin("phoenix", "Phoenix-X", 0, Color(0xFF00E5FF), Color(0xFFFF5500), R.drawable.phoenix, 90f, 0f, ShipPerk.BALANCED, "REGEN", "Regeneração", "+1 Vida a cada 600 pts"),
    ShipSkin("spectre", "Void Spectre", 150, Color(0xFF9D4EDD), Color(0xFF00F5D4), R.drawable.spectre, 90f, 0f, ShipPerk.RAPID_ASSAULT, "SPEED", "Hiper Cadência", "Taxa de tiro ultra rápida"),
    ShipSkin("titan", "Solar Titan", 320, Color(0xFFFFB703), Color(0xFFFF0055), R.drawable.titan, -90f, 180f, ShipPerk.HEAVY_SHIELD, "SHIELD", "Escudo Cinético", "Inicia setores com proteção"),
    ShipSkin("nebula", "Dark Nebula", 550, Color(0xFF16F40E), Color(0xFFFFFFFF), R.drawable.nebula, 90f, 0f, ShipPerk.DOUBLE_DAMAGE, "DAMAGE", "Canhão Quântico", "Dano duplo permanente (2x)"),
    ShipSkin("valkyrie", "Chrono Valkyrie", 800, Color(0xFFFF007F), Color(0xFF00E5FF), R.drawable.valkyrie, 90f, 0f, ShipPerk.TIME_WARP, "CHRONO", "Fenda Temporal", "Inimigos 30% mais lentos")
)

class Bullet(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = -24f,
    val isEnemy: Boolean = false,
    val damage: Int = 1,
    val isParalyzing: Boolean = false
)

class Enemy(
    var x: Float,
    var y: Float,
    val color: Color = Color.White,
    val points: Int = 10,
    var swayOffset: Float = Random.nextFloat() * 6.28f,
    var swaySpeed: Float = Random.nextFloat() * 0.04f + 0.02f,
    var swayAmplitude: Float = Random.nextFloat() * 1.6f + 1.1f,
    var isBoss: Boolean = false,
    var bossType: BossType = BossType.MOTHERSHIP,
    var kind: EnemyKind = EnemyKind.NORMAL_INVADER,
    var hp: Int = 1,
    val maxHp: Int = 1,
    val era: Int = 0,
    var isDiving: Boolean = false,
    var diveVx: Float = 0f,
    var hasShield: Boolean = false,
    var isPhasedOut: Boolean = false // Propriedade adicionada para resolver o erro
)

class RusherEnemy(
    var x: Float,
    var y: Float,
    var hp: Int = 2,
    var vx: Float = 0f,
    var vy: Float = 8.5f,
    var swayOffset: Float = Random.nextFloat() * 6.28f
)

class PowerUp(var x: Float, var y: Float, val type: PowerUpType)
class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Float = 1f, val color: Color, val size: Float = 4f)
class Star(var x: Float, var y: Float, val speed: Float, val size: Float, val alpha: Float)

class MainActivity : ComponentActivity() {
    private lateinit var audioEngine: ArcadeAudioEngine
    private var soundEnabledState = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("jetpack_invaders_prefs", Context.MODE_PRIVATE)
        soundEnabledState.value = prefs.getBoolean("sound_enabled", true)
        audioEngine = ArcadeAudioEngine { soundEnabledState.value }

        setContent {
            JetpackInvaders20Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var currentScreen by remember { mutableStateOf(AppScreen.COVER) }

                    var totalCoins by remember { mutableIntStateOf(prefs.getInt("saved_coins", 50)) }
                    var highScore by remember { mutableIntStateOf(prefs.getInt("high_score", 0)) }
                    var isDevModeActive by remember { mutableStateOf(prefs.getBoolean("dev_mode_active", false)) }
                    var soundEnabled by soundEnabledState
                    var devSelectedWave by remember { mutableIntStateOf(1) }

                    val savedEquippedId = prefs.getString("equipped_skin", "phoenix") ?: "phoenix"
                    var currentSkin by remember { mutableStateOf(availableSkins.firstOrNull { it.id == savedEquippedId } ?: availableSkins[0]) }

                    val savedUnlockedSet = prefs.getStringSet("unlocked_skins", setOf("phoenix")) ?: setOf("phoenix")
                    val unlockedSkins = remember { mutableStateListOf<String>().apply { addAll(savedUnlockedSet) } }

                    var finalScore by remember { mutableIntStateOf(0) }
                    var finalWave by remember { mutableIntStateOf(1) }

                    fun persistData() {
                        prefs.edit()
                            .putInt("saved_coins", totalCoins)
                            .putInt("high_score", highScore)
                            .putBoolean("dev_mode_active", isDevModeActive)
                            .putBoolean("sound_enabled", soundEnabled)
                            .putString("equipped_skin", currentSkin.id)
                            .putStringSet("unlocked_skins", unlockedSkins.toSet())
                            .apply()
                    }

                    val onToggleDevMode = {
                        isDevModeActive = !isDevModeActive
                        if (isDevModeActive) {
                            totalCoins += 99999
                        }
                        persistData()
                    }

                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            AppScreen.COVER -> CoverScreen(
                                coins = totalCoins,
                                highScore = highScore,
                                isDevMode = isDevModeActive,
                                soundEnabled = soundEnabled,
                                selectedWave = devSelectedWave,
                                onSelectWave = { devSelectedWave = it },
                                onToggleSound = {
                                    soundEnabled = !soundEnabled
                                    persistData()
                                },
                                onDevModeClick = onToggleDevMode,
                                onStartClick = { currentScreen = AppScreen.GAME },
                                onHangarClick = { currentScreen = AppScreen.HANGAR }
                            )
                            AppScreen.HANGAR -> HangarScreen(
                                coins = totalCoins,
                                isDevMode = isDevModeActive,
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
                                        audioEngine.playPowerUp()
                                        persistData()
                                    }
                                },
                                onDevModeClick = onToggleDevMode,
                                onBack = { currentScreen = AppScreen.COVER }
                            )
                            AppScreen.GAME -> DynamicGamePlayScreen(
                                equippedSkin = currentSkin,
                                isDevGodMode = isDevModeActive,
                                startWave = devSelectedWave,
                                audioEngine = audioEngine,
                                soundEnabled = soundEnabled,
                                onToggleSound = {
                                    soundEnabled = !soundEnabled
                                    persistData()
                                },
                                onAddCoins = { gained ->
                                    totalCoins += gained
                                    persistData()
                                },
                                onGameOver = { score, wave ->
                                    finalScore = score
                                    finalWave = wave
                                    if (score > highScore) {
                                        highScore = score
                                    }
                                    audioEngine.playExplosion()
                                    persistData()
                                    currentScreen = AppScreen.GAME_OVER
                                }
                            )
                            AppScreen.GAME_OVER -> GameOverScreen(
                                score = finalScore,
                                highScore = highScore,
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

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
    }
}

// -------------------------------------------------------------
// TELA DE ENTRADA
// -------------------------------------------------------------
@Composable
fun CoverScreen(
    coins: Int,
    highScore: Int,
    isDevMode: Boolean,
    soundEnabled: Boolean,
    selectedWave: Int,
    onSelectWave: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onDevModeClick: () -> Unit,
    onStartClick: () -> Unit,
    onHangarClick: () -> Unit
) {
    val context = LocalContext.current

    val valkyrieBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.valkyrie).asImageBitmap() }
    val spectreBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.spectre).asImageBitmap() }
    val phoenixBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.phoenix).asImageBitmap() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090024))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 54.dp)
                        .background(Color(0xFF040018))
                        .border(1.5.dp, Color(0xFF3B2D6B), RoundedCornerShape(2.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                onToggleSound()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AUDIO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(if (soundEnabled) "🔊" else "🔇", fontSize = 14.sp)
                    }
                }

                Box(
                    modifier = Modifier.size(24.dp).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onDevModeClick()
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 54.dp)
                        .background(Color(0xFF040018))
                        .border(1.5.dp, Color(0xFF3B2D6B), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MOEDAS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$coins", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                Text("JETPACK", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 6.sp, fontFamily = FontFamily.Monospace)
                Text("INVADERS", fontSize = 36.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 5.sp, fontFamily = FontFamily.Monospace)

                Spacer(modifier = Modifier.height(28.dp))

                Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        rotate(degrees = 90f, pivot = Offset(centerX - 95f, centerY - 15f)) {
                            drawImage(image = spectreBitmap, dstOffset = IntOffset((centerX - 135f).toInt(), (centerY - 55f).toInt()), dstSize = IntSize(80.dp.toPx().toInt(), 80.dp.toPx().toInt()), filterQuality = FilterQuality.None)
                        }

                        rotate(degrees = 90f, pivot = Offset(centerX + 95f, centerY - 15f)) {
                            drawImage(image = phoenixBitmap, dstOffset = IntOffset((centerX + 55f).toInt(), (centerY - 55f).toInt()), dstSize = IntSize(80.dp.toPx().toInt(), 80.dp.toPx().toInt()), filterQuality = FilterQuality.None)
                        }

                        rotate(degrees = 90f, pivot = Offset(centerX, centerY)) {
                            drawImage(image = valkyrieBitmap, dstOffset = IntOffset((centerX - 60.dp.toPx()).toInt(), (centerY - 60.dp.toPx()).toInt()), dstSize = IntSize(120.dp.toPx().toInt(), 120.dp.toPx().toInt()), filterQuality = FilterQuality.None)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("TOP RECORD: $highScore", color = Color(0xFFFFD700), fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)

                if (isDevMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .background(Color(0xFF0F002E).copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFF6D28D9), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("[TESTE DEV - SELETOR DE FASES]", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))

                        val shortcuts = listOf(
                            1 to "E0 (S1)",
                            6 to "E1 (S6)",
                            11 to "E2 (S11)",
                            16 to "E3 (S16)",
                            20 to "ÔMEGA (S20)"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            shortcuts.forEach { (w, label) ->
                                Button(
                                    onClick = { onSelectWave(w) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedWave == w) Color(0xFF00E5FF) else Color(0xFF1E1B4B)
                                    ),
                                    shape = RoundedCornerShape(2.dp),
                                    modifier = Modifier.weight(1f).height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(label, color = if (selectedWave == w) Color.Black else Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(54.dp)
                        .background(brush = Brush.verticalGradient(listOf(Color(0xFF3B2D6B).copy(alpha = 0.55f), Color(0xFF1E143D).copy(alpha = 0.85f))), shape = RoundedCornerShape(27.dp))
                        .border(1.5.dp, Color(0xFF8B7AB8).copy(alpha = 0.6f), RoundedCornerShape(27.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                onStartClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("INICIAR MISSÃO", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(54.dp)
                        .background(brush = Brush.verticalGradient(listOf(Color(0xFF3B2D6B).copy(alpha = 0.55f), Color(0xFF1E143D).copy(alpha = 0.85f))), shape = RoundedCornerShape(27.dp))
                        .border(1.5.dp, Color(0xFF8B7AB8).copy(alpha = 0.6f), RoundedCornerShape(27.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                onHangarClick()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SKINS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TELA DO HANGAR (SKINS)
// -------------------------------------------------------------
@Composable
fun HangarScreen(
    coins: Int,
    isDevMode: Boolean,
    selectedSkin: ShipSkin,
    unlockedSkins: List<String>,
    onSelectSkin: (ShipSkin) -> Unit,
    onBuySkin: (ShipSkin) -> Unit,
    onDevModeClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val shipBitmaps = remember {
        availableSkins.associate { skin ->
            skin.id to BitmapFactory.decodeResource(context.resources, skin.drawableRes).asImageBitmap()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF030712)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(2.dp)) {
                Text("< VOLTAR", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }

            Button(onClick = onDevModeClick, colors = ButtonDefaults.buttonColors(containerColor = if (isDevMode) Color(0xFF15803D) else Color(0xFF991B1B)), shape = RoundedCornerShape(2.dp)) {
                Text(if (isDevMode) "GOD ON" else "DEV OFF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }

            Box(modifier = Modifier.border(1.dp, Color(0xFFFFB703), RoundedCornerShape(2.dp)).background(Color(0xFF0F172A)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("CR: $coins", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("SKINS", color = Color(0xFF00E5FF), fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 4.sp)
        Text("SELECIONE SEU CAÇA DE COMBATE", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(availableSkins) { skin ->
                val isUnlocked = unlockedSkins.contains(skin.id)
                val isEquipped = selectedSkin.id == skin.id

                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF080D1A)).border(width = if (isEquipped) 2.dp else 1.dp, color = if (isEquipped) skin.primaryColor else Color(0xFF1E293B), shape = RoundedCornerShape(2.dp)).padding(12.dp)) {
                    Column {
                        Text(skin.name.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth().height(85.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(modifier = Modifier.weight(1f).height(85.dp), contentAlignment = Alignment.CenterStart) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val bitmap = shipBitmaps[skin.id]
                                    if (bitmap != null) {
                                        rotate(degrees = skin.previewRotation, pivot = center) {
                                            val targetPx = 70.dp.toPx()
                                            drawImage(image = bitmap, dstOffset = IntOffset((center.x - targetPx / 2f).toInt(), (center.y - targetPx / 2f).toInt()), dstSize = IntSize(targetPx.toInt(), targetPx.toInt()), filterQuality = FilterQuality.None)
                                        }
                                    }
                                }
                            }

                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                                Text(skin.perkTitle.uppercase(), color = skin.primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text(skin.perkDescription, color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.size(38.dp).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF334155), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                                    Text(
                                        when (skin.perk) {
                                            ShipPerk.BALANCED -> "❤️+"
                                            ShipPerk.RAPID_ASSAULT -> "⚡"
                                            ShipPerk.HEAVY_SHIELD -> "🛡️"
                                            ShipPerk.DOUBLE_DAMAGE -> "💥"
                                            ShipPerk.TIME_WARP -> "⏳"
                                        },
                                        fontSize = 16.sp
                                    )
                                }
                                Text("[${skin.perkTag}]", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isUnlocked) {
                            Button(onClick = { onSelectSkin(skin) }, enabled = !isEquipped, modifier = Modifier.fillMaxWidth().height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (isEquipped) Color(0xFF1E293B) else skin.primaryColor), shape = RoundedCornerShape(2.dp)) {
                                Text(if (isEquipped) "EQUIPADO" else "EQUIPAR", color = if (isEquipped) Color.Gray else Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        } else {
                            Button(onClick = { onBuySkin(skin) }, enabled = coins >= skin.price, modifier = Modifier.fillMaxWidth().height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)), shape = RoundedCornerShape(2.dp)) {
                                Text("${skin.price} CR // DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// GAMEPLAY COMPLETO (BASE COMPROVADA E TOTALMENTE ESTABILIZADA)
// -------------------------------------------------------------
@Composable
fun DynamicGamePlayScreen(
    equippedSkin: ShipSkin,
    isDevGodMode: Boolean,
    startWave: Int,
    audioEngine: ArcadeAudioEngine,
    soundEnabled: Boolean,
    onToggleSound: () -> Unit,
    onAddCoins: (Int) -> Unit,
    onGameOver: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val shipBitmaps = remember { availableSkins.associate { it.id to BitmapFactory.decodeResource(context.resources, it.drawableRes).asImageBitmap() } }
    val powerUpBitmaps = remember { PowerUpType.values().associateWith { BitmapFactory.decodeResource(context.resources, it.drawableRes).asImageBitmap() } }

    val rusherBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.crab_rusher).asImageBitmap() }
    val normalInvaderBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.invasor).asImageBitmap() }
    val beetleShooterBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.alien_era0).asImageBitmap() }
    val bossMothershipBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.spaceship_mae).asImageBitmap() }

    val glaiveBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.glaive).asImageBitmap() }
    val vespaBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.vespa_atirador).asImageBitmap() }
    val arieteBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.ariete).asImageBitmap() }
    val orbitalBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.orbital).asImageBitmap() }

    val parasitaBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.parasita).asImageBitmap() }
    val insectoidBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.insectoid).asImageBitmap() }
    val casuloBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.casulo).asImageBitmap() }
    val serpentoidBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.serpentoid).asImageBitmap() }

    // Sprites Era 3 (Nomes estritos originais)
    val vazioBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.vazio).asImageBitmap() }
    val tesseractBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.tesseract).asImageBitmap() }
    val enigmaBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.enigma_bom).asImageBitmap() }
    val omegaBitmap = remember { BitmapFactory.decodeResource(context.resources, R.drawable.omega).asImageBitmap() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }
        val lateralPadding = with(density) { 28.dp.toPx() }

        val shipWidth = with(density) { 60.dp.toPx() }
        val shipHeight = with(density) { 60.dp.toPx() }
        val enemySize = with(density) { 38.dp.toPx() }
        val powerUpSize = with(density) { 32.dp.toPx() }
        val rusherSize = with(density) { 48.dp.toPx() }

        val bossWidth = with(density) { 150.dp.toPx() }
        val bossHeight = with(density) { 110.dp.toPx() }

        val shipBottomPadding = with(density) { 48.dp.toPx() }
        val shipY = screenHeight - 160f - shipBottomPadding
        val bossTopY = with(density) { 155.dp.toPx() }

        var score by remember { mutableIntStateOf(0) }
        var coinMilestone by remember { mutableIntStateOf(0) }
        var regenMilestone by remember { mutableIntStateOf(0) }
        var lives by remember { mutableIntStateOf(if (isDevGodMode) 999 else 3) }
        var currentWave by remember { mutableIntStateOf(startWave) }
        var shipX by remember { mutableFloatStateOf(screenWidth / 2f - shipWidth / 2f) }

        var isTouchActive by remember { mutableStateOf(false) }
        var paralyzeTimeLeft by remember { mutableLongStateOf(0L) }
        var pinchWallWidth by remember { mutableFloatStateOf(0f) }

        var bossCurrentHp by remember { mutableIntStateOf(1) }
        var bossMaxHp by remember { mutableIntStateOf(1) }

        val animatedBossHpProgress by animateFloatAsState(
            targetValue = (bossCurrentHp.toFloat() / bossMaxHp.coerceAtLeast(1)).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 100),
            label = "bossHpAnim"
        )

        var hasShield by remember { mutableStateOf(equippedSkin.perk == ShipPerk.HEAVY_SHIELD) }
        var damageBoostTimeLeft by remember { mutableLongStateOf(0L) }
        var rapidFireTimeLeft by remember { mutableLongStateOf(0L) }
        var freezeTimeLeft by remember { mutableLongStateOf(0L) }
        var shakeIntensity by remember { mutableFloatStateOf(0f) }

        var bombReadyTime by remember { mutableLongStateOf(0L) }
        var lastTapTime by remember { mutableLongStateOf(0L) }
        var bossHitFlashTime by remember { mutableLongStateOf(0L) }

        val activeRushers = remember { mutableStateListOf<RusherEnemy>() }
        var nextRusherSpawnTime by remember { mutableLongStateOf(0L) }
        var nextGlaiveDiveTime by remember { mutableLongStateOf(0L) }
        var nextSerpentMinionTime by remember { mutableLongStateOf(0L) }
        var nextAcidWallTime by remember { mutableLongStateOf(0L) }
        var nextOmegaRingTime by remember { mutableLongStateOf(0L) }

        val bullets = remember { mutableListOf<Bullet>() }
        val enemies = remember { mutableListOf<Enemy>() }
        val powerUps = remember { mutableListOf<PowerUp>() }
        val particles = remember { mutableListOf<Particle>() }
        val stars = remember {
            List(30) {
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
        val isFireEra = (currentEra % 4) == 1
        val isBioEra = (currentEra % 4) == 2
        val isVoidEra = (currentEra % 4) == 3

        val backgroundBrush = remember(currentEra) {
            when (currentEra % 4) {
                0 -> Brush.verticalGradient(listOf(Color(0xFF030712), Color(0xFF0B192C), Color(0xFF020617)))
                1 -> Brush.verticalGradient(listOf(Color(0xFF1F0D00), Color(0xFF3D1600), Color(0xFF0F0500)))
                2 -> Brush.verticalGradient(listOf(Color(0xFF021B14), Color(0xFF053828), Color(0xFF01140E)))
                else -> Brush.verticalGradient(listOf(Color(0xFF140226), Color(0xFF2E0854), Color(0xFF0B0118)))
            }
        }

        fun triggerBomb(timeNow: Long) {
            if (timeNow < bombReadyTime) return
            bombReadyTime = timeNow + 12_000_000_000L
            shakeIntensity = 12f
            paralyzeTimeLeft = 0L
            audioEngine.playBomb()

            bullets.removeAll { it.isEnemy }
            activeRushers.clear()

            repeat(20) {
                particles.add(
                    Particle(
                        x = shipX + shipWidth / 2f,
                        y = shipY + shipHeight / 2f,
                        vx = (Random.nextFloat() - 0.5f) * 14f,
                        vy = (Random.nextFloat() - 0.5f) * 14f,
                        color = Color(0xFFD946EF)
                    )
                )
            }
        }

        fun spawnWave(wave: Int) {
            enemies.clear()
            bullets.clear()
            powerUps.clear()
            activeRushers.clear()
            pinchWallWidth = 0f

            if (equippedSkin.perk == ShipPerk.HEAVY_SHIELD) {
                hasShield = true
            }

            val isBossWave = wave % 5 == 0
            val waveEra = (wave - 1) / 5
            val waveIsFire = (waveEra % 4) == 1
            val waveIsBio = (waveEra % 4) == 2
            val waveIsVoid = (waveEra % 4) == 3

            if (isBossWave) {
                audioEngine.playBossAlarm()
                val bossType = when ((wave / 5) % 4) {
                    1 -> BossType.MOTHERSHIP
                    2 -> BossType.DREADNOUGHT
                    3 -> BossType.SERPENTOID
                    else -> BossType.OMEGA
                }
                val bossHp = when (bossType) {
                    BossType.MOTHERSHIP -> 45 + (wave * 12)
                    BossType.DREADNOUGHT -> 60 + (wave * 8)
                    BossType.SERPENTOID -> 75 + (wave * 10)
                    else -> 100 + (wave * 15)
                }

                bossCurrentHp = bossHp
                bossMaxHp = bossHp

                enemies.add(
                    Enemy(
                        x = screenWidth / 2f - bossWidth / 2f,
                        y = bossTopY,
                        color = when (bossType) {
                            BossType.MOTHERSHIP -> Color(0xFFFF0055)
                            BossType.DREADNOUGHT -> Color(0xFFFFB703)
                            BossType.SERPENTOID -> Color(0xFF10B981)
                            BossType.OMEGA -> Color(0xFFD946EF)
                        },
                        points = 2000 * (wave / 5),
                        isBoss = true,
                        bossType = bossType,
                        hp = bossHp,
                        maxHp = bossHp,
                        era = waveEra
                    )
                )
            } else {
                val cols = 5
                val rows = 3 + (wave / 6).coerceAtMost(2)
                val spacing = enemySize + 16f
                val startX = (screenWidth - (cols * spacing)) / 2f
                val waveStartY = with(density) { 100.dp.toPx() }

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val kind = when {
                            waveIsVoid -> when (r) {
                                0 -> EnemyKind.ERA_VOID_PRISMA
                                1 -> EnemyKind.ERA_VOID_TESSERACT
                                else -> EnemyKind.ERA_VOID_ENIGMA
                            }
                            waveIsBio -> when (r) {
                                0 -> EnemyKind.ERA_BIO_INSECTOID
                                1 -> EnemyKind.ERA_BIO_CASULO
                                else -> EnemyKind.ERA_BIO_PARASITA
                            }
                            waveIsFire -> when (r) {
                                0 -> EnemyKind.ERA_FIRE_VESPA
                                1 -> EnemyKind.ERA_FIRE_ARIETE
                                else -> EnemyKind.ERA_FIRE_GLAIVE
                            }
                            else -> if (r == 0) EnemyKind.BEETLE_SHOOTER else EnemyKind.NORMAL_INVADER
                        }

                        val enemyHp = when (kind) {
                            EnemyKind.ERA_VOID_TESSERACT -> 4
                            EnemyKind.ERA_BIO_CASULO -> 3
                            EnemyKind.ERA_FIRE_ARIETE, EnemyKind.BEETLE_SHOOTER -> 2
                            else -> 1
                        }

                        enemies.add(
                            Enemy(
                                x = startX + c * spacing,
                                y = waveStartY + r * spacing,
                                points = 35 + wave * 5,
                                kind = kind,
                                hp = enemyHp,
                                maxHp = enemyHp,
                                era = waveEra,
                                hasShield = (kind == EnemyKind.ERA_FIRE_ARIETE)
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
            nextRusherSpawnTime = System.nanoTime() + 9_000_000_000L
            nextGlaiveDiveTime = System.nanoTime() + 10_000_000_000L
            nextSerpentMinionTime = System.nanoTime() + 6_000_000_000L
            nextAcidWallTime = System.nanoTime() + 8_000_000_000L
            nextOmegaRingTime = System.nanoTime() + 6_000_000_000L

            while (isDevGodMode || lives > 0) {
                withFrameNanos { timeNow ->
                    if (shakeIntensity > 0f) shakeIntensity = (shakeIntensity - 0.7f).coerceAtLeast(0f)

                    if (score - coinMilestone >= 200) {
                        coinMilestone = score
                        onAddCoins(10)
                        audioEngine.playPowerUp()
                    }

                    if (!isDevGodMode && equippedSkin.perk == ShipPerk.BALANCED && score - regenMilestone >= 600) {
                        regenMilestone = score
                        lives = (lives + 1).coerceAtMost(5)
                        audioEngine.playPowerUp()
                    }

                    for (i in stars.indices) {
                        val s = stars[i]
                        s.y += s.speed
                        if (s.y > screenHeight) {
                            s.y = 0f
                            s.x = Random.nextFloat() * screenWidth
                        }
                    }

                    if (enemies.isEmpty() && activeRushers.isEmpty()) {
                        currentWave++
                        spawnWave(currentWave)
                        return@withFrameNanos
                    }

                    val isFrozen = timeNow < freezeTimeLeft
                    val timeScale = if (equippedSkin.perk == ShipPerk.TIME_WARP) 0.70f else 1.0f

                    val pendingBullets = mutableListOf<Bullet>()
                    val pendingEnemies = mutableListOf<Enemy>()

                    val isBossWave = currentWave % 5 == 0
                    val isEra0 = currentWave <= 5

                    // 1. Enigma Intangibilidade
                    if (isVoidEra && !isFrozen) {
                        for (i in enemies.indices) {
                            val e = enemies[i]
                            if (e.kind == EnemyKind.ERA_VOID_ENIGMA) {
                                e.isPhasedOut = (sin((frameTick + e.swayOffset * 10f) * 0.045f) > 0.35f)
                            }
                        }
                    }

                    // 2. Boss Ômega (<50% HP Barreira Anular + Pinça)
                    val omegaBoss = enemies.firstOrNull { it.isBoss && it.bossType == BossType.OMEGA }
                    if (omegaBoss != null && !isFrozen) {
                        val isBelowHalf = omegaBoss.hp < (omegaBoss.maxHp / 2)
                        if (isBelowHalf) {
                            val maxPinch = screenWidth * 0.18f
                            pinchWallWidth = (pinchWallWidth + 0.25f * timeScale).coerceAtMost(maxPinch)

                            if (timeNow >= nextOmegaRingTime) {
                                val totalSlots = 8
                                val safeSlot = Random.nextInt(1, totalSlots - 1)
                                val slotW = (screenWidth - lateralPadding * 2) / totalSlots

                                for (col in 0 until totalSlots) {
                                    if (col == safeSlot) continue
                                    val bx = lateralPadding + (col * slotW) + (slotW / 2f)
                                    pendingBullets.add(Bullet(x = bx, y = omegaBoss.y + bossHeight, vx = 0f, vy = 8f * timeScale, isEnemy = true))
                                }
                                shakeIntensity = 10f
                                audioEngine.playExplosion()
                                nextOmegaRingTime = timeNow + 5_000_000_000L
                            }
                        }
                    }

                    // 3. Boss Serpentoid (Parasitas mergulhadores)
                    val serpentBoss = enemies.firstOrNull { it.isBoss && it.bossType == BossType.SERPENTOID }
                    if (serpentBoss != null && !isFrozen) {
                        if (timeNow >= nextSerpentMinionTime) {
                            pendingEnemies.add(Enemy(x = serpentBoss.x + 20f, y = serpentBoss.y + bossHeight * 0.7f, kind = EnemyKind.ERA_BIO_PARASITA, hp = 1, maxHp = 1, isDiving = true, diveVx = -5f))
                            pendingEnemies.add(Enemy(x = serpentBoss.x + bossWidth - 50f, y = serpentBoss.y + bossHeight * 0.7f, kind = EnemyKind.ERA_BIO_PARASITA, hp = 1, maxHp = 1, isDiving = true, diveVx = 5f))
                            audioEngine.playPowerUp()
                            nextSerpentMinionTime = timeNow + 6_500_000_000L
                        }

                        val isBelowHalfHp = serpentBoss.hp < (serpentBoss.maxHp / 2)
                        if (isBelowHalfHp && timeNow >= nextAcidWallTime) {
                            val totalSlots = 8
                            val safeHoleIndex = Random.nextInt(1, totalSlots - 1)
                            val slotWidth = (screenWidth - lateralPadding * 2) / totalSlots

                            for (col in 0 until totalSlots) {
                                if (col == safeHoleIndex) continue
                                val bulletX = lateralPadding + (col * slotWidth) + (slotWidth / 2f)
                                pendingBullets.add(Bullet(x = bulletX, y = serpentBoss.y + bossHeight, vx = 0f, vy = 7.5f * timeScale, isEnemy = true))
                            }
                            shakeIntensity = 7f
                            audioEngine.playExplosion()
                            nextAcidWallTime = timeNow + 5_500_000_000L
                        }
                    }

                    // Krab Rusher apenas na Era 0
                    if (isEra0 && !isBossWave && activeRushers.isEmpty() && timeNow >= nextRusherSpawnTime) {
                        val leftX = lateralPadding
                        val rightX = screenWidth - rusherSize - lateralPadding

                        activeRushers.add(RusherEnemy(leftX, -rusherSize, hp = 2, vx = Random.nextFloat() * 2.2f + 1.2f, vy = Random.nextFloat() * 2.5f + 8.0f))
                        activeRushers.add(RusherEnemy(rightX, -rusherSize - 110f, hp = 2, vx = -(Random.nextFloat() * 2.2f + 1.2f), vy = Random.nextFloat() * 2.5f + 8.5f))
                        nextRusherSpawnTime = timeNow + Random.nextLong(12_000_000_000L, 17_000_000_000L)
                    }

                    if (!isFrozen && activeRushers.isNotEmpty()) {
                        val rIter = activeRushers.iterator()
                        while (rIter.hasNext()) {
                            val rusher = rIter.next()
                            rusher.swayOffset += 0.08f * timeScale
                            rusher.y += rusher.vy * timeScale
                            rusher.x += (rusher.vx + sin(rusher.swayOffset) * 2.6f) * timeScale

                            if (rusher.x <= lateralPadding) {
                                rusher.x = lateralPadding
                                rusher.vx = kotlin.math.abs(rusher.vx)
                            } else if (rusher.x >= screenWidth - rusherSize - lateralPadding) {
                                rusher.x = screenWidth - rusherSize - lateralPadding
                                rusher.vx = -kotlin.math.abs(rusher.vx)
                            }

                            val shipRect = Rect(shipX, shipY, shipX + shipWidth, shipY + shipHeight)
                            val rusherRect = Rect(rusher.x, rusher.y, rusher.x + rusherSize, rusher.y + rusherSize)

                            if (shipRect.overlaps(rusherRect)) {
                                shakeIntensity = 14f
                                audioEngine.playExplosion()
                                if (!isDevGodMode) {
                                    if (hasShield) hasShield = false else lives--
                                }
                                repeat(10) {
                                    particles.add(Particle(rusher.x + rusherSize / 2f, rusher.y + rusherSize / 2f, (Random.nextFloat() - 0.5f) * 10f, (Random.nextFloat() - 0.5f) * 10f, color = Color(0xFF00F5D4)))
                                }
                                rIter.remove()
                                if (!isDevGodMode && lives <= 0) {
                                    onGameOver(score, currentWave)
                                    return@withFrameNanos
                                }
                            } else if (rusher.y > screenHeight) {
                                rIter.remove()
                            }
                        }
                    }

                    // Habilidade da Glaive: Mergulho Kamikaze (Era 1)
                    if (isFireEra && !isBossWave && timeNow >= nextGlaiveDiveTime) {
                        val availableGlaive = enemies.firstOrNull { it.kind == EnemyKind.ERA_FIRE_GLAIVE && !it.isDiving }
                        if (availableGlaive != null) {
                            availableGlaive.isDiving = true
                            availableGlaive.diveVx = if (Random.nextBoolean()) 3.5f else -3.5f
                        }
                        nextGlaiveDiveTime = timeNow + Random.nextLong(10_000_000_000L, 15_000_000_000L)
                    }

                    // Tiro do Jogador
                    val baseInterval = if (equippedSkin.perk == ShipPerk.RAPID_ASSAULT) 120_000_000L else 170_000_000L
                    val isRapid = timeNow < rapidFireTimeLeft
                    val finalShotInterval = if (isRapid) (baseInterval * 0.55f).toLong() else baseInterval
                    val baseDamage = if (equippedSkin.perk == ShipPerk.DOUBLE_DAMAGE) 2 else 1
                    val finalDamage = if (timeNow < damageBoostTimeLeft) baseDamage * 2 else baseDamage

                    if (isTouchActive && timeNow - lastPlayerShotTime > finalShotInterval) {
                        val originX = shipX + shipWidth / 2f
                        pendingBullets.add(Bullet(originX - 4f, shipY - 10f, vx = 0f, vy = -26f, damage = finalDamage))
                        audioEngine.playLaser()
                        lastPlayerShotTime = timeNow
                    }

                    // Cadência Inimiga
                    val baseEnemyInterval = if (isVoidEra) 520_000_000L else 600_000_000L
                    val potentialShooters = enemies.filter { it.isBoss || it.kind != EnemyKind.ERA_BIO_PARASITA }

                    if (!isFrozen && timeNow - lastEnemyShotTime > (baseEnemyInterval / timeScale).toLong() && potentialShooters.isNotEmpty()) {
                        val boss = potentialShooters.firstOrNull { it.isBoss }
                        if (boss != null) {
                            when (boss.bossType) {
                                BossType.MOTHERSHIP -> {
                                    pendingBullets.add(Bullet(boss.x + 35f, boss.y + bossHeight * 0.82f, vx = -3.5f * timeScale, vy = 10f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(boss.x + bossWidth / 2f, boss.y + bossHeight * 0.90f, vx = 0f * timeScale, vy = 11.5f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(boss.x + bossWidth - 35f, boss.y + bossHeight * 0.82f, vx = 3.5f * timeScale, vy = 10f * timeScale, isEnemy = true))
                                }
                                BossType.DREADNOUGHT -> {
                                    for (angle in 0 until 4) {
                                        val rad = (angle * 90f + (frameTick * 2f)) * (Math.PI.toFloat() / 180f)
                                        pendingBullets.add(Bullet(boss.x + bossWidth / 2f, boss.y + bossHeight / 2f, vx = (cos(rad) * 4.5f) * timeScale, vy = (sin(rad).coerceAtLeast(0.25f) * 6.5f) * timeScale, isEnemy = true))
                                    }
                                }
                                BossType.SERPENTOID -> {
                                    val sway = sin(boss.swayOffset * 2f) * 4f
                                    pendingBullets.add(Bullet(boss.x + bossWidth / 2f, boss.y + bossHeight * 0.85f, vx = sway * timeScale, vy = 13.5f * timeScale, isEnemy = true, isParalyzing = true))
                                }
                                BossType.OMEGA -> {
                                    val originX = boss.x + bossWidth / 2f
                                    val originY = boss.y + bossHeight * 0.88f
                                    val targetCenterX = shipX + shipWidth / 2f
                                    val aimDx = ((targetCenterX - originX) * 0.018f).coerceIn(-3.5f, 3.5f)
                                    val waveSweep = sin(boss.swayOffset * 1.8f) * 1.6f

                                    for (i in -2..2) {
                                        val spread = i * (2.8f + Random.nextFloat() * 0.8f)
                                        val jitterVx = (Random.nextFloat() - 0.5f) * 1.4f
                                        val bulletVx = (spread + aimDx + waveSweep + jitterVx) * timeScale

                                        val baseVy = 13.5f - kotlin.math.abs(i) * 0.9f
                                        val jitterVy = (Random.nextFloat() - 0.5f) * 1.2f
                                        val bulletVy = (baseVy + jitterVy) * timeScale

                                        pendingBullets.add(Bullet(originX, originY, vx = bulletVx, vy = bulletVy, isEnemy = true))
                                    }
                                }
                            }
                        } else {
                            val shooter = potentialShooters.random()
                            when (shooter.kind) {
                                EnemyKind.ERA_VOID_PRISMA -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = -1.8f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 1.8f * timeScale, vy = 11f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_VOID_ENIGMA -> {
                                    pendingBullets.add(Bullet(shooter.x + 8f, shooter.y + enemySize, vx = 0f, vy = 12f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(shooter.x + enemySize - 8f, shooter.y + enemySize, vx = 0f, vy = 12f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_VOID_TESSERACT -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 0f, vy = 13.5f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_BIO_INSECTOID -> {
                                    val dx = (shipX + shipWidth / 2f) - (shooter.x + enemySize / 2f)
                                    val aimVx = (dx * 0.025f).coerceIn(-3.5f, 3.5f)
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = aimVx * timeScale, vy = 11.5f * timeScale, isEnemy = true, isParalyzing = true))
                                }
                                EnemyKind.ERA_BIO_CASULO -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 0f, vy = 14f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_FIRE_GLAIVE -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = -1.8f * timeScale, vy = 9.5f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 1.8f * timeScale, vy = 9.5f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_FIRE_VESPA -> {
                                    pendingBullets.add(Bullet(shooter.x + 8f, shooter.y + enemySize, vx = 0f, vy = 10.5f * timeScale, isEnemy = true))
                                    pendingBullets.add(Bullet(shooter.x + enemySize - 8f, shooter.y + enemySize, vx = 0f, vy = 10.5f * timeScale, isEnemy = true))
                                }
                                EnemyKind.ERA_FIRE_ARIETE -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 0f, vy = 12f * timeScale, isEnemy = true))
                                }
                                else -> {
                                    pendingBullets.add(Bullet(shooter.x + enemySize / 2f, shooter.y + enemySize, vx = 0f, vy = 11f * timeScale, isEnemy = true))
                                }
                            }
                        }
                        lastEnemyShotTime = timeNow
                    }

                    val bIter = bullets.iterator()
                    while (bIter.hasNext()) {
                        val b = bIter.next()
                        b.x += b.vx
                        b.y += b.vy
                        if (b.y < -30f || b.y > screenHeight + 30f || b.x < 0f || b.x > screenWidth) {
                            bIter.remove()
                        }
                    }

                    val pIter = powerUps.iterator()
                    while (pIter.hasNext()) {
                        val p = pIter.next()
                        p.y += 4f
                        val shipRect = Rect(shipX, shipY, shipX + shipWidth, shipY + shipHeight)
                        if (shipRect.contains(Offset(p.x, p.y))) {
                            when (p.type) {
                                PowerUpType.DAMAGE_2X -> damageBoostTimeLeft = timeNow + 8_000_000_000L
                                PowerUpType.SHIELD -> hasShield = true
                                PowerUpType.RAPID_FIRE -> rapidFireTimeLeft = timeNow + 7_000_000_000L
                                PowerUpType.HEAL -> if (!isDevGodMode) lives = (lives + 1).coerceAtMost(5)
                                PowerUpType.FREEZE -> freezeTimeLeft = timeNow + 4_500_000_000L
                            }
                            audioEngine.playPowerUp()
                            pIter.remove()
                        } else if (p.y > screenHeight) {
                            pIter.remove()
                        }
                    }

                    if (!isFrozen) {
                        val isBossPresent = enemies.any { it.isBoss }
                        if (isBossPresent) {
                            val boss = enemies.first { it.isBoss }
                            boss.swayOffset += 0.05f * timeScale
                            boss.x += (cos(boss.swayOffset) * 4.2f) * timeScale
                            boss.y = bossTopY + sin(boss.swayOffset * 1.4f) * 18f

                            if (boss.x <= lateralPadding + pinchWallWidth) {
                                boss.x = lateralPadding + pinchWallWidth
                                enemyDir = 1f
                            } else if (boss.x >= screenWidth - bossWidth - lateralPadding - pinchWallWidth) {
                                boss.x = screenWidth - bossWidth - lateralPadding - pinchWallWidth
                                enemyDir = -1f
                            }
                        } else {
                            var wallHit = false
                            val baseSpeedX = (1.8f + currentWave * 0.20f) * timeScale
                            val constantFallSpeed = (0.42f + (currentWave * 0.03f)) * timeScale

                            for (i in enemies.indices) {
                                val e1 = enemies[i]
                                e1.swayOffset += e1.swaySpeed * timeScale

                                if (e1.kind == EnemyKind.ERA_BIO_PARASITA) {
                                    e1.y += (7.2f + currentWave * 0.15f) * timeScale
                                    val targetCenterX = shipX + shipWidth / 2f
                                    val myCenterX = e1.x + enemySize / 2f
                                    e1.x += ((targetCenterX - myCenterX) * 0.04f + sin(e1.swayOffset * 3f) * 3.5f) * timeScale
                                    if (e1.y > screenHeight) {
                                        e1.y = 80f
                                        e1.x = Random.nextFloat() * (screenWidth - enemySize - lateralPadding * 2) + lateralPadding
                                    }
                                } else if (!e1.isBoss) {
                                    when (e1.kind) {
                                        EnemyKind.ERA_VOID_ENIGMA -> {
                                            e1.x += (baseSpeedX * 1.3f * enemyDir) + sin(e1.swayOffset * 3f) * 4f
                                            e1.y += constantFallSpeed * 1.15f
                                        }
                                        EnemyKind.ERA_VOID_TESSERACT -> {
                                            e1.x += (baseSpeedX * 0.55f * enemyDir)
                                            e1.y += constantFallSpeed * 0.7f
                                        }
                                        EnemyKind.ERA_VOID_PRISMA -> {
                                            e1.x += (baseSpeedX * 1.05f * enemyDir)
                                            e1.y += constantFallSpeed * 0.9f
                                        }
                                        else -> {
                                            e1.x += (baseSpeedX * enemyDir) + sin(e1.swayOffset) * e1.swayAmplitude
                                            e1.y += constantFallSpeed
                                        }
                                    }
                                }

                                if (e1.y >= shipY + shipHeight && !e1.isBoss && e1.kind != EnemyKind.ERA_BIO_PARASITA) e1.y = 80f

                                if (e1.x <= lateralPadding && !e1.isBoss) {
                                    e1.x = lateralPadding
                                    wallHit = true
                                } else if (e1.x >= screenWidth - enemySize - lateralPadding && !e1.isBoss) {
                                    e1.x = screenWidth - enemySize - lateralPadding
                                    wallHit = true
                                }
                            }

                            if (wallHit) {
                                enemyDir *= -1f
                            }
                        }
                    }

                    // Colisão com nave
                    val shipRect = Rect(shipX, shipY, shipX + shipWidth, shipY + shipHeight)
                    val enemyIter = enemies.iterator()
                    while (enemyIter.hasNext()) {
                        val e = enemyIter.next()
                        val w = if (e.isBoss) bossWidth else enemySize
                        val h = if (e.isBoss) bossHeight else enemySize
                        if (shipRect.overlaps(Rect(e.x, e.y, e.x + w, e.y + h))) {
                            shakeIntensity = 14f
                            if (!isDevGodMode) {
                                if (hasShield) hasShield = false else lives--
                            }
                            if (!e.isBoss) enemyIter.remove()
                            audioEngine.playExplosion()

                            if (!isDevGodMode && lives <= 0) {
                                onGameOver(score, currentWave)
                                return@withFrameNanos
                            }
                            break
                        }
                    }

                    val bCheckIter = bullets.iterator()
                    while (bCheckIter.hasNext()) {
                        val b = bCheckIter.next()
                        if (b.isEnemy && shipRect.contains(Offset(b.x, b.y))) {
                            bCheckIter.remove()
                            shakeIntensity = 9f
                            if (!isDevGodMode) {
                                if (hasShield) hasShield = false else lives--
                            }

                            if (b.isParalyzing) {
                                paralyzeTimeLeft = timeNow + 1_400_000_000L
                            }

                            audioEngine.playExplosion()
                            if (!isDevGodMode && lives <= 0) {
                                onGameOver(score, currentWave)
                                return@withFrameNanos
                            }
                            break
                        }
                    }

                    val playerBulletsIter = bullets.iterator()
                    while (playerBulletsIter.hasNext()) {
                        val b = playerBulletsIter.next()
                        if (b.isEnemy) continue

                        // Tesseract gravitacional
                        val activeTesseract = enemies.firstOrNull { it.kind == EnemyKind.ERA_VOID_TESSERACT }
                        if (activeTesseract != null) {
                            val pullDx = (activeTesseract.x + enemySize / 2f) - b.x
                            b.x += (pullDx * 0.035f).coerceIn(-1.5f, 1.5f)
                        }

                        var bulletHit = false
                        val targetsIter = enemies.iterator()
                        while (targetsIter.hasNext()) {
                            val e = targetsIter.next()
                            val w = if (e.isBoss) bossWidth else enemySize
                            val h = if (e.isBoss) bossHeight else enemySize

                            if (Rect(e.x, e.y, e.x + w, e.y + h).contains(Offset(b.x, b.y))) {
                                bulletHit = true

                                // 1. Prisma Reflete o tiro frontal
                                if (e.kind == EnemyKind.ERA_VOID_PRISMA) {
                                    pendingBullets.add(Bullet(b.x, b.y, vx = (Random.nextFloat() - 0.5f) * 10f, vy = 15f, isEnemy = true))
                                    audioEngine.playPowerUp()
                                    break
                                }

                                // 2. Enigma Intangível
                                if (e.kind == EnemyKind.ERA_VOID_ENIGMA && e.isPhasedOut) {
                                    repeat(2) { particles.add(Particle(b.x, b.y, (Random.nextFloat() - 0.5f) * 4f, (Random.nextFloat() - 0.5f) * 4f, color = Color(0xFFC084FC))) }
                                    break
                                }

                                e.hp -= b.damage

                                if (e.isBoss) {
                                    bossHitFlashTime = timeNow + 60_000_000L
                                    bossCurrentHp = e.hp.coerceAtLeast(0)
                                }

                                if (e.hp <= 0) {
                                    targetsIter.remove()
                                    score += e.points
                                    audioEngine.playExplosion()

                                    val explosionColor = if (isVoidEra) Color(0xFFD946EF) else Color(0xFF10B981)
                                    repeat(if (e.isBoss) 16 else 6) {
                                        particles.add(Particle(e.x + w / 2f, e.y + h / 2f, (Random.nextFloat() - 0.5f) * 10f, (Random.nextFloat() - 0.5f) * 10f, color = explosionColor))
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

                    if (pendingBullets.isNotEmpty()) {
                        bullets.addAll(pendingBullets)
                    }
                    if (pendingEnemies.isNotEmpty()) {
                        enemies.addAll(pendingEnemies)
                    }

                    val partIter = particles.iterator()
                    while (partIter.hasNext()) {
                        val p = partIter.next()
                        p.x += p.vx
                        p.y += p.vy
                        p.alpha -= 0.06f
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
                        isTouchActive = true
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 280L) {
                            triggerBomb(System.nanoTime())
                        }
                        lastTapTime = now

                        val isParalyzed = System.nanoTime() < paralyzeTimeLeft
                        if (!isParalyzed) {
                            shipX = (down.position.x - shipWidth / 2f).coerceIn(lateralPadding + pinchWallWidth, screenWidth - shipWidth - lateralPadding - pinchWallWidth)
                        }

                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    val currentlyParalyzed = System.nanoTime() < paralyzeTimeLeft
                                    if (!currentlyParalyzed) {
                                        shipX = (change.position.x - shipWidth / 2f).coerceIn(lateralPadding + pinchWallWidth, screenWidth - shipWidth - lateralPadding - pinchWallWidth)
                                    }
                                    change.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        isTouchActive = false
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val _obs = frameTick
                val timeNow = System.nanoTime()

                for (i in stars.indices) {
                    val s = stars[i]
                    drawCircle(Color.White.copy(alpha = s.alpha), radius = s.size, center = Offset(s.x, s.y))
                }

                if (timeNow < freezeTimeLeft) {
                    drawRect(Color(0xFF00F5D4).copy(alpha = 0.12f), size = size)
                }

                if (timeNow < paralyzeTimeLeft) {
                    drawRect(Color(0xFF10B981).copy(alpha = 0.20f), size = size)
                }

                if (pinchWallWidth > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6).copy(alpha = 0.6f), Color.Transparent)),
                        topLeft = Offset(0f, 0f),
                        size = Size(pinchWallWidth + lateralPadding, screenHeight)
                    )
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF8B5CF6).copy(alpha = 0.6f))),
                        topLeft = Offset(screenWidth - pinchWallWidth - lateralPadding, 0f),
                        size = Size(pinchWallWidth + lateralPadding, screenHeight)
                    )
                }

                for (i in powerUps.indices) {
                    val pu = powerUps[i]
                    powerUpBitmaps[pu.type]?.let { bitmap ->
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset((pu.x - powerUpSize / 2f).toInt(), (pu.y - powerUpSize / 2f).toInt()),
                            dstSize = IntSize(powerUpSize.toInt(), powerUpSize.toInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                }

                for (i in enemies.indices) {
                    val e = enemies[i]
                    if (e.isBoss) {
                        val isFlashing = timeNow < bossHitFlashTime
                        when (e.bossType) {
                            BossType.MOTHERSHIP -> {
                                drawImage(
                                    image = bossMothershipBitmap,
                                    dstOffset = IntOffset(e.x.toInt(), e.y.toInt()),
                                    dstSize = IntSize(bossWidth.toInt(), bossHeight.toInt()),
                                    colorFilter = if (isFlashing) ColorFilter.tint(Color.White) else null,
                                    filterQuality = FilterQuality.None
                                )
                            }
                            BossType.DREADNOUGHT -> {
                                val pulseRotation = (frameTick * 0.3f) % 360f
                                rotate(degrees = pulseRotation, pivot = Offset(e.x + bossWidth / 2f, e.y + bossHeight / 2f)) {
                                    drawImage(
                                        image = orbitalBitmap,
                                        dstOffset = IntOffset(e.x.toInt(), e.y.toInt()),
                                        dstSize = IntSize(bossWidth.toInt(), bossHeight.toInt()),
                                        colorFilter = if (isFlashing) ColorFilter.tint(Color.White) else null,
                                        filterQuality = FilterQuality.None
                                    )
                                }
                            }
                            BossType.SERPENTOID -> {
                                val serpentTilt = sin(frameTick * 0.08f) * 6f
                                rotate(degrees = serpentTilt, pivot = Offset(e.x + bossWidth / 2f, e.y + bossHeight / 2f)) {
                                    drawImage(
                                        image = serpentoidBitmap,
                                        dstOffset = IntOffset(e.x.toInt(), e.y.toInt()),
                                        dstSize = IntSize(bossWidth.toInt(), bossHeight.toInt()),
                                        colorFilter = if (isFlashing) ColorFilter.tint(Color.White) else null,
                                        filterQuality = FilterQuality.None
                                    )
                                }
                            }
                            BossType.OMEGA -> {
                                val omegaTilt = sin(frameTick * 0.05f) * 4f
                                rotate(degrees = omegaTilt, pivot = Offset(e.x + bossWidth / 2f, e.y + bossHeight / 2f)) {
                                    drawImage(
                                        image = omegaBitmap,
                                        dstOffset = IntOffset(e.x.toInt(), e.y.toInt()),
                                        dstSize = IntSize(bossWidth.toInt(), bossHeight.toInt()),
                                        colorFilter = if (isFlashing) ColorFilter.tint(Color.White) else null,
                                        filterQuality = FilterQuality.None
                                    )
                                }
                            }
                        }
                    } else {
                        val bitmapToDraw = when (e.kind) {
                            EnemyKind.ERA_VOID_PRISMA -> vazioBitmap
                            EnemyKind.ERA_VOID_TESSERACT -> tesseractBitmap
                            EnemyKind.ERA_VOID_ENIGMA -> enigmaBitmap
                            EnemyKind.ERA_BIO_PARASITA -> parasitaBitmap
                            EnemyKind.ERA_BIO_INSECTOID -> insectoidBitmap
                            EnemyKind.ERA_BIO_CASULO -> casuloBitmap
                            EnemyKind.ERA_FIRE_GLAIVE -> glaiveBitmap
                            EnemyKind.ERA_FIRE_VESPA -> vespaBitmap
                            EnemyKind.ERA_FIRE_ARIETE -> arieteBitmap
                            EnemyKind.BEETLE_SHOOTER -> beetleShooterBitmap
                            else -> normalInvaderBitmap
                        }
                        val alphaFactor = if (e.isPhasedOut) 0.35f else 1.0f

                        drawImage(
                            image = bitmapToDraw,
                            dstOffset = IntOffset(e.x.toInt(), e.y.toInt()),
                            dstSize = IntSize(enemySize.toInt(), enemySize.toInt()),
                            alpha = alphaFactor,
                            filterQuality = FilterQuality.None
                        )

                        if (e.kind == EnemyKind.ERA_VOID_PRISMA) {
                            drawArc(
                                color = Color(0xFF38BDF8).copy(alpha = 0.75f),
                                startAngle = 30f,
                                sweepAngle = 120f,
                                useCenter = false,
                                topLeft = Offset(e.x - 4f, e.y + enemySize * 0.6f),
                                size = Size(enemySize + 8f, 16f),
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                }

                for (i in bullets.indices) {
                    val b = bullets[i]
                    if (b.isEnemy) {
                        val enemyLaserColor = when {
                            b.isParalyzing -> Color(0xFF10B981)
                            isVoidEra -> Color(0xFFD946EF)
                            isBioEra -> Color(0xFF34D399)
                            isFireEra -> Color(0xFFFF9E00)
                            else -> Color(0xFFFF3366)
                        }
                        drawRoundRect(
                            color = enemyLaserColor,
                            topLeft = Offset(b.x - 3.5f, b.y),
                            size = Size(if (b.isParalyzing) 9f else 7f, if (b.isParalyzing) 16f else 20f),
                            cornerRadius = CornerRadius(1f, 1f)
                        )
                    } else {
                        val bulletThickness = if (b.damage > 1) 13f else 8f
                        drawRoundRect(
                            color = equippedSkin.primaryColor,
                            topLeft = Offset(b.x - bulletThickness / 2f, b.y),
                            size = Size(bulletThickness, 26f),
                            cornerRadius = CornerRadius(1f, 1f)
                        )
                    }
                }

                for (i in particles.indices) {
                    val p = particles[i]
                    drawCircle(p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)), radius = p.size * p.alpha, center = Offset(p.x, p.y))
                }

                shipBitmaps[equippedSkin.id]?.let { bitmap ->
                    val shipCenter = Offset(shipX + shipWidth / 2f, shipY + shipHeight / 2f)
                    rotate(degrees = equippedSkin.gameRotation, pivot = shipCenter) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(shipX.toInt(), shipY.toInt()),
                            dstSize = IntSize(shipWidth.toInt(), shipHeight.toInt()),
                            filterQuality = FilterQuality.None
                        )
                    }
                }

                activeRushers.forEach { rusher ->
                    drawImage(
                        image = rusherBitmap,
                        dstOffset = IntOffset(rusher.x.toInt(), rusher.y.toInt()),
                        dstSize = IntSize(rusherSize.toInt(), rusherSize.toInt()),
                        filterQuality = FilterQuality.None
                    )
                }

                if (hasShield) {
                    drawCircle(Color(0xFFFFB703).copy(alpha = 0.4f), radius = shipWidth * 0.75f, center = Offset(shipX + shipWidth / 2f, shipY + shipHeight / 2f), style = Stroke(width = 3f))
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("SCORE: $score", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                            .border(
                                1.dp,
                                when {
                                    currentWave % 5 == 0 -> Color(0xFFFF0055)
                                    isVoidEra -> Color(0xFFD946EF)
                                    isBioEra -> Color(0xFF10B981)
                                    isFireEra -> Color(0xFFFF9E00)
                                    else -> Color(0xFFFFB703)
                                },
                                RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(if (currentWave % 5 == 0) "! ALERTA BOSS !" else "SETOR $currentWave", color = if (currentWave % 5 == 0) Color(0xFFFF0055) else Color(0xFFFFB703), fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(2.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isDevGodMode) "⚡ GOD" else "❤️ ".repeat(lives),
                                color = if (isDevGodMode) Color(0xFF00F5D4) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = onToggleSound,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.size(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text(if (soundEnabled) "🔊" else "🔇", fontSize = 12.sp)
                        }
                    }
                }

                val isBombReady = System.nanoTime() >= bombReadyTime
                val isParalyzed = System.nanoTime() < paralyzeTimeLeft

                Text(
                    text = when {
                        isParalyzed -> "⚠️ SISTEMA TRAVADO POR TEIA ÁCIDA!"
                        isBombReady -> "> BOMBA: PRONTA [2x TOQUE]"
                        else -> "> BOMBA: RECARREGANDO..."
                    },
                    color = when {
                        isParalyzed -> Color(0xFF10B981)
                        isBombReady -> Color(0xFF00F5D4)
                        else -> Color.Gray
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val currentBoss = enemies.firstOrNull { it.isBoss }
                if (currentWave % 5 == 0 && currentBoss != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF020617))
                            .border(1.dp, currentBoss.color, RoundedCornerShape(2.dp))
                            .padding(6.dp)
                    ) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "BOSS: ${currentBoss.bossType.displayName}",
                                    color = currentBoss.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text("$bossCurrentHp / $bossMaxHp HP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { animatedBossHpProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
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
// TELA DE GAME OVER RETRÔ
// -------------------------------------------------------------
@Composable
fun GameOverScreen(
    score: Int,
    highScore: Int,
    wave: Int,
    onRestart: () -> Unit,
    onBackToMenu: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF030712)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("MISSION ABORTED", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF0055), letterSpacing = 3.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFF0A0F1D))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(2.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("FINAL SCORE", fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                    Text("$score", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color(0xFF00E5FF), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("BEST RECORD: $highScore", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB703), fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("SECTOR REACHED: $wave", fontSize = 12.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5D4)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth(0.9f).height(46.dp)
            ) {
                Text("REINICIAR BATALHA", fontWeight = FontWeight.Black, color = Color(0xFF030712), fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onBackToMenu,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth(0.9f).height(44.dp)
            ) {
                Text("MENU PRINCIPAL", fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
            }
        }
    }
}