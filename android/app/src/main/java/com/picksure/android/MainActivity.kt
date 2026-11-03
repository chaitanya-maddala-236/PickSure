package com.picksure.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picksure.android.data.local.PickEventEntity
import com.picksure.android.data.local.PickSureDatabase
import com.picksure.android.data.local.TaskCacheEntity
import com.picksure.android.data.local.TaskItemCacheEntity
import com.picksure.android.data.remote.ApiFactory
import com.picksure.android.data.remote.ApprovalRequest
import com.picksure.android.data.remote.LoginRequest
import com.picksure.android.data.remote.FreshnessSummaryDto
import com.picksure.android.data.remote.LotVerifyRequest
import com.picksure.android.data.remote.ScanVerifyRequest
import com.picksure.android.data.remote.SubstitutionDto
import com.picksure.android.data.remote.TaskDto
import com.picksure.android.data.remote.TaskItemDto
import com.picksure.android.data.sync.LocalEventRepository
import com.picksure.android.data.sync.SyncScheduler
import com.picksure.android.ui.BarcodeCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

private val Forest = Color(0xFF176B4D)
private val ForestDark = Color(0xFF0E4C37)
private val Canvas = Color(0xFFF4F6F1)
private val Ink = Color(0xFF1D2924)
private val Muted = Color(0xFF64736C)
private val Orange = Color(0xFFC26A12)
private val Red = Color(0xFFB3261E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White,
                background = Canvas, surface = Color.White, onSurface = Ink, error = Red)) {
                PickSureApp()
            }
        }
    }
}

private data class PickableItem(
    val id: String,
    val taskId: String,
    val orderId: String,
    val sku: String,
    val name: String,
    val barcode: String,
    val requested: Int,
    val aisle: String,
    val rack: String,
    val shelf: String,
    val serverPicked: Int = 0,
    val serverStatus: String = "open",
    val recommendedLotId: String? = null,
    val lotCode: String? = null,
    val lotTag: String? = null,
    val expiresOn: String? = null,
    val lotStatus: String = "no_stock",
)

private data class OrderTask(val id: String, val orderId: String, val priority: String, val items: List<PickableItem>, val status: String = "assigned")
private data class BatchLine(val sku: String, val name: String, val barcode: String, val aisle: String, val rack: String,
                             val shelf: String, val items: List<PickableItem>, val recommendedLotId: String? = null,
                             val lotCode: String? = null, val lotTag: String? = null, val expiresOn: String? = null,
                             val lotStatus: String = "no_stock")
private enum class Screen { LOGIN, DASHBOARD, BATCH, SCANNER, EXCEPTION, PACKING, SYNC, APPROVALS, FRESHNESS }

private fun pickedProgress(item: PickableItem, events: List<PickEventEntity>): Int {
    val related = events.filter { it.taskItemId == item.id && it.eventType == "PICK" }
    val acknowledgedLocally = related.filter { it.syncStatus == "SYNCED" }.sumOf { it.quantity }
    val waitingForAck = related.filter { it.syncStatus == "PENDING" || it.syncStatus == "SYNCING" }.sumOf { it.quantity }
    return maxOf(item.serverPicked, acknowledgedLocally) + waitingForAck
}

private fun demoTasks(): List<OrderTask> {
    fun item(id: String, task: String, order: String, sku: String, name: String, barcode: String, aisle: String, rack: String, shelf: String): PickableItem {
        val lotCode = "${sku.substringBefore('-')}-${java.time.LocalDate.now().year}-FRESH"
        val productId = when (sku.substringBefore('-')) {
            "MILK" -> "milk"
            "BREAD" -> "bread"
            "EGG" -> "eggs"
            "BUTTER" -> "butter"
            else -> "yogurt"
        }
        return PickableItem(id, task, order, sku, name, barcode, 1, aisle, rack, shelf,
            recommendedLotId = "lot-sku-$productId-fresh", lotCode = lotCode,
            lotTag = "PICKSURE-LOT-${sku.substringBefore('-')}-FRESH-01",
            expiresOn = java.time.LocalDate.now().plusDays(14).toString(), lotStatus = "ready")
    }
    return listOf(
        OrderTask("task-101", "order-101", "high", listOf(
            item("item-101-milk", "task-101", "order-101", "MILK-AMUL-500", "Amul Milk 500 ml", "8901262150012", "A1", "R1", "S2"),
            item("item-101-bread", "task-101", "order-101", "BREAD-WHEAT-400", "Whole Wheat Bread 400 g", "8901058850019", "A1", "R2", "S1"),
        )),
        OrderTask("task-102", "order-102", "normal", listOf(
            item("item-102-milk", "task-102", "order-102", "MILK-AMUL-500", "Amul Milk 500 ml", "8901262150012", "A1", "R1", "S2"),
            item("item-102-eggs", "task-102", "order-102", "EGG-FARM-6", "Farm Eggs 6 pack", "8901725123406", "A2", "R1", "S3"),
        )),
        OrderTask("task-103", "order-103", "normal", listOf(
            item("item-103-bread", "task-103", "order-103", "BREAD-WHEAT-400", "Whole Wheat Bread 400 g", "8901058850019", "A1", "R2", "S1"),
            item("item-103-butter", "task-103", "order-103", "BUTTER-AMUL-100", "Amul Butter 100 g", "8901262150029", "A2", "R2", "S1"),
        )),
    )
}

