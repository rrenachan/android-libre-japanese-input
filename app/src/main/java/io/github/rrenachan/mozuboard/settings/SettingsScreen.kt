package io.github.rrenachan.mozuboard.settings

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import io.github.rrenachan.mozuboard.MozcUtil
import io.github.rrenachan.mozuboard.R
import io.github.rrenachan.mozuboard.preference.ClientSidePreference.KeyboardLayout
import io.github.rrenachan.mozuboard.preference.MiniBrowserActivity
import io.github.rrenachan.mozuboard.preference.MozcSoftwareKeyboardAdvancedPreferenceActivity
import io.github.rrenachan.mozuboard.userdictionary.UserDictionaryToolActivity
import io.github.rrenachan.mozuboard.util.LauncherIconManagerFactory

/**
 * Mozu Board の新しい設定画面 (Material You / Compose)。
 *
 * 変更点 (2026-11-28):
 * - 上部の「Mozu Board の設定」バーは削除。タイトルは画面内の見出しにする。
 * - ステータスバーは透明にして、アプリの背景色と一体化させる。
 *   (旧テーマの色がステータスバーに残らないように、Activity で
 *   decorFitsSystemWindows=false + statusBarColor=TRANSPARENT にする)
 * - 配色の自動調整 (ダイナミックカラーが変な色になる問題) は後回し。
 *   今は dynamicColorScheme を使いつつ、カード色を surfaceVariant に固定している。
 */
class MozuSettingsActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ステータスバーとナビゲーションバーをアプリの背景と一体化させる
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = Color.Transparent.toArgb()
    val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !dark
    setContent {
      MozuTheme {
        MozuSettingsScreen()
      }
    }
  }
}

@Composable
private fun MozuTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val dark = isSystemInDarkTheme()
  val colorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    dark -> darkColorScheme()
    else -> lightColorScheme()
  }
  MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun prefs(context: Context) =
  PreferenceManager.getDefaultSharedPreferences(context)

private fun isImeEnabled(context: Context): Boolean {
  val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
  return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

/** 画面に戻ってきたとき (onResume) に再評価する state。 */
@Composable
private fun rememberImeEnabled(): Boolean {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var enabled by remember { mutableStateOf(isImeEnabled(context)) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        enabled = isImeEnabled(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  return enabled
}

@Composable
fun MozuSettingsScreen() {
  var screen by remember { mutableStateOf("home") }
  if (screen == "layout") {
    LayoutSettingsScreen(onBack = { screen = "home" })
    return
  }
  if (screen == "about") {
    AboutSettingsScreen(onBack = { screen = "home" })
    return
  }
  // TopAppBar なし。ステータスバー分の余白だけ取る。
  LazyColumn(
    modifier = Modifier.fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Text(
        "Mozu Board",
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(top = 16.dp),
      )
    }
    item { EnableKeyboardSection() }
    item { LayoutSummarySection(onOpen = { screen = "layout" }) }
    item { InputFeedbackSection() }
    item { ConversionSection() }
    item { DictionarySection() }
    item { AboutSummarySection(onOpen = { screen = "about" }) }
    item { Spacer(Modifier.height(16.dp)) }
  }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium)
      Spacer(Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
private fun SwitchRow(
  title: String,
  description: String,
  key: String,
  defaultValue: Boolean,
  iconRes: Int? = null,
  onChanged: ((Boolean) -> Unit)? = null,
) {
  val context = LocalContext.current
  var checked by remember {
    mutableStateOf(prefs(context).getBoolean(key, defaultValue))
  }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (iconRes != null) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp).padding(end = 0.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.size(16.dp))
    }
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(description, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(
      checked = checked,
      onCheckedChange = {
        checked = it
        prefs(context).edit().putBoolean(key, it).apply()
        onChanged?.invoke(it)
      },
    )
  }
}

@Composable
private fun SectionTitle(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
  )
}

