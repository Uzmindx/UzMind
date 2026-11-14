package uz.uzmind.malumotlarim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

private val Primary = Color(0xFF1E3A8A)
private val Accent = Color(0xFF3B82F6)

private val Categories = listOf("Umumiy", "Ish", "Shaxsiy", "Moliya", "Muhim")

private fun catColor(c: String) = when (c) {
    "Ish" -> Color(0xFF2563EB)
    "Shaxsiy" -> Color(0xFF16A34A)
    "Moliya" -> Color(0xFFD97706)
    "Muhim" -> Color(0xFFDC2626)
    else -> Color(0xFF64748B)
}

data class Note(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Umumiy",
    val pinned: Boolean = false,
    val updatedAt: Timestamp? = null,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Primary, secondary = Accent)
            ) { App() }
        }
    }
}

@Composable
fun App() {
    val auth = remember { FirebaseAuth.getInstance() }
    var uid by remember { mutableStateOf(auth.currentUser?.uid) }
    DisposableEffect(Unit) {
        val l = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(l)
        onDispose { auth.removeAuthStateListener(l) }
    }
    if (uid == null) AuthScreen() else NotesScreen(uid!!)
}

private fun authError(e: Exception?): String {
    val code = (e as? FirebaseAuthException)?.errorCode ?: ""
    return when (code) {
        "ERROR_INVALID_EMAIL" -> "Email noto'g'ri kiritilgan"
        "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> "Email yoki parol noto'g'ri"
        "ERROR_USER_NOT_FOUND" -> "Bunday foydalanuvchi topilmadi"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Bu email allaqachon ro'yxatdan o'tgan"
        "ERROR_WEAK_PASSWORD" -> "Parol kamida 6 belgidan iborat bo'lsin"
        "ERROR_TOO_MANY_REQUESTS" -> "Juda ko'p urinish. Birozdan keyin qayta urinib ko'ring"
        else -> e?.localizedMessage ?: "Xatolik yuz berdi"
    }
}

@Composable
fun AuthScreen() {
    val auth = remember { FirebaseAuth.getInstance() }
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Primary).systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Storage, null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text("Ma'lumotlarim", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Ma'lumotlaringiz bulutda xavfsiz saqlanadi", color = Color(0xFFBFDBFE), fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (isLogin) "Kirish" else "Ro'yxatdan o'tish",
                        fontSize = 22.sp, fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        email, { email = it.trim() }, label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        pass, { pass = it }, label = { Text("Parol") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton({ showPass = !showPass }) {
                                Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    msg?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = if (it.startsWith("✓")) Color(0xFF16A34A) else Color(0xFFDC2626), fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (email.isBlank() || pass.isBlank()) { msg = "Email va parolni kiriting"; return@Button }
                            loading = true; msg = null
                            val task = if (isLogin) auth.signInWithEmailAndPassword(email, pass)
                            else auth.createUserWithEmailAndPassword(email, pass)
                            task.addOnCompleteListener { t ->
                                loading = false
                                if (!t.isSuccessful) msg = authError(t.exception)
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (isLogin) "Kirish" else "Ro'yxatdan o'tish", fontSize = 16.sp)
                    }
                    TextButton({ isLogin = !isLogin; msg = null }, Modifier.fillMaxWidth()) {
                        Text(if (isLogin) "Hisobingiz yo'qmi? Ro'yxatdan o'ting" else "Hisobingiz bormi? Kiring")
                    }
                    if (isLogin) TextButton({
                        if (email.isBlank()) { msg = "Avval emailni kiriting"; return@TextButton }
                        auth.sendPasswordResetEmail(email).addOnCompleteListener { t ->
                            msg = if (t.isSuccessful) "✓ Parolni tiklash havolasi emailingizga yuborildi" else authError(t.exception)
                        }
                    }, Modifier.fillMaxWidth()) { Text("Parolni unutdingizmi?", color = Color.Gray) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(uid: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    val col = remember(uid) { db.collection("users").document(uid).collection("notes") }
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Note?>(null) }
    var toDelete by remember { mutableStateOf<Note?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    DisposableEffect(uid) {
        val reg: ListenerRegistration = col.orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    notes = snap.documents.map { d ->
                        Note(
                            id = d.id,
                            title = d.getString("title") ?: "",
                            content = d.getString("content") ?: "",
                            category = d.getString("category") ?: "Umumiy",
                            pinned = d.getBoolean("pinned") ?: false,
                            updatedAt = d.getTimestamp("updatedAt")
                        )
                    }
                    loaded = true
                }
            }
        onDispose { reg.remove() }
    }

    editing?.let { n ->
        EditScreen(n, onClose = { editing = null }, onSave = { title, content, cat, pinned ->
            val data = hashMapOf<String, Any>(
                "title" to title, "content" to content, "category" to cat,
                "pinned" to pinned, "updatedAt" to Timestamp.now()
            )
            if (n.id.isEmpty()) col.add(data) else col.document(n.id).set(data)
            editing = null
        })
        return
    }

    val shown = notes
        .filter { filter == null || it.category == filter }
        .filter {
            search.isBlank() || it.title.contains(search, true) || it.content.contains(search, true)
        }
        .sortedByDescending { it.pinned }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ma'lumotlarim", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = Color.White, actionIconContentColor = Color.White
                ),
                actions = {
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Default.AccountCircle, "Hisob") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(FirebaseAuth.getInstance().currentUser?.email ?: "", fontSize = 13.sp) },
                                onClick = {}, enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text("Jami yozuvlar: ${notes.size}") }, onClick = {}, enabled = false
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Chiqish") },
                                leadingIcon = { Icon(Icons.Default.Logout, null) },
                                onClick = { menu = false; confirmLogout = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Note(category = filter ?: "Umumiy") },
                containerColor = Accent, contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Yangi yozuv") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                search, { search = it },
                placeholder = { Text("Qidirish...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, null) } },
                singleLine = true, shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScrollCompat().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(filter == null, { filter = null }, label = { Text("Hammasi") })
                Categories.forEach { c ->
                    FilterChip(filter == c, { filter = if (filter == c) null else c }, label = { Text(c) })
                }
            }
            when {
                !loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                shown.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inbox, null, tint = Color.LightGray, modifier = Modifier.size(72.dp))
                        Text(
                            if (notes.isEmpty()) "Hali yozuv yo'q.\n\"Yangi yozuv\" tugmasini bosing"
                            else "Hech narsa topilmadi",
                            color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { n ->
                        NoteCard(n,
                            onClick = { editing = n },
                            onPin = { col.document(n.id).update("pinned", !n.pinned) },
                            onDelete = { toDelete = n })
                    }
                }
            }
        }
    }

    toDelete?.let { n ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("O'chirilsinmi?") },
            text = { Text("\"${n.title.ifBlank { "Nomsiz" }}\" yozuvi butunlay o'chiriladi.") },
            confirmButton = {
                TextButton({ col.document(n.id).delete(); toDelete = null }) { Text("O'chirish", color = Color(0xFFDC2626)) }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Bekor qilish") } }
        )
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Hisobdan chiqish") },
        text = { Text("Ma'lumotlaringiz bulutda saqlanib qoladi. Qayta kirganingizda ko'rasiz.") },
        confirmButton = { TextButton({ confirmLogout = false; FirebaseAuth.getInstance().signOut() }) { Text("Chiqish") } },
        dismissButton = { TextButton({ confirmLogout = false }) { Text("Bekor qilish") } }
    )
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