private fun fromApi(tasks: List<TaskDto>, itemsByTask: Map<String, List<TaskItemDto>>): List<OrderTask> = tasks.map { task ->
    OrderTask(task.id, task.order_id, task.priority, itemsByTask[task.id].orEmpty().map { item ->
        PickableItem(item.id, item.task_id, task.order_id, item.sku, item.product_name, item.barcode,
            item.requested_qty, item.aisle, item.rack, item.shelf, item.picked_qty, item.status,
            item.recommended_lot_id, item.lot_code, item.lot_tag, item.expires_on, item.lot_status)
    }, task.status)
}

private fun fromCache(snapshots: List<com.picksure.android.data.local.CachedTaskSnapshot>): List<OrderTask> = snapshots.map { snapshot ->
    OrderTask(snapshot.task.id, snapshot.task.orderId, snapshot.task.priority, snapshot.items.map { item ->
        PickableItem(item.id, item.taskId, item.orderId, item.sku, item.productName, item.barcode,
            item.requestedQty, item.aisle, item.rack, item.shelf, item.serverPicked, item.serverStatus,
            item.recommendedLotId, item.lotCode, item.lotTag, item.expiresOn, item.lotStatus)
    }, snapshot.task.status)
}

@Composable
private fun PickSureApp() {
    val context = LocalContext.current
    val api = remember { ApiFactory.create(context) }
    val database = remember { PickSureDatabase.get(context) }
    val events by database.pickEventDao().observeAll().collectAsState(initial = emptyList())
    val cachedSnapshots by database.taskCacheDao().observeSnapshot().collectAsState(initial = emptyList())
    val prefs = remember { context.getSharedPreferences("picksure", android.content.Context.MODE_PRIVATE) }
    val repository = remember { LocalEventRepository(context) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(if (prefs.getString("token", null).isNullOrBlank()) Screen.LOGIN else Screen.DASHBOARD) }
    var tasks by remember { mutableStateOf(demoTasks()) }
    var employeeName by remember { mutableStateOf(prefs.getString("employee_name", "Picker") ?: "Picker") }
    var employeeRole by remember { mutableStateOf(prefs.getString("employee_role", "picker") ?: "picker") }
    var approvals by remember { mutableStateOf(emptyList<SubstitutionDto>()) }
    var freshness by remember { mutableStateOf<FreshnessSummaryDto?>(null) }
    var email by remember { mutableStateOf("picker@picksure.demo") }
    var password by remember { mutableStateOf("PickSure123!") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<PickableItem?>(null) }
    var quantity by remember { mutableIntStateOf(1) }
    var exceptionReason by remember { mutableStateOf("Out of stock") }
    var replacementSku by remember { mutableStateOf("YOGURT-PLAIN-400") }
    var currentOrder by remember { mutableStateOf<OrderTask?>(null) }
    var scanStep by remember { mutableStateOf("PRODUCT") }

    fun pickedCount(item: PickableItem): Int = pickedProgress(item, events)
    fun resolved(item: PickableItem): Boolean = pickedCount(item) >= item.requested || item.serverStatus in setOf("substituted", "unavailable") || events.any {
        it.taskItemId == item.id && it.eventType == "EXCEPTION" && it.syncStatus != "NEEDS_ATTENTION"
    }
    fun nextItem(line: BatchLine): PickableItem? = line.items.firstOrNull { item -> pickedCount(item) < item.requested && !resolved(item) && item.serverStatus != "substitution_pending" && !events.any {
        it.taskItemId == item.id && it.eventType == "SUBSTITUTION" && it.syncStatus == "PENDING"
    } }
    fun batchLines(): List<BatchLine> = tasks.flatMap { it.items }.groupBy { it.sku }.map { (_, sameSku) ->
        val first = sameSku.first()
        BatchLine(first.sku, first.name, first.barcode, first.aisle, first.rack, first.shelf, sameSku)
    }.map { line ->
        val first = line.items.first()
        line.copy(recommendedLotId = first.recommendedLotId, lotCode = first.lotCode,
            lotTag = first.lotTag, expiresOn = first.expiresOn, lotStatus = first.lotStatus)
    }.sortedWith(compareBy<BatchLine>({ it.aisle }, { it.rack }, { it.shelf }, { it.sku }))

    suspend fun loadServerTasks(): List<OrderTask> {
        val loaded = withContext(Dispatchers.IO) {
            val taskList = api.tasks()
            val itemMap = taskList.associate { task -> task.id to api.taskItems(task.id) }
            fromApi(taskList, itemMap)
        }
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.taskCacheDao().clearItems()
                database.taskCacheDao().clearTasks()
                database.taskCacheDao().putTasks(loaded.map { task -> TaskCacheEntity(task.id, task.orderId, task.priority, task.status) })
                database.taskCacheDao().putItems(loaded.flatMap { task -> task.items.map { item ->
                    TaskItemCacheEntity(item.id, item.taskId, item.orderId, item.sku, item.name, item.barcode,
                        item.requested, item.aisle, item.rack, item.shelf, item.serverPicked, item.serverStatus,
                        item.recommendedLotId, item.lotCode, item.lotTag, item.expiresOn, item.lotStatus)
                } })
            }
        }
        return loaded
    }

    suspend fun loadApprovals(): List<SubstitutionDto> = withContext(Dispatchers.IO) { api.pendingSubstitutions() }
    suspend fun loadFreshness(): FreshnessSummaryDto = withContext(Dispatchers.IO) { api.freshness() }

    LaunchedEffect(Unit) {
        if (!prefs.getString("token", null).isNullOrBlank()) {
            runCatching { loadServerTasks() }.onSuccess { if (it.isNotEmpty()) tasks = it }
            runCatching { loadFreshness() }.onSuccess { freshness = it }
            if (employeeRole in setOf("supervisor", "admin")) runCatching { loadApprovals() }.onSuccess { approvals = it }
            SyncScheduler.schedule(context)
        }
    }
    LaunchedEffect(cachedSnapshots) {
        if (cachedSnapshots.isNotEmpty()) tasks = fromCache(cachedSnapshots)
    }

    val acknowledgedTaskEvents = events.filter { it.eventType in setOf("PICK", "SUBSTITUTION") && it.syncStatus == "SYNCED" }.map { it.eventUuid }
    LaunchedEffect(acknowledgedTaskEvents) {
        if (!prefs.getString("token", null).isNullOrBlank() && acknowledgedTaskEvents.isNotEmpty()) {
            runCatching { loadServerTasks() }.onSuccess { if (it.isNotEmpty()) tasks = it }
        }
    }
    LaunchedEffect(screen) {
        if (screen == Screen.APPROVALS && employeeRole in setOf("supervisor", "admin")) {
            runCatching { loadApprovals() }.onSuccess { approvals = it }
        }
        if (screen == Screen.FRESHNESS && !prefs.getString("token", null).isNullOrBlank()) {
            runCatching { loadFreshness() }.onSuccess { freshness = it }
        }
        if (screen == Screen.BATCH && !prefs.getString("token", null).isNullOrBlank()) {
            runCatching { loadServerTasks() }.onSuccess { if (it.isNotEmpty()) tasks = it }
        }
    }

    BackHandler(enabled = screen != Screen.LOGIN && screen != Screen.DASHBOARD) {
        screen = when (screen) {
            Screen.SCANNER, Screen.EXCEPTION, Screen.PACKING, Screen.SYNC -> Screen.BATCH
            Screen.BATCH -> Screen.DASHBOARD
            else -> Screen.DASHBOARD
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (screen != Screen.LOGIN && screen != Screen.SCANNER) {
                Header(title = when (screen) {
                    Screen.DASHBOARD -> "PickSure"
                    Screen.BATCH -> "Batch picking"
                    Screen.EXCEPTION -> "Item exception"
                    Screen.PACKING -> "Packing"
                    Screen.SYNC -> "Sync center"
                    Screen.APPROVALS -> "Supervisor approvals"
                    Screen.FRESHNESS -> "Freshness monitor"
                    else -> "PickSure"
                }, subtitle = employeeName, onHome = { screen = Screen.DASHBOARD })
            }
            when (screen) {
                Screen.LOGIN -> LoginScreen(email, { email = it }, password, { password = it }, message, busy,
                    onSignIn = {
                        scope.launch {
                            busy = true
                            message = null
                            runCatching { withContext(Dispatchers.IO) { api.login(LoginRequest(email.trim(), password)) } }
                                .onSuccess { login ->
                                    prefs.edit().putString("token", login.access_token).putString("employee_name", login.employee.name)
                                        .putString("employee_role", login.employee.role).apply()
                                    employeeName = login.employee.name
                                    employeeRole = login.employee.role
                                    runCatching { loadServerTasks() }.onSuccess { if (it.isNotEmpty()) tasks = it }
                                    runCatching { loadFreshness() }.onSuccess { freshness = it }
                                    if (employeeRole in setOf("supervisor", "admin")) runCatching { loadApprovals() }.onSuccess { approvals = it }
                                    SyncScheduler.schedule(context)
                                    screen = Screen.DASHBOARD
                                }.onFailure { message = "Could not sign in. Check your connection or continue offline." }
                            busy = false
                        }
                    },
                    onOffline = { message = null; employeeName = "Offline picker"; screen = Screen.DASHBOARD })
                Screen.DASHBOARD -> DashboardScreen(tasks, events, employeeName, employeeRole, freshness,
                    onStart = { message = null; screen = Screen.BATCH },
                    onPacking = { currentOrder = tasks.firstOrNull(); screen = Screen.PACKING },
                    onSync = { screen = Screen.SYNC },
                    onApprovals = { screen = Screen.APPROVALS },
                    onFreshness = { screen = Screen.FRESHNESS },
                    onLogout = { prefs.edit().remove("token").remove("employee_role").apply(); employeeName = "Picker"; employeeRole = "picker"; freshness = null; screen = Screen.LOGIN })
                Screen.BATCH -> BatchScreen(batchLines(), events, ::pickedCount, ::resolved, message,
                    onPick = { line ->
                        selectedItem = nextItem(line)
                        quantity = selectedItem?.let { (it.requested - pickedCount(it)).coerceAtLeast(1) } ?: 1
                        if (selectedItem != null) { scanStep = "PRODUCT"; message = null; screen = Screen.SCANNER }
                    },
                    onProblem = { line ->
                        val target = nextItem(line)
                        if (target != null) { selectedItem = target; exceptionReason = "Out of stock"; screen = Screen.EXCEPTION }
                    },
                    onPacking = { currentOrder = tasks.firstOrNull(); screen = Screen.PACKING },
                    onSync = { screen = Screen.SYNC })
                Screen.SCANNER -> {
                    val item = selectedItem
                    if (item == null) { screen = Screen.BATCH } else ScannerScreen(
                        item = item,
                        quantity = quantity,
                        maxQuantity = (item.requested - pickedCount(item)).coerceAtLeast(1),
                        message = message,
                        step = scanStep,
                        onQuantityChange = { quantity = it },
                        onBack = { message = null; screen = Screen.BATCH },
                        onBarcode = { scanned ->
                            scope.launch {
                                if (scanStep == "PRODUCT") {
                                    if (scanned != item.barcode) {
                                        message = "Wrong product. This order needs ${item.sku}. No pick was recorded."
                                    } else {
                                        val token = prefs.getString("token", null)
                                        val remoteCheck = if (token.isNullOrBlank()) null else runCatching {
                                            withContext(Dispatchers.IO) { api.verifyScan(ScanVerifyRequest(item.id, scanned)) }
                                        }.getOrNull()
                                        if (remoteCheck != null && !remoteCheck.valid) message = remoteCheck.message
                                        else if (item.recommendedLotId.isNullOrBlank() || item.lotTag.isNullOrBlank()) {
                                            message = "No safe lot is available. Report this item to the supervisor."
                                        } else {
                                            message = null
                                            scanStep = "LOT"
                                        }
                                    }
                                } else {
                                    if (scanned != item.lotTag) {
                                        message = "Lot label mismatch. Scan ${item.lotCode ?: "the recommended lot"}; no pick was recorded."
                                    } else {
                                        val token = prefs.getString("token", null)
                                        val remoteCheck = if (token.isNullOrBlank()) null else runCatching {
                                            withContext(Dispatchers.IO) { api.verifyLot(LotVerifyRequest(item.id, item.recommendedLotId!!, scanned)) }
                                        }.getOrNull()
                                        if (remoteCheck != null && !remoteCheck.valid) message = remoteCheck.message
                                        else {
                                            repository.record("PICK", item.taskId, item.id, item.sku, item.barcode, quantity,
                                                lotId = item.recommendedLotId, lotTag = scanned)
                                            message = "${item.lotCode} verified · expires ${item.expiresOn}. Saved locally; server confirmation is pending."
                                            screen = Screen.BATCH
                                        }
                                    }
                                }
                            }
                        })
                }
                Screen.EXCEPTION -> ExceptionScreen(selectedItem, exceptionReason, { exceptionReason = it }, replacementSku,
                    { replacementSku = it }, onBack = { screen = Screen.BATCH },
                    onUnavailable = {
                        val item = selectedItem ?: return@ExceptionScreen
                        scope.launch { repository.record("EXCEPTION", item.taskId, item.id, item.sku, reason = exceptionReason); message = "Unavailable item saved locally. Server confirmation is pending."; screen = Screen.BATCH }
                    },
                    onSubstitute = {
                        val item = selectedItem ?: return@ExceptionScreen
                        scope.launch { repository.record("SUBSTITUTION", item.taskId, item.id, item.sku,
                            reason = exceptionReason, replacementSku = replacementSku, approvalStatus = "pending"); message = "Substitution request saved locally. Supervisor approval is still required."; screen = Screen.BATCH }
                    })
                Screen.PACKING -> PackingScreen(tasks, events, ::pickedCount, ::resolved, currentOrder,
                    onSelect = { currentOrder = it }, onBack = { screen = Screen.BATCH },
                    onPack = { order ->
                        scope.launch {
                            repository.record("PACK", order.id)
                            message = "Packing saved on this device. Server confirmation is pending."
                        }
                    }, message = message)
                Screen.SYNC -> SyncScreen(events, onBack = { screen = Screen.BATCH }, onRetry = {
                    scope.launch { repository.retry(); message = "Retry queued when network is available." }
                }, message = message)
                Screen.APPROVALS -> ApprovalScreen(approvals, onBack = { screen = Screen.DASHBOARD }, onDecision = { row, decision ->
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { api.decideSubstitution(row.id, ApprovalRequest(decision)) } }
                            .onSuccess {
                                message = "Substitution ${decision.lowercase()}."
                                runCatching { loadApprovals() }.onSuccess { approvals = it }
                                runCatching { loadServerTasks() }.onSuccess { if (it.isNotEmpty()) tasks = it }
                            }.onFailure { message = "Could not submit the approval decision." }
                    }
                }, message = message)
                Screen.FRESHNESS -> FreshnessScreen(freshness, onBack = { screen = Screen.DASHBOARD }, onRefresh = {
                    scope.launch { runCatching { loadFreshness() }.onSuccess { freshness = it } }
                })
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String, onHome: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Forest).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
        }
        TextButton(onClick = onHome) { Text("Home", color = Color.White) }
    }
}

