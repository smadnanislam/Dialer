@file:OptIn(ExperimentalFoundationApi::class)

package com.example.iphonedialer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val IosBlue = Color(0xFF007AFF)
private val IosGreen = Color(0xFF34C759)
private val IosRed = Color(0xFFFF3B30)
private val IosGray = Color(0xFF8E8E93)

@Composable
private fun fillColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFE9E9EB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent?.data?.schemeSpecificPart.orEmpty()
        setContent { DialerTheme { DialerApp(initial) } }
    }
}

@Composable
fun DialerTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme())
        darkColorScheme(background = Color.Black, surface = Color.Black,
            onBackground = Color.White, onSurface = Color.White, primary = IosBlue)
    else
        lightColorScheme(background = Color.White, surface = Color.White,
            onBackground = Color.Black, onSurface = Color.Black, primary = IosBlue)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun DialerApp(initialNumber: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(if (initialNumber.isNotEmpty()) 2 else 0) }
    var hasContacts by remember { mutableStateOf(false) }
    var hasLog by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf(emptyList<ContactItem>()) }
    var calls by remember { mutableStateOf(emptyList<CallItem>()) }

    fun refresh() {
        hasContacts = ctx.granted(Manifest.permission.READ_CONTACTS)
        hasLog = ctx.granted(Manifest.permission.READ_CALL_LOG)
        scope.launch {
            if (hasContacts) contacts = withContext(Dispatchers.IO) { loadContacts(ctx) }
            if (hasLog) calls = withContext(Dispatchers.IO) { loadCalls(ctx) }
        }
    }

    val perms = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.CALL_PHONE
    )
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }

    LaunchedEffect(Unit) {
        if (perms.any { !ctx.granted(it) }) launcher.launch(perms)
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "Recents" to Icons.Filled.Schedule,
        "Contacts" to Icons.Filled.Person,
        "Keypad" to Icons.Filled.Dialpad
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = IosBlue, selectedTextColor = IosBlue,
                            unselectedIconColor = IosGray, unselectedTextColor = IosGray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> if (hasLog) RecentsScreen(calls) { placeCall(ctx, it) }
                     else PermissionPrompt("Allow access to your call history to see Recents.") { launcher.launch(perms) }
                1 -> if (hasContacts) ContactsScreen(contacts) { placeCall(ctx, it) }
                     else PermissionPrompt("Allow access to your contacts to see them here.") { launcher.launch(perms) }
                else -> KeypadScreen(initialNumber) { placeCall(ctx, it) }
            }
        }
    }
}

@Composable
fun PermissionPrompt(message: String, onAllow: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        Arrangement.Center, Alignment.CenterHorizontally
    ) {
        Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAllow, colors = ButtonDefaults.buttonColors(containerColor = IosBlue)) {
            Text("Allow Access", color = Color.White)
        }
        TextButton(onClick = {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
            )
        }) { Text("Open Settings", color = IosBlue) }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(fillColor()).padding(2.dp)
    ) {
        options.forEachIndexed { i, t ->
            Text(
                t, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (i == selected) MaterialTheme.colorScheme.background else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val fmt = if (DateUtils.isToday(ms)) "h:mm a" else "dd/MM/yy"
    return SimpleDateFormat(fmt, Locale.getDefault()).format(Date(ms))
}

@Composable
fun RecentsScreen(calls: List<CallItem>, onCall: (String) -> Unit) {
    var missedOnly by remember { mutableStateOf(false) }
    val shown = if (missedOnly) calls.filter { it.isMissed } else calls
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recents", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Segmented(listOf("All", "Missed"), if (missedOnly) 1 else 0) { missedOnly = it == 1 }
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Recents", color = IosGray, fontSize = 20.sp) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown) { c ->
                    Column {
                        Row(
                            Modifier.fillMaxWidth().clickable { onCall(c.number) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when {
                                c.isMissed -> Icons.Filled.CallMissed
                                c.type == 2 -> Icons.Filled.CallMade
                                else -> Icons.Filled.CallReceived
                            }
                            Icon(icon, null, tint = if (c.isMissed) IosRed else IosGray, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    c.title, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (c.isMissed) IosRed else MaterialTheme.colorScheme.onBackground
                                )
                                if (c.name != null) Text(c.number, fontSize = 13.sp, color = IosGray)
                            }
                            Text(formatTime(c.date), fontSize = 14.sp, color = IosGray)
                        }
                        HorizontalDivider(Modifier.padding(start = 52.dp), thickness = 0.5.dp, color = fillColor())
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(contacts: List<ContactItem>, onCall: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val grouped = remember(contacts, query) {
        contacts
            .filter { query.isBlank() || it.name.contains(query, true) || it.number.contains(query) }
            .groupBy { it.name.first().uppercaseChar().takeIf { ch -> ch.isLetter() } ?: '#' }
            .toSortedMap(compareBy<Char> { if (it == '#') '\uFFFF' else it })
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Contacts", fontSize = 34.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        TextField(
            value = query, onValueChange = { query = it },
            placeholder = { Text("Search", color = IosGray) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = IosGray) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = fillColor(), unfocusedContainerColor = fillColor(),
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = IosBlue
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No Contacts", color = IosGray, fontSize = 20.sp) }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                grouped.forEach { (letter, list) ->
                    stickyHeader {
                        Text(
                            letter.toString(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = IosGray,
                            modifier = Modifier.fillMaxWidth().background(fillColor())
                                .padding(horizontal = 20.dp, vertical = 3.dp)
                        )
                    }
                    items(list) { c ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { onCall(c.number) }
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape).background(IosGray.copy(alpha = 0.35f)),
                                    Alignment.Center
                                ) {
                                    Text(
                                        c.name.trim().split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }
                                            .joinToString(""),
                                        fontSize = 15.sp, fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.number, fontSize = 13.sp, color = IosGray)
                                }
                            }
                            HorizontalDivider(Modifier.padding(start = 74.dp), thickness = 0.5.dp, color = fillColor())
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeyButton(label: String, sub: String, onClick: () -> Unit, onLong: (() -> Unit)? = null) {
    Column(
        Modifier.size(80.dp).clip(CircleShape).background(fillColor())
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        Arrangement.Center, Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 32.sp, fontWeight = FontWeight.Light)
        if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
fun KeypadScreen(initial: String, onCall: (String) -> Unit) {
    var number by remember { mutableStateOf(initial) }
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        Arrangement.Bottom, Alignment.CenterHorizontally
    ) {
        Text(
            number, fontSize = 38.sp, fontWeight = FontWeight.Light, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.height(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { (d, sub) ->
                    KeyButton(
                        d, sub, onClick = { number += d },
                        onLong = if (d == "0") ({ number += "+" }) else null
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(80.dp))
            Box(
                Modifier.size(80.dp).clip(CircleShape).background(IosGreen).clickable { onCall(number) },
                Alignment.Center
            ) { Icon(Icons.Filled.Call, "Call", tint = Color.White, modifier = Modifier.size(34.dp)) }
            Box(
                Modifier.size(80.dp).clip(CircleShape).combinedClickable(
                    enabled = number.isNotEmpty(),
                    onClick = { number = number.dropLast(1) },
                    onLongClick = { number = "" }
                ),
                Alignment.Center
            ) {
                if (number.isNotEmpty()) Icon(Icons.Filled.Backspace, "Delete", tint = IosGray, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
