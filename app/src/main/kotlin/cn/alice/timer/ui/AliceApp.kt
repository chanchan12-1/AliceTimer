package cn.alice.timer.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.alice.timer.AppGraph
import cn.alice.timer.core.*
import cn.alice.timer.data.*
import cn.alice.timer.monitor.MonitorService
import cn.alice.timer.monitor.PermissionStatus
import cn.alice.timer.notification.CharacterAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable fun AliceApp(graph: AppGraph) = AliceTheme {
    val data by graph.store.data.collectAsStateWithLifecycle()
    val permissions by graph.permissions.collectAsStateWithLifecycle()
    val running by graph.serviceRunning.collectAsStateWithLifecycle()
    val error by graph.monitorError.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var setup by rememberSaveable { mutableStateOf(!data.onboardingSeen) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(data.onboardingSeen, data.monitoringEnabled, permissions.usage) {
        if (data.onboardingSeen && data.monitoringEnabled && permissions.usage) MonitorService.tryStart(graph.context)
    }
    if (setup) {
        SetupScreen(graph, data, permissions) {
            graph.completeOnboarding()
            setup = false
        }
    } else {
        Scaffold(containerColor = AliceBackground, bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                listOf("计时" to "◷", "台词" to "♡", "设置" to "⚙").forEachIndexed { index, (label, symbol) ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Text(symbol, fontSize = 24.sp) }, label = { Text(label) })
                }
            }
        }) { padding ->
            when (page) {
                0 -> HomeScreen(data, permissions, running, error, Modifier.padding(padding),
                    onSetup = { setup = true }, onMonitoring = { graph.setMonitoring(it) },
                    onStartNow = { scope.launch {
                        val message = runCatching { graph.startTimerNow(); "已从现在开始计时" }.getOrDefault("无法保存计时，请重试")
                        Toast.makeText(graph.context, message, Toast.LENGTH_SHORT).show()
                    } },
                    onChangeRemaining = { remainingMs -> scope.launch {
                        val message = runCatching {
                            if (graph.changeRemainingTime(remainingMs)) "剩余时间已修改" else "计时已结束或时间超过当前间隔，请重新设置"
                        }.getOrDefault("无法保存计时，请重试")
                        Toast.makeText(graph.context, message, Toast.LENGTH_SHORT).show()
                    } })
                1 -> DialogueScreen(graph, data, Modifier.padding(padding))
                else -> SettingsScreen(graph, data, permissions, Modifier.padding(padding), onSetup = { setup = true },
                    onPreview = { scope.launch {
                        val message = withContext(Dispatchers.IO) { graph.notifications.remind(graph.store.value, preview = true) }
                        Toast.makeText(graph.context, message, Toast.LENGTH_SHORT).show()
                    } })
            }
        }
    }
}

@Composable private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AliceMuted, fontSize = 13.sp)
    }
}