@Composable
private fun LoginScreen(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit,
                        message: String?, busy: Boolean, onSignIn: () -> Unit, onOffline: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        Text("PICKSURE · FRESHNESS FIRST", color = Forest, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(10.dp))
        Text("Freshness,\nfrom shelf to doorstep.", fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Verify the product and its expiry lot before fulfillment. Picks stay on this device when the network drops.", color = Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = email, onValueChange = onEmail, label = { Text("Work email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = password, onValueChange = onPassword, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (message != null) { Spacer(Modifier.height(10.dp)); Text(message, color = Red) }
        Spacer(Modifier.height(18.dp))
        Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            else Text("Sign in", fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onOffline, modifier = Modifier.fillMaxWidth()) { Text("Continue in offline demo") }
        Text("Demo picker: picker@picksure.demo  ·  PickSure123!", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DashboardScreen(tasks: List<OrderTask>, events: List<PickEventEntity>, employee: String, role: String,
                            freshness: FreshnessSummaryDto?,
                            onStart: () -> Unit, onPacking: () -> Unit, onSync: () -> Unit,
                            onApprovals: () -> Unit, onFreshness: () -> Unit, onLogout: () -> Unit) {
    val pending = events.count { it.syncStatus == "PENDING" || it.syncStatus == "SYNCING" }
    val attention = events.count { it.syncStatus == "NEEDS_ATTENTION" }
    val done = tasks.sumOf { task -> task.items.count { item ->
        pickedProgress(item, events) >= item.requested
    } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Good shift, $employee", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Central Dark Store · freshness-first fulfillment", color = Muted)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Assigned", tasks.size.toString(), Modifier.weight(1f))
                MetricCard("Lines picked", done.toString(), Modifier.weight(1f))
                MetricCard("To sync", pending.toString(), Modifier.weight(1f), if (attention > 0) Orange else Forest)
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onFreshness),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEE8)), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FRESHNESS FIRST", color = ForestDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Scan product + lot. Send the freshest eligible stock.", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(freshness?.let { "${it.near_expiry_units} units held back · ${it.minimum_shelf_life_days}-day minimum shelf life" }
                            ?: "FEFO checks help prevent short-dated deliveries; held stock can be reviewed for markdown or rescue.", color = Muted, fontSize = 12.sp)
                    }
                    Text("›", color = Forest, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = ForestDark), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Text("BATCH PICK", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("One shelf route. ${tasks.size} orders.", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text("Products are grouped by aisle, rack and shelf.", color = Color.White.copy(alpha = .78f))
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForestDark)) { Text("Start batch") }
                }
            }
        }
        item { Text("Assigned orders", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
        items(tasks) { task ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.orderId.replace("order-", "Order #"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("${task.items.size} lines · ${task.priority.uppercase()} priority", color = Muted, fontSize = 13.sp)
                    }
                    Text("${task.items.count { item -> pickedProgress(item, events) >= item.requested }}/${task.items.size}", color = Forest, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onPacking, modifier = Modifier.weight(1f)) { Text("Packing") }
                OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f)) { Text(if (attention > 0) "Sync · $attention" else "Sync center") }
            }
            if (role in setOf("supervisor", "admin")) {
                OutlinedButton(onClick = onApprovals, modifier = Modifier.fillMaxWidth()) { Text("Substitution approvals · review") }
            }
            OutlinedButton(onClick = onFreshness, modifier = Modifier.fillMaxWidth()) { Text("Freshness monitor") }
            TextButton(onClick = onLogout) { Text("Sign out") }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Forest) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(13.dp)) {
            Text(value, color = valueColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BatchScreen(lines: List<BatchLine>, events: List<PickEventEntity>, pickedCount: (PickableItem) -> Int,
                        isResolved: (PickableItem) -> Boolean,
                        feedback: String?,
                        onPick: (BatchLine) -> Unit, onProblem: (BatchLine) -> Unit, onPacking: () -> Unit, onSync: () -> Unit) {
    val allItems = lines.flatMap { it.items }
    val picked = allItems.sumOf { pickedCount(it).coerceAtMost(it.requested) }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Follow the shelf route", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("${picked}/${allItems.sumOf { it.requested }} units recorded · grouped by location", color = Muted)
            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEE8)), shape = RoundedCornerShape(12.dp)) {
                Text("Aisle → rack → shelf", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = ForestDark, fontWeight = FontWeight.SemiBold)
            }
            if (feedback != null) {
                Spacer(Modifier.height(6.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE5EEE8)), shape = RoundedCornerShape(12.dp)) {
                    Text(feedback, Modifier.padding(14.dp), color = ForestDark, fontWeight = FontWeight.Medium)
                }
            }
        }
        items(lines) { line ->
            val linePicked = line.items.sumOf { pickedCount(it).coerceAtMost(it.requested) }
            val total = line.items.sumOf { it.requested }
            val complete = line.items.all(isResolved)
            val hasSafeLot = line.lotStatus == "ready" && !line.recommendedLotId.isNullOrBlank() && !line.lotTag.isNullOrBlank()
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text("${line.sku} · ${line.aisle} / ${line.rack} / ${line.shelf}", color = Muted, fontSize = 12.sp)
                        }
                        Text("$linePicked/$total", color = if (complete) Forest else Orange, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    val freshnessColor = if (hasSafeLot) Forest else if (line.lotStatus == "near_expiry_only") Orange else Red
                    val freshnessBg = if (hasSafeLot) Color(0xFFE5EEE8) else if (line.lotStatus == "near_expiry_only") Color(0xFFFFF1DB) else Color(0xFFFFE7E5)
                    Card(colors = CardDefaults.cardColors(containerColor = freshnessBg), shape = RoundedCornerShape(11.dp)) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(if (hasSafeLot) "FEFO lot · ${line.lotCode}" else if (line.lotStatus == "near_expiry_only") "Held back · stock is too near expiry" else "No eligible stock found",
                                color = freshnessColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (hasSafeLot && line.expiresOn != null) Text("Expires ${line.expiresOn} · scan its QR after the product barcode", color = Muted, fontSize = 12.sp)
                            else if (line.lotStatus == "near_expiry_only") Text("Nothing below the store's minimum shelf-life policy can be dispatched.", color = Muted, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPick(line) }, enabled = !complete && hasSafeLot, modifier = Modifier.weight(1f)) { Text(if (complete) "Done" else if (hasSafeLot) "Scan + verify" else "Pick blocked") }
                        OutlinedButton(onClick = { onProblem(line) }, enabled = !complete, modifier = Modifier.weight(1f)) { Text("Report issue") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPacking, modifier = Modifier.weight(1f)) { Text("Packing checklist") }
                OutlinedButton(onClick = onSync, modifier = Modifier.weight(1f)) { Text("Sync status") }
            }
        }
    }
}

