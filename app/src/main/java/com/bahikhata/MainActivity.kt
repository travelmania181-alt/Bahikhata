package com.bahikhata

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val Ink = Color(0xFF12352A)
private val Forest = Color(0xFF087F5B)
private val Mint = Color(0xFFF2F5F3)
private val Paper = Color(0xFFF2F5F3)
private val Coral = Color(0xFFB9573F)
private val Quiet = Color(0xFF71807A)
private val Line = Color(0xFFE2E8E2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Forest,
                    onPrimary = Color.White,
                    secondary = Coral,
                    background = Paper,
                    surface = Color.White,
                    onSurface = Ink,
                    onBackground = Ink,
                ),
            ) {
                LedgerScreen()
            }
        }
    }
}

@Composable
private fun LedgerScreen() {
    val context = LocalContext.current
    val entries = remember {
        mutableStateListOf<LedgerEntry>().apply {
            addAll(LedgerStore.readEntries(context).sortedByDescending { it.date })
        }
    }
    var account by remember { mutableStateOf(GoogleSignIn.getLastSignedInAccount(context)) }
    var showEntryDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<EntryType?>(null) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val workManager = WorkManager.getInstance(context)
        val workInfos = workManager.getWorkInfosForUniqueWorkLiveData(DriveSyncWorker.UNIQUE_WORK_NAME)
        val observer = Observer<List<WorkInfo>> { infos ->
            val work = infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull()
            connectionMessage = when (work?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
                    "Syncing workbook to Drive..."
                WorkInfo.State.SUCCEEDED -> "Workbook synced to Drive."
                WorkInfo.State.FAILED -> work.outputData.getString(DriveSyncWorker.ERROR_KEY)
                    ?: "Drive sync failed. Tap Sync to retry."
                WorkInfo.State.CANCELLED -> "Drive sync canceled. Tap Sync to retry."
                else -> connectionMessage
            }
        }
        workInfos.observe(lifecycleOwner, observer)
        onDispose { workInfos.removeObserver(observer) }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.data == null) {
            connectionMessage = "Google sign-in returned no account data (result ${result.resultCode})."
        } else {
            try {
                val signedIn = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account = signedIn
                connectionMessage = null
                DriveSyncWorker.enqueue(context)
            } catch (error: ApiException) {
                connectionMessage = "Google sign-in failed (status ${error.statusCode}). Check OAuth setup."
            }
        }
    }

    val month = YearMonth.now()
    val monthEntries = entries.filter { YearMonth.from(it.date) == month }
    val income = monthEntries.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor }
    val expenses = monthEntries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }
    val visibleEntries = entries.filter { filter == null || it.type == filter }

    Scaffold(
        containerColor = Paper,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showEntryDialog = true },
                containerColor = Forest,
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add transaction")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Forest, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "KhataBook logo",
                        modifier = Modifier.size(46.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("KhataBook", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("PERSONAL LEDGER", fontSize = 10.sp, letterSpacing = 1.3.sp, color = Quiet)
                }
                Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)).uppercase(),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Quiet)
            }

            Spacer(Modifier.height(28.dp))
            Text("THIS MONTH", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Quiet)
            Spacer(Modifier.height(5.dp))
            Text(formatMoney(income - expenses), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("Net balance", fontSize = 13.sp, color = Quiet)

            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryAmount(
                    label = "Income",
                    amount = income,
                    icon = Icons.Outlined.ArrowDownward,
                    tint = Forest,
                    modifier = Modifier.weight(1f),
                )
                SummaryAmount(
                    label = "Spent",
                    amount = expenses,
                    icon = Icons.Outlined.ArrowUpward,
                    tint = Coral,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            Divider(color = Line)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (account == null) Icons.Outlined.CloudUpload else Icons.Outlined.CloudDone,
                    contentDescription = null,
                    tint = if (account == null) Quiet else Forest,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (account == null) "Only on this device" else "Google Drive connected",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                    Text(
                        if (account == null) "Connect to create your Excel workbook" else "Workbook updates when online",
                        fontSize = 11.sp,
                        color = Quiet,
                    )
                }
                TextButton(
                    onClick = {
                        if (account == null) {
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(Scope(LedgerStore.driveScope()))
                                .build()
                            signInLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                        } else {
                            DriveSyncWorker.enqueue(context)
                        }
                    },
                ) {
                    Text(if (account == null) "Connect" else "Sync", color = Forest, fontWeight = FontWeight.SemiBold)
                }
            }
            connectionMessage?.let {
                Text(it, color = Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transactions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.weight(1f))
                Text("${entries.size}", fontSize = 12.sp, color = Quiet)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                FilterChip(selected = filter == EntryType.EXPENSE, onClick = { filter = EntryType.EXPENSE }, label = { Text("Expenses") })
                FilterChip(selected = filter == EntryType.INCOME, onClick = { filter = EntryType.INCOME }, label = { Text("Income") })
            }
            Spacer(Modifier.height(4.dp))
            if (visibleEntries.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No transactions yet", fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("Add your first entry to start the ledger.", fontSize = 13.sp, color = Quiet)
                }
            } else {
                visibleEntries.forEachIndexed { index, entry ->
                    TransactionRow(entry)
                    if (index != visibleEntries.lastIndex) Divider(color = Line, modifier = Modifier.padding(start = 48.dp))
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }

    if (showEntryDialog) {
        EntryDialog(
            onDismiss = { showEntryDialog = false },
            onSave = { entry ->
                val updated = (entries.toList() + entry).sortedByDescending { it.date }
                LedgerStore.saveEntries(context, updated)
                entries.clear()
                entries.addAll(updated)
                if (account != null) DriveSyncWorker.enqueue(context)
                showEntryDialog = false
            },
        )
    }
}

@Composable
private fun SummaryAmount(
    label: String,
    amount: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 12.sp, color = Quiet)
        }
        Spacer(Modifier.height(5.dp))
        Text(formatMoney(amount), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun TransactionRow(entry: LedgerEntry) {
    val isIncome = entry.type == EntryType.INCOME
    val tint = if (isIncome) Forest else Coral
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).background(if (isIncome) Mint else Color(0xFFF8E9E4), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isIncome) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.category, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(entry.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")), entry.note.takeIf { it.isNotBlank() })
                    .joinToString("  ·  "),
                fontSize = 11.sp,
                color = Quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (isIncome) "+" else "−") + formatMoney(entry.amountMinor),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun EntryDialog(
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
) {
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add transaction", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == EntryType.EXPENSE, onClick = { type = EntryType.EXPENSE }, label = { Text("Expense") })
                    FilterChip(selected = type == EntryType.INCOME, onClick = { type = EntryType.INCOME }, label = { Text("Income") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))
                }
                error?.let { Text(it, color = Coral, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = try {
                    amount.toBigDecimalOrNull()
                        ?.takeIf { it > java.math.BigDecimal.ZERO }
                        ?.movePointRight(2)
                        ?.setScale(0, RoundingMode.HALF_UP)
                        ?.longValueExact()
                } catch (_: ArithmeticException) {
                    null
                }
                if (parsed == null) {
                    error = "Enter an amount greater than zero."
                } else if (category.isBlank()) {
                    error = "Add a category."
                } else {
                    onSave(
                        LedgerEntry(
                            date = date,
                            type = type,
                            category = category.trim(),
                            amountMinor = parsed,
                            note = note.trim(),
                        ),
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = dateState)
        }
    }
}

private fun formatMoney(amountMinor: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    formatter.currency = Currency.getInstance("INR")
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = 2
    return formatter.format(amountMinor / 100.0)
}