@Composable private fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable private fun HomeScreen(data: AppData, permissions: PermissionStatus, running: Boolean, error: String,
    modifier: Modifier, onSetup: () -> Unit, onMonitoring: (Boolean) -> Unit,
    onStartNow: () -> Unit, onChangeRemaining: (Long) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editRemaining by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data.cycle.startedAt, data.cycle.dueAt) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val c = data.cycle
    val remaining = (c.dueAt - now).coerceAtLeast(0)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Heading("爱丽丝计时器", "作者：bilibili w蝉蝉") }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
                Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFE9E8FB), Color(0xFFEDF5FA))))
                    .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("✦  老师，交给爱丽丝吧  ✦", color = AlicePurple, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("摸头倒计时", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        OutlinedButton(onClick = onStartNow) { Text("从现在开始", fontSize = 12.sp) }
                    }
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                        CircularProgressIndicator(progress = {
                            if (c.phase == Phase.RUNNING) (remaining.toFloat() / (c.dueAt - c.startedAt).coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
                        }, modifier = Modifier.fillMaxSize(), color = AlicePurple, trackColor = Color.White.copy(alpha = 0.65f), strokeWidth = 6.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(when (c.phase) { Phase.RUNNING -> "摸头倒计时"; Phase.NOTIFIED -> "本轮已到时间"; else -> "咖啡厅待机中" }, color = AliceMuted, fontSize = 13.sp)
                            Text(if (c.phase == Phase.RUNNING) countdown(remaining) else if (c.phase == Phase.NOTIFIED) "时间到啦" else "(｡･ω･｡)",
                                fontSize = if (c.phase == Phase.RUNNING) 31.sp else 25.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace)
                            if (c.phase == Phase.RUNNING) Text("预计 ${dateTime(c.dueAt)} 提醒", fontSize = 11.sp, color = AliceMuted)
                        }
                    }
                    OutlinedButton(onClick = { editRemaining = true }, enabled = c.phase == Phase.RUNNING && remaining > 0,
                        modifier = Modifier.fillMaxWidth()) { Text("修改剩余时间") }
                    Text(when (c.phase) {
                        Phase.RUNNING -> "安心忙你的事，时间到了会叫你哦。"
                        Phase.NOTIFIED -> "下一次进入游戏后，会准备新一轮提醒。"
                        else -> if (c.gameForeground) "正在游戏中，离开后自动开始计时。" else "进入游戏，再离开，就会自动开始计时。"
                    }, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
        item { SoftCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("自动监测", fontWeight = FontWeight.SemiBold)
                    Text(data.gameLabel, fontSize = 12.sp, color = AliceMuted)
                }
                Switch(checked = data.monitoringEnabled, onCheckedChange = onMonitoring)
            }
            val status = when {
                !data.monitoringEnabled -> "监测已暂停 · 已开始的计时仍会提醒"
                !permissions.usage -> "等待使用情况访问授权"
                error.isNotEmpty() -> error
                !running -> "后台监测尚未启动 · 打开设置检查权限"
                c.phase == Phase.RUNNING -> "正在计时 · 重进游戏不会重置"
                c.phase == Phase.NOTIFIED -> "等待下一次进入游戏"
                c.gameForeground -> "游戏正在前台 · 等待离开游戏"
                else -> "正在监测 · 等待进入游戏"
            }
            Text("●  $status", color = if (running) Color(0xFF679682) else AliceMuted, fontSize = 12.sp)
        } }
        if (!permissions.usage || !permissions.notifications || !permissions.exact || !permissions.battery || !data.autoStartConfirmed) item {
            SoftCard {
                Text("让提醒准时抵达", fontWeight = FontWeight.SemiBold)
                Text("请检查通知、后台无限制与自启动设置。未允许精确提醒时，系统可能延后通知。", color = AliceMuted, fontSize = 12.sp)
                OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("检查运行权限") }
            }
        }
        item { SoftCard {
            Text("今日值班学生", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Characters.all.forEach { character -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(graph = null, character = character, uri = data.avatarUris[character.id])
                    Text(character.name, fontSize = 12.sp, modifier = Modifier.padding(top = 7.dp), color = if (character.id in data.enabledCharacters) AliceInk else AliceMuted)
                } }
            }
            Text("到时间后，随机一位学生给老师发来消息。", color = AliceMuted, fontSize = 12.sp)
        } }
        if (data.lastReminder.isNotBlank()) item { SoftCard {
            Text("上一封来信 · ${dateTime(data.lastReminderAt)}", fontSize = 12.sp, color = AliceMuted)
            Text(data.lastReminder, fontSize = 14.sp)
        } }
        item { Text("只在本机检测前后台状态，不读取游戏数据。", fontSize = 11.sp, color = AliceMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
    if (editRemaining) RemainingTimeDialog(remaining, data.intervalMs, { editRemaining = false }) {
        onChangeRemaining(it)
        editRemaining = false
    }
}

@Composable private fun RemainingTimeDialog(remainingMs: Long, maxIntervalMs: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val initialMinutes = remember { ((remainingMs + 59_999) / 60_000).coerceAtMost(maxIntervalMs / 60_000) }
    var hours by rememberSaveable { mutableStateOf((initialMinutes / 60).toString()) }
    var minutes by rememberSaveable { mutableStateOf((initialMinutes % 60).toString()) }
    val hourValue = hours.toLongOrNull() ?: 0
    val minuteValue = minutes.toLongOrNull() ?: 0
    val totalMs = (hourValue * 60 + minuteValue) * 60_000
    val error = when {
        minuteValue > 59 -> "分钟请输入 0 到 59"
        totalMs <= 0 -> "剩余时间至少为 1 分钟"
        totalMs > maxIntervalMs -> "不能超过设置的提醒间隔：${durationLabel(maxIntervalMs)}"
        else -> null
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("修改剩余时间") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(hours, { if (it.length <= 3 && it.all(Char::isDigit)) hours = it }, Modifier.weight(1f),
                    label = { Text("小时") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(minutes, { if (it.length <= 2 && it.all(Char::isDigit)) minutes = it }, Modifier.weight(1f),
                    label = { Text("分钟") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("从保存时起计算剩余时间，最多 ${durationLabel(maxIntervalMs)}。", color = AliceMuted, fontSize = 12.sp)
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }, confirmButton = { TextButton(onClick = { onSave(totalMs) }, enabled = error == null) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun Avatar(graph: AppGraph?, character: Character, uri: String?, size: Int = 52) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, character.id, uri) {
        value = withContext(Dispatchers.IO) { CharacterAssets.load(graph?.context ?: context, character.id, uri) }
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(character.color).copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), character.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(character.name, color = Color(character.color).copy(alpha = 1f),
            fontSize = (size / maxOf(2.8f, character.name.length * 1.35f)).sp,
            maxLines = 1, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun SetupScreen(graph: AppGraph, data: AppData, permissions: PermissionStatus, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { graph.refreshPermissions() }
    Scaffold(containerColor = AliceBackground) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Heading("欢迎回来，老师！", "花一分钟设置，让爱丽丝安心在后台等候。") }
            item { SoftCard {
                Text("请允许后台无限制和应用自启动", fontWeight = FontWeight.Bold, color = AlicePurple)
                Text("锁屏、关闭界面后也需要检测游戏是否离开前台。请把爱丽丝计时器的电池设置改为「无限制」，并允许「自启动 / 后台活动」。", fontSize = 13.sp, color = AliceMuted)
                Text("只负责提醒；无需无障碍权限，不自动操作游戏。", fontSize = 12.sp)
            } }
            item { PermissionCard("01", "使用情况访问", "用于识别游戏进入与离开前台。", permissions.usage, "去授权") { SystemSettings.usage(context) } }
            item { PermissionCard("02", "角色通知", "允许学生发来摸头提醒。", permissions.notifications, "允许通知") {
                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else SystemSettings.notifications(context)
            } }
            item { PermissionCard("03", "后台无限制", "取消电池优化，并在应用电量管理中允许后台运行。", permissions.battery, "电池设置") { SystemSettings.battery(context) } }
            item { SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("04", color = AlicePurple, modifier = Modifier.padding(end = 14.dp))
                    Text("应用自启动", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { SystemSettings.autoStart(context) }) { Text("去设置") }
                }
                Text(SystemSettings.autoStartInstructions(), fontSize = 12.sp, color = AliceMuted)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { graph.store.update { it.copy(autoStartConfirmed = !it.autoStartConfirmed) } }) {
                    Checkbox(data.autoStartConfirmed, onCheckedChange = { checked -> graph.store.update { it.copy(autoStartConfirmed = checked) } })
                    Text("我已检查（或设备没有此开关）", fontSize = 12.sp)
                }
                Text("系统未提供统一查询接口，这项由老师确认。", fontSize = 11.sp, color = AliceMuted)
            } }
            if (Build.VERSION.SDK_INT >= 31) item { PermissionCard("05", "精确提醒", "允许「闹钟和提醒」，减少到时提醒延迟。", permissions.exact, "去设置") { SystemSettings.exact(context) } }
            item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                Text(if (permissions.usage) "准备好了，进入计时器" else "稍后设置，先看看")
            } }
            item { Text("系统设置中的「强行停止」会终止后台工作，需要再次打开应用恢复。可随时在设置页重新检查权限。", color = AliceMuted, fontSize = 11.sp) }
        }
    }
}