@Composable
private fun ScannerScreen(item: PickableItem, quantity: Int, maxQuantity: Int, message: String?, step: String, onQuantityChange: (Int) -> Unit,
                          onBack: () -> Unit, onBarcode: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Freshness check · ${if (step == "PRODUCT") "1 / 2" else "2 / 2"}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(if (step == "PRODUCT") "First scan the product barcode · ${item.sku}" else "Product verified · now scan lot ${item.lotCode}", color = Muted, fontSize = 13.sp)
        if (step == "LOT") {
            Text("Expiry ${item.expiresOn ?: "unknown"} · safe lot selected by earliest expiry", color = Forest, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick quantity", modifier = Modifier.weight(1f), color = Muted)
            TextButton(onClick = { if (quantity > 1) onQuantityChange(quantity - 1) }) { Text("−") }
            Text("$quantity", fontWeight = FontWeight.Bold)
            TextButton(onClick = { if (quantity < maxQuantity) onQuantityChange(quantity + 1) }) { Text("+") }
        }
        Spacer(Modifier.height(8.dp))
        key(step) { BarcodeCamera(onBarcode = onBarcode, modifier = Modifier.weight(1f).fillMaxWidth()) }
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE7E5)), shape = RoundedCornerShape(12.dp)) {
                Text(message, Modifier.padding(14.dp), color = Red, fontWeight = FontWeight.Medium)
            }
        } else {
            Text(if (step == "PRODUCT") "Center the product barcode in the camera frame." else "Center the lot QR label in the camera frame.", Modifier.padding(vertical = 10.dp), color = Muted)
        }
    }
}