@Composable
private fun EnableKeyboardSection() {
  val context = LocalContext.current
  val enabled = rememberImeEnabled()
  Column {
    SectionTitle("キーボード")
    GroupCard {
      ClickRow(
        "キーボードを有効にする",
        if (enabled) "Mozu Board は有効です。"
        else "まだ有効になっていません。タップして入力方法の設定を開く",
        external = true, iconRes = R.drawable.ic_keyboard,
      ) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
      }
    }
  }
}

@Composable
private fun LayoutSummarySection(onOpen: () -> Unit) {
  val context = LocalContext.current
  val current = prefs(context).getString(
    "pref_current_keyboard_layout_key", KeyboardLayout.O24.name
  ) ?: KeyboardLayout.O24.name
  Column {
    SectionTitle("キーボードの配列")
    GroupCard {
      ClickRow("配列の設定", "現在の配列: $current", iconRes = R.drawable.ic_tune, onClick = onOpen)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutSettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val key = "pref_current_keyboard_layout_key"
  var selected by remember {
    mutableStateOf(
      prefs(context).getString(key, KeyboardLayout.O24.name) ?: KeyboardLayout.O24.name
    )
  }
  val items = listOf(
    KeyboardLayout.O24 to "O24 — 大西レイアウト",
    KeyboardLayout.QWERTY to "QWERTY",
    KeyboardLayout.TWELVE_KEYS to "12キー",
    KeyboardLayout.GODAN to "五段",
  )
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("キーボードの配列") },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { Spacer(Modifier.height(4.dp)) }
      item {
        SectionCard("配列を選ぶ") {
          Column(Modifier.selectableGroup()) {
            items.forEach { (layout, label) ->
              Row(
                Modifier.fillMaxWidth().selectable(
                  selected = selected == layout.name,
                  onClick = {
                    selected = layout.name
                    prefs(context).edit().putString(key, layout.name).apply()
                  },
                  role = Role.RadioButton,
                ).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = selected == layout.name,
                  onClick = {
                    selected = layout.name
                    prefs(context).edit().putString(key, layout.name).apply()
                  },
                )
                Text(label, style = MaterialTheme.typography.bodyLarge)
              }
            }
          }
        }
      }
      item {
        SectionCard("配列を追加する") {
          Text(
            "ここに今後、配列の追加・編集機能を入れます。\n" +
              "（カスタム配列の作成、キーの入れ替えなど）",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
      item {
        FilledTonalButton(onClick = {
          context.startActivity(
            Intent(context, MozcSoftwareKeyboardAdvancedPreferenceActivity::class.java)
          )
        }) {
          Text("キーボードの詳細設定")
        }
      }
      item {
        FilledTonalButton(onClick = onBack) { Text("戻る") }
      }
      item { Spacer(Modifier.height(16.dp)) }
    }
  }
}

@Composable
private fun InputFeedbackSection() {
  Column {
    SectionTitle("入力フィードバック")
    GroupCard {
      SwitchRow("触覚フィードバック", "キーを押したときに振動する", "pref_haptic_feedback_key", false, iconRes = R.drawable.ic_vibration)
      GroupDivider()
      SwitchRow("キー音", "キーを押したときに音を鳴らす", "pref_sound_feedback_key", false, iconRes = R.drawable.ic_volume_up)
      GroupDivider()
      SwitchRow("キーのポップアップ", "押したキーを大きく表示する", "pref_popup_feedback_key", true, iconRes = R.drawable.ic_preview)
    }
  }
}

@Composable
private fun ConversionSection() {
  Column {
    SectionTitle("変換")
    GroupCard {
      SwitchRow("スペースを全角にする", "スペースキーを全角スペースにする", "pref_space_character_form_key", false, iconRes = R.drawable.ic_space_bar)
      GroupDivider()
      SwitchRow("かな修飾子を無視して変換", "小さい「っ」などを気にせず変換する", "pref_kana_modifier_insensitive_conversion", true, iconRes = R.drawable.ic_translate)
      GroupDivider()
      SwitchRow("入力ミスを補正する", "タイプミスを自動で補正する", "pref_typing_correction", true, iconRes = R.drawable.ic_spellcheck)
      GroupDivider()
      SwitchRow("シークレットモード", "変換履歴を保存しない", "pref_other_anonimous_mode_key", false, iconRes = R.drawable.ic_visibility_off)
    }
  }
}

@Composable
private fun DictionarySection() {
  val context = LocalContext.current
  Column {
    SectionTitle("辞書")
    GroupCard {
      ClickRow("ユーザー辞書", "単語の登録・編集をする", iconRes = R.drawable.ic_book) {
        context.startActivity(Intent(context, UserDictionaryToolActivity::class.java))
      }
      GroupDivider()
      SwitchRow("音声入力", "音声で入力する", "pref_voice_input_key", true, iconRes = R.drawable.ic_mic)
    }
  }
}

@Composable
private fun AboutSummarySection(onOpen: () -> Unit) {
  val context = LocalContext.current
  Column {
    SectionTitle("Mozu Board について")
    GroupCard {
      ClickRow(
        "アプリについて",
        "バージョン: ${MozcUtil.getVersionName(context)}",
        iconRes = R.drawable.ic_info,
        onClick = onOpen,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("アプリについて") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = "戻る",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
      )
    },
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item { Spacer(Modifier.height(4.dp)) }
      // 1つのまとまりの中に項目を並べ、区切り線で分ける。
      item {
        GroupCard {
          ClickRow("制作者", "rrena (GitHub: rrenachan)", external = true, iconRes = R.drawable.ic_person) {
            context.startActivity(
              Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/rrenachan"))
            )
          }
          GroupDivider()
          ClickRow("オープンソースライセンス", "オープンソースソフトウェアに関するライセンスの詳細", iconRes = R.drawable.ic_description) {
            val intent = Intent(context, MiniBrowserActivity::class.java)
            intent.data = Uri.parse(context.getString(R.string.pref_oss_credits_url))
            context.startActivity(intent)
          }
          GroupDivider()
          ClickRow("利用規約", "準備中", iconRes = R.drawable.ic_gavel) { /* TODO: 利用規約のページを用意する */ }
          GroupDivider()
          ClickRow("プライバシーポリシー", "準備中", iconRes = R.drawable.ic_shield) { /* TODO: プライバシーポリシーのページを用意する */ }
          GroupDivider()
          InfoRow("現在のバージョン", MozcUtil.getVersionName(context))
        }
      }
      item {
        GroupCard {
          SwitchRow(
            "ランチャーにアイコンを表示する",
            "ホーム画面に Mozu Board のアイコンを表示する",
            "pref_launcher_icon_visibility",
            true,
            onChanged = {
              LauncherIconManagerFactory.getDefaultInstance().updateLauncherIconVisibility(context)
            },
          )
        }
      }
      item { Spacer(Modifier.height(16.dp)) }
    }
  }
}

/** 1つのまとまり。中の項目は区切り線で分ける。 */
@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(Modifier.padding(vertical = 4.dp), content = content)
  }
}

@Composable
private fun GroupDivider() {
  Divider(
    modifier = Modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.outline,
    thickness = 1.dp,
  )
}

/** 項目自体がボタンになる行。アプリ外に飛ぶ場合は右端に ↗ を表示する。 */
@Composable
private fun ClickRow(title: String, description: String?, external: Boolean = false, iconRes: Int? = null, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (iconRes != null) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.size(16.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      if (description != null) {
        Text(description, style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (external) {
      Icon(
        painter = painterResource(R.drawable.ic_open_in_new),
        contentDescription = "外部リンク",
        modifier = Modifier.size(20.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** タップしない表示だけの行。 */
@Composable
private fun InfoRow(title: String, value: String) {
  Column(Modifier.fillMaxWidth().padding(16.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Text(value, style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