private val dateFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@Composable
fun NoteCard(n: Note, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(catColor(n.category)))
            Column(Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.title.ifBlank { "Nomsiz" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    IconButton(onPin, Modifier.size(32.dp)) {
                        Icon(
                            if (n.pinned) Icons.Default.PushPin else Icons.Outlined.PushPin, "Qadash",
                            tint = if (n.pinned) Accent else Color.LightGray, modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onDelete, Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, "O'chirish", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
                if (n.content.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(n.content, color = Color(0xFF475569), maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        n.category, color = catColor(n.category), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.background(catColor(n.category).copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    n.updatedAt?.let { Text(dateFmt.format(it.toDate()), color = Color.Gray, fontSize = 12.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(note: Note, onClose: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var cat by remember { mutableStateOf(note.category) }
    var pinned by remember { mutableStateOf(note.pinned) }
    val changed = title != note.title || content != note.content || cat != note.category || pinned != note.pinned
    var askDiscard by remember { mutableStateOf(false) }
    val back = { if (changed && (title.isNotBlank() || content.isNotBlank())) askDiscard = true else onClose() }
    BackHandler { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note.id.isEmpty()) "Yangi yozuv" else "Tahrirlash") },
                navigationIcon = { IconButton(back) { Icon(Icons.Default.ArrowBack, "Orqaga") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton({ pinned = !pinned }) {
                        Icon(if (pinned) Icons.Default.PushPin else Icons.Outlined.PushPin, "Qadash")
                    }
                    IconButton(
                        onClick = { onSave(title.trim(), content.trim(), cat, pinned) },
                        enabled = title.isNotBlank() || content.isNotBlank()
                    ) { Icon(Icons.Default.Check, "Saqlash") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding().padding(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Categories.forEach { c ->
                    FilterChip(cat == c, { cat = c }, label = { Text(c) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                title, { title = it }, label = { Text("Sarlavha") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                content, { content = it }, label = { Text("Ma'lumot") },
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onSave(title.trim(), content.trim(), cat, pinned) },
                enabled = title.isNotBlank() || content.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
            ) { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Saqlash", fontSize = 16.sp) }
        }
    }
    if (askDiscard) AlertDialog(
        onDismissRequest = { askDiscard = false },
        title = { Text("O'zgarishlar saqlanmadi") },
        text = { Text("Saqlamasdan chiqasizmi?") },
        confirmButton = { TextButton({ askDiscard = false; onSave(title.trim(), content.trim(), cat, pinned) }) { Text("Saqlash") } },
        dismissButton = { TextButton({ askDiscard = false; onClose() }) { Text("Saqlamasdan chiqish") } }
    )
}