@Composable
private fun ExceptionScreen(item: PickableItem?, reason: String, onReason: (String) -> Unit, replacementSku: String,
                            onReplacement: (String) -> Unit, onBack: () -> Unit, onUnavailable: () -> Unit, onSubstitute: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Resolve this line", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(item?.name ?: "Selected product", color = Muted)
        OutlinedTextField(value = reason, onValueChange = onReason, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
        Text("Suggested replacement", fontWeight = FontWeight.SemiBold, color = Ink)
        listOf("YOGURT-PLAIN-400" to "Plain Yogurt 400 g", "BUTTER-AMUL-100" to "Amul Butter 100 g").forEach { (sku, name) ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onReplacement(sku) }, shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (replacementSku == sku) Color(0xFFE5EEE8) else Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (replacementSku == sku) "●  " else "○  ", color = Forest)
                    Column { Text(name, fontWeight = FontWeight.SemiBold); Text(sku, color = Muted, fontSize = 12.sp) }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Button(onClick = onUnavailable, enabled = reason.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text("Mark unavailable") }
        OutlinedButton(onClick = onSubstitute, modifier = Modifier.fillMaxWidth()) { Text("Request substitute approval") }
        Text("Substitution remains pending until approved by a supervisor.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun PackingScreen(tasks: List<OrderTask>, events: List<PickEventEntity>, pickedCount: (PickableItem) -> Int,
                          isResolved: (PickableItem) -> Boolean,
                          selected: OrderTask?, onSelect: (OrderTask) -> Unit, onBack: () -> Unit, onPack: (OrderTask) -> Unit, message: String?) {
    val order = selected ?: tasks.firstOrNull()
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Verify each order before sealing", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("Order checklist is saved with an idempotent event.", color = Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tasks.forEach { task ->
                val active = task.id == order?.id
                OutlinedButton(onClick = { onSelect(task) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (active) Forest else Muted)) {
                    Text(task.orderId.replace("order-", "#"))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (order != null) {
            order.items.forEach { item ->
                val qty = pickedCount(item)
                val issue = item.serverStatus in setOf("unavailable", "substituted") || events.any {
                    it.taskItemId == item.id && it.eventType == "EXCEPTION" && it.syncStatus != "NEEDS_ATTENTION"
                }
                val awaitingApproval = item.serverStatus == "substitution_pending" || events.any {
                    it.taskItemId == item.id && it.eventType == "SUBSTITUTION" && it.syncStatus != "NEEDS_ATTENTION"
                }
                val pickedLotTag = events.lastOrNull {
                    it.taskItemId == item.id && it.eventType == "PICK" && it.syncStatus != "NEEDS_ATTENTION"
                }?.lotTag
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (qty >= item.requested || issue) "✓" else "○", color = if (qty >= item.requested || issue) Forest else Orange, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(if (item.serverStatus == "substitution_approved") "Replacement approved · scan it" else if (item.serverStatus == "substituted") "Replacement approved" else if (awaitingApproval) "Waiting for supervisor approval" else if (issue) "Unavailable item recorded" else "Picked $qty of ${item.requested}", color = Muted, fontSize = 12.sp)
                        if (qty > 0 && pickedLotTag != null) Text("Verified lot QR · $pickedLotTag", color = Forest, fontSize = 11.sp)
                    }
                }
                Divider(color = Color(0xFFE8ECE7))
            }
            val complete = order.items.all(isResolved)
            val alreadyPacked = events.any { it.taskId == order.id && it.eventType == "PACK" && it.syncStatus != "NEEDS_ATTENTION" }
            Spacer(Modifier.height(18.dp))
            Button(onClick = { onPack(order) }, enabled = complete && !alreadyPacked, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (alreadyPacked) "Packing saved" else if (complete) "Confirm packed" else "Resolve every line first")
            }
            if (message != null) Text(message, Modifier.padding(top = 10.dp), color = Forest, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to batch") }
    }
}