@Composable private fun PermissionCard(number: String, title: String, subtitle: String, granted: Boolean, button: String, action: () -> Unit) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number, color = AlicePurple, modifier = Modifier.padding(end = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (granted) Text("✓ 已开启", color = Color(0xFF679682), fontSize = 11.sp)
            }
            OutlinedButton(onClick = action) { Text(if (granted) "查看" else button) }
        }
        Text(subtitle, fontSize = 12.sp, color = AliceMuted)
    }
}

@Composable private fun DialogueScreen(graph: AppGraph, data: AppData, modifier: Modifier) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Dialogue?>(null) }
    var deleting by remember { mutableStateOf<Dialogue?>(null) }
    val character = Characters.all.find { it.id == selected }
    if (character != null) BackHandler { selected = null }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selected != null) {
            runCatching { graph.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            graph.store.update { it.copy(avatarUris = it.avatarUris + (selected!! to uri.toString())) }
        }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            if (character == null) Heading("来自学生的消息", "每一句提醒，都可以由老师亲自编写。")
            else {
                TextButton(onClick = { selected = null }, contentPadding = PaddingValues(0.dp)) { Text("‹  所有角色") }
                Heading("${character.name}的台词", "${character.fullName}  /  ${character.englishName}")
            }
        }
        if (character == null) items(Characters.all, key = { it.id }) { person ->
            SoftCard(Modifier.clickable { selected = person.id }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(graph, person, data.avatarUris[person.id], 60)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(person.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${data.lines.count { it.characterId == person.id && it.enabled }} 条启用 · ${data.lines.count { it.characterId == person.id }} 条台词", fontSize = 12.sp, color = AliceMuted)
                    }
                    Text("›", fontSize = 26.sp, color = AliceMuted)
                }
            }
        } else {
            item { SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(graph, character, data.avatarUris[character.id], 64)
                    Column {
                        Text("角色头像", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }) { Text("替换图片") }
                    }
                    if (data.avatarUris.containsKey(character.id)) TextButton(onClick = { graph.store.update { it.copy(avatarUris = it.avatarUris - character.id) } }) { Text("恢复") }
                }
            } }
            items(data.lines.filter { it.characterId == character.id }, key = { it.id }) { line -> SoftCard {
                Text(line.text, fontSize = 15.sp, lineHeight = 23.sp, color = if (line.enabled) AliceInk else AliceMuted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(line.enabled, onCheckedChange = { enabled -> graph.store.update { it.copy(lines = it.lines.map { current -> if (current.id == line.id) current.copy(enabled = enabled) else current }) } })
                    Text(if (line.enabled) "启用" else "禁用", fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    TextButton(onClick = { editing = line }) { Text("编辑") }
                    TextButton(onClick = { deleting = line }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            } }
            item { OutlinedButton(onClick = { editing = Dialogue(UUID.randomUUID().toString(), character.id, "") }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("＋ 新增台词") } }
            if (data.lines.none { it.characterId == character.id && it.enabled }) item { Text("这个角色没有可用台词时，将暂时不参与随机提醒。", fontSize = 12.sp, color = AliceMuted) }
        }
    }
    editing?.let { line -> DialogueEditor(line, onDismiss = { editing = null }) { text ->
        graph.store.update { current -> current.copy(lines = if (current.lines.any { it.id == line.id }) current.lines.map { if (it.id == line.id) it.copy(text = text) else it } else current.lines + line.copy(text = text)) }
        editing = null
    } }
    deleting?.let { line -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除这条台词？") }, text = { Text(line.text) },
        confirmButton = { TextButton(onClick = { graph.store.update { it.copy(lines = it.lines.filterNot { item -> item.id == line.id }) }; deleting = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("保留") } }) }
}