@Composable
private fun FreshnessScreen(summary: FreshnessSummaryDto?, onBack: () -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Keep short-dated stock out of customer bags", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("PickSure uses FEFO (first expiry, first out), then blocks any lot inside the store's minimum shelf-life window.", color = Muted)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh lot monitor") }
        }
        if (summary == null) {
            item { EmptyCard("Store snapshot not loaded", "Connect to the fulfillment API to view live lot counts. Previously cached task lots remain visible during picking.") }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = ForestDark), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("STORE POLICY", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Minimum ${summary.minimum_shelf_life_days} days of shelf life at dispatch", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Near-expiry stock stays in inventory but cannot be assigned to an order. Review it for markdown or rescue before expiry.", color = Color.White.copy(alpha = .78f), fontSize = 13.sp)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Dispatchable", summary.safe_units.toString(), Modifier.weight(1f), Forest)
                    MetricCard("Held back", summary.near_expiry_units.toString(), Modifier.weight(1f), Orange)
                    MetricCard("Expired", summary.expired_units.toString(), Modifier.weight(1f), Red)
                }
            }
            item { Text("Lot traceability", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
            if (summary.lots.isEmpty()) item { EmptyCard("No stock lots", "Inventory lots will appear when the store is seeded or replenished.") }
            items(summary.lots) { lot ->
                val statusColor = when (lot.status) { "safe" -> Forest; "near_expiry" -> Orange; else -> Red }
                val statusBg = when (lot.status) { "safe" -> Color(0xFFE5EEE8); "near_expiry" -> Color(0xFFFFF1DB); else -> Color(0xFFFFE7E5) }
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lot.product_name, fontWeight = FontWeight.Bold, color = Ink)
                                Text("${lot.sku} · ${lot.lot_code}", color = Muted, fontSize = 12.sp)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = statusBg), shape = RoundedCornerShape(20.dp)) {
                                Text(when (lot.status) { "safe" -> "DISPATCHABLE"; "near_expiry" -> "HELD BACK"; else -> "EXPIRED" },
                                    Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${lot.quantity} units · expires ${lot.expires_on} · ${lot.aisle}/${lot.rack}/${lot.shelf}", color = Muted, fontSize = 12.sp)
                        Text("Lot QR payload · ${lot.lot_tag}", color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
        item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to dashboard") } }
    }
}

@Composable
private fun ApprovalScreen(rows: List<SubstitutionDto>, onBack: () -> Unit,
                           onDecision: (SubstitutionDto, String) -> Unit, message: String?) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Review replacement requests", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Approvals update the item state and write an audit record.", color = Muted)
            if (message != null) Text(message, color = Forest, modifier = Modifier.padding(top = 8.dp))
        }
        if (rows.isEmpty()) item { EmptyCard("No pending approvals", "New picker requests appear here after they sync.") }
        items(rows) { row ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${row.original_sku}  →  ${row.replacement_sku}", fontWeight = FontWeight.Bold, color = Ink)
                    Text("Task line ${row.task_item_id}", color = Muted, fontSize = 12.sp)
                    if (!row.reason.isNullOrBlank()) Text("Reason: ${row.reason}", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onDecision(row, "approved") }, modifier = Modifier.weight(1f)) { Text("Approve") }
                        OutlinedButton(onClick = { onDecision(row, "rejected") }, modifier = Modifier.weight(1f)) { Text("Reject") }
                    }
                }
            }
        }
        item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") } }
    }
}