@Composable private fun DialogueEditor(line: Dialogue, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(line.id) { mutableStateOf(line.text) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (line.text.isEmpty()) "新增台词" else "编辑台词") }, text = {
        OutlinedTextField(text, onValueChange = { if (it.length <= 500) text = it }, minLines = 4, maxLines = 8,
            label = { Text("学生想对老师说……") }, supportingText = { Text("${text.length}/500") })
    }, confirmButton = { TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun SettingsScreen(graph: AppGraph, data: AppData, permissions: PermissionStatus, modifier: Modifier, onSetup: () -> Unit, onPreview: () -> Unit) {
    var customTime by remember { mutableStateOf(false) }
    var installedPicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading("老师的设置", "把提醒调成你喜欢的样子。") }
        item { SoftCard {
            Text("提醒间隔", fontWeight = FontWeight.SemiBold)
            Text(durationLabel(data.intervalMs), fontSize = 28.sp, color = AlicePurple)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(30L to "30分", 60L to "1小时", 120L to "2小时", 180L to "3小时").forEach { (minutes, label) ->
                    FilterChip(selected = data.intervalMs == minutes * 60_000, onClick = { graph.store.update { it.copy(intervalMs = minutes * 60_000) } }, label = { Text(label, fontSize = 12.sp) })
                }
            }
            OutlinedButton(onClick = { customTime = true }, modifier = Modifier.fillMaxWidth()) { Text("自定义小时 / 分钟") }
            Text("修改间隔会用于下一个周期，当前倒计时保持原来的截止时间。", color = AliceMuted, fontSize = 12.sp)
        } }
        item { SoftCard {
            Text("监测的游戏版本", fontWeight = FontWeight.SemiBold)
            GameVersions.all.forEach { version ->
                val installed = version.packages.any { context.packageManager.getLaunchIntentForPackage(it) != null }
                Row(Modifier.fillMaxWidth().clickable(enabled = data.cycle.phase != Phase.RUNNING) { graph.selectGame(version.title, version.packages) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = data.gamePackages == version.packages,
                        onClick = { graph.selectGame(version.title, version.packages) }, enabled = data.cycle.phase != Phase.RUNNING)
                    Column(Modifier.weight(1f)) {
                        Text(version.title, fontSize = 13.sp)
                        Text(if (installed) "已安装" else "未检测到已安装版本", color = AliceMuted, fontSize = 11.sp)
                    }
                }
            }
            OutlinedButton(onClick = { installedPicker = true }, enabled = data.cycle.phase != Phase.RUNNING, modifier = Modifier.fillMaxWidth()) { Text("从已安装应用选择其他版本") }
            if (data.gamePackages !in GameVersions.all.map { it.packages }) Text("当前：${data.gameLabel}", fontSize = 12.sp, color = AlicePurple)
            Text(if (data.cycle.phase == Phase.RUNNING) "本轮计时结束后可以切换版本。" else "使用包名识别，兼容不同语言的应用名称。每次监测一个版本。", color = AliceMuted, fontSize = 12.sp)
        } }
        item { SoftCard {
            Text("通知显示", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("常驻监测通知", Modifier.weight(1f))
                Text(if (permissions.monitorNotificationVisible) "显示" else "已隐藏", color = AlicePurple, fontSize = 13.sp)
            }
            OutlinedButton(onClick = { SystemSettings.monitorNotification(context) }, modifier = Modifier.fillMaxWidth()) { Text("显示 / 隐藏") }
            Text("跳转后关闭通知即可隐藏常驻通知", color = AliceMuted, fontSize = 12.sp)
            if (!permissions.notifications) Text("角色提醒未开启，请在后台运行与权限中开启角色通知。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } }
        item { SoftCard {
            Text("随机通知角色", fontWeight = FontWeight.SemiBold)
            Characters.all.forEach { character -> Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(graph, character, data.avatarUris[character.id], 40)
                Text(character.name, Modifier.weight(1f).padding(start = 14.dp))
                Switch(character.id in data.enabledCharacters, onCheckedChange = { checked -> graph.store.update {
                    it.copy(enabledCharacters = if (checked) it.enabledCharacters + character.id else it.enabledCharacters - character.id)
                } })
            } }
            if (DialoguePicker.pick(data.lines, data.enabledCharacters) == null) Text("没有可用角色台词，将不会发送角色提醒。", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) { Text("发送一条预览通知") }
        } }
        item { SoftCard {
            Text("后台运行与权限", fontWeight = FontWeight.SemiBold)
            Text("使用情况访问：${if (permissions.usage) "已开启" else "待授权"}\n角色通知：${if (permissions.notifications) "已开启" else "待授权"}\n精确提醒：${if (permissions.exact) "已开启" else "系统可能延后"}\n电池优化：${if (permissions.battery) "已取消" else "请设为无限制"}", fontSize = 13.sp, lineHeight = 24.sp, color = AliceMuted)
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("后台无限制 / 自启动设置") }
        } }
        item { Text("爱丽丝计时器 0.1.3\n本地提醒工具 · 无联网 · 无自动点击", color = AliceMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    if (customTime) IntervalDialog(data.intervalMs, { customTime = false }) { interval -> graph.store.update { it.copy(intervalMs = interval) }; customTime = false }
    if (installedPicker) InstalledPicker(graph, { installedPicker = false })
}

@Composable private fun IntervalDialog(intervalMs: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var hours by remember { mutableStateOf((intervalMs / 3_600_000).toString()) }
    var minutes by remember { mutableStateOf((intervalMs / 60_000 % 60).toString()) }
    val total = (hours.toLongOrNull() ?: 0) * 60 + (minutes.toLongOrNull() ?: 0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("自定义提醒间隔") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(hours, { if (it.length <= 3 && it.all(Char::isDigit)) hours = it }, Modifier.weight(1f), label = { Text("小时") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(minutes, { if (it.length <= 3 && it.all(Char::isDigit)) minutes = it }, Modifier.weight(1f), label = { Text("分钟") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("支持 1 分钟到 7 天；下个周期生效。", fontSize = 12.sp, color = AliceMuted)
        }
    }, confirmButton = { TextButton(onClick = { onSave(total * 60_000) }, enabled = total in 1..10_080) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable private fun InstalledPicker(graph: AppGraph, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = remember {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }.distinctBy { it.activityInfo.packageName }
            .map { it.loadLabel(context.packageManager).toString() to it.activityInfo.packageName }.sortedBy { it.first }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择已安装的游戏") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) { items(apps) { (label, packageName) ->
            Column(Modifier.fillMaxWidth().clickable { graph.selectGame(label, setOf(packageName)); onDismiss() }.padding(vertical = 12.dp)) {
                Text(label); Text(packageName, fontSize = 10.sp, color = AliceMuted)
            }
        } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun countdown(ms: Long): String {
    val seconds = (ms + 999) / 1_000
    return "%02d:%02d:%02d".format(Locale.ROOT, seconds / 3_600, seconds / 60 % 60, seconds % 60)
}
private fun durationLabel(ms: Long): String {
    val minutes = ms / 60_000
    if (minutes == 0L) return "${ms / 1000} 秒（测试）"
    return listOfNotNull(if (minutes >= 60) "${minutes / 60} 小时" else null, if (minutes % 60 > 0) "${minutes % 60} 分钟" else null).joinToString(" ")
}
private fun dateTime(at: Long) = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(at))