@Composable
private fun SyncScreen(events: List<PickEventEntity>, onBack: () -> Unit, onRetry: () -> Unit, message: String?) {
    val pending = events.count { it.syncStatus == "PENDING" || it.syncStatus == "SYNCING" }
    val synced = events.count { it.syncStatus == "SYNCED" }
    val attention = events.count { it.syncStatus == "NEEDS_ATTENTION" }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Saved here ≠ confirmed by server", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Pending actions remain in Room until the server acknowledges each event.", color = Muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard("Pending", pending.toString(), Modifier.weight(1f), Orange)
                MetricCard("Confirmed", synced.toString(), Modifier.weight(1f), Forest)
                MetricCard("Needs review", attention.toString(), Modifier.weight(1f), Red)
            }
            if (events.any { it.syncStatus == "SYNCING" }) Text("Syncing events with the server…", color = ForestDark, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), enabled = pending > 0 || attention > 0) { Text("Retry pending events") }
            if (message != null) Text(message, color = Forest, modifier = Modifier.padding(top = 6.dp))
        }
        if (events.isEmpty()) item { EmptyCard("No local events yet", "Scans and exceptions will appear here.") }
        items(events.reversed()) { event ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(event.eventType, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(event.syncStatus)
                    }
                    Text(event.sku ?: event.taskId, color = Muted, fontSize = 13.sp)
                    if (event.lotTag != null) Text("Lot QR · ${event.lotTag}", color = Forest, fontSize = 11.sp)
                    Text("Event ${event.eventUuid.take(8)} · ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.createdAt))}", color = Muted, fontSize = 11.sp)
                    if (event.lastError != null) Text(event.lastError, color = Red, fontSize = 12.sp)
                }
            }
        }
        item { TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") } }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) { "SYNCED" -> Forest; "NEEDS_ATTENTION" -> Red; "SYNCING" -> ForestDark; else -> Orange }
    Text(when (status) { "SYNCED" -> "SERVER CONFIRMED"; "NEEDS_ATTENTION" -> "NEEDS REVIEW"; "SYNCING" -> "SYNCING"; else -> "SAVED LOCALLY" },
        color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyCard(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, color = Muted) }
    }
}
