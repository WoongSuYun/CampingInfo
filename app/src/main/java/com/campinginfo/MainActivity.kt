@file:OptIn(ExperimentalMaterial3Api::class)

package com.campinginfo

import android.os.Bundle
import android.content.Intent
import android.widget.ImageView
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { CampingApp(CampsiteStore(applicationContext)) }
    }
}

private sealed interface Page { data object List : Page; data object Detail : Page; data object Map : Page; data object Form : Page }
private enum class VisitFilter { ALL, VISITED, NOT_VISITED }
private data class AddressSelection(val address: String, val latitude: Double?, val longitude: Double?)
private data class PlaceSearchResult(val id: String, val name: String, val address: String, val latitude: Double, val longitude: Double)
private fun detectRegion(address: String): String = when {
    address.contains("서울") -> "서울"
    address.contains("경기") -> "경기"
    address.contains("인천") -> "인천"
    address.contains("강원") -> "강원도"
    address.contains("충북") || address.contains("충남") || address.contains("대전") || address.contains("세종") -> "충청도"
    address.contains("전북") || address.contains("전남") || address.contains("광주") -> "전라도"
    address.contains("경북") || address.contains("경남") || address.contains("대구") || address.contains("부산") || address.contains("울산") -> "경상도"
    address.contains("제주") -> "제주도"
    else -> ""
}

@Composable private fun CampingApp(store: CampsiteStore) {
    var items by remember { mutableStateOf(store.load()) }; var page by remember { mutableStateOf<Page>(Page.List) }; var editing by remember { mutableStateOf<Campsite?>(null) }; var appTitle by remember { mutableStateOf(store.getAppTitle()) }
    val context = LocalContext.current
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { context.contentResolver.openOutputStream(it)?.use { output -> output.write(store.exportJson().toByteArray()) } } }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { restored -> context.contentResolver.openInputStream(restored)?.bufferedReader()?.use { reader -> store.importJson(reader.readText())?.let { items = it } } } }
    BackHandler(enabled = page != Page.List) { page = Page.List }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF356859), secondary = Color(0xFFC08B5C))) { Surface(Modifier.fillMaxSize()) {
        when (page) {
            Page.List -> CampsiteList(appTitle, { title -> appTitle = title; store.setAppTitle(title) }, items, { editing = null; page = Page.Form }, { editing = it; page = Page.Detail }, { target -> items = items.map { if (it.id == target.id) it.copy(favorite = !it.favorite) else it }; store.save(items) }, { backupLauncher.launch("HaWoongCamping-backup.json") }, { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) })
            Page.Detail -> editing?.let { item -> CampsiteDetail(item, { page = Page.List }, { page = Page.Form }, { page = Page.Map }, { items = items.filterNot { it.id == item.id }; store.save(items); editing = null; page = Page.List }, { target -> val updated = target.copy(favorite = !target.favorite); items = items.map { if (it.id == target.id) updated else it }; editing = updated; store.save(items) }) }
            Page.Map -> editing?.let { item -> CampsiteMapScreen(item, { page = Page.Detail }) }
            Page.Form -> CampsiteForm(editing, { page = Page.List }, { item -> items = if (editing == null) items + item else items.map { if (it.id == item.id) item else it }; store.save(items); page = Page.List }, { item -> items = items.filterNot { it.id == item.id }; store.save(items); page = Page.List })
        }
    } }
}

@Composable private fun CampsiteList(appTitle: String, onTitleChange: (String) -> Unit, items: List<Campsite>, onAdd: () -> Unit, onEdit: (Campsite) -> Unit, onFavorite: (Campsite) -> Unit, onBackup: () -> Unit, onRestore: () -> Unit) {
    var titleDialog by remember { mutableStateOf(false) }; var titleInput by remember(appTitle) { mutableStateOf(appTitle) }
    var region by remember { mutableStateOf("전체") }; var query by remember { mutableStateOf("") }; var filterOpen by remember { mutableStateOf(false) }
    var pet by remember { mutableStateOf(false) }; var fenced by remember { mutableStateOf(false) }; var bathroom by remember { mutableStateOf(false) }; var electricity by remember { mutableStateOf(false) }; var shower by remember { mutableStateOf(false) }; var parking by remember { mutableStateOf(false) }; var wifi by remember { mutableStateOf(false) }; var tarp by remember { mutableStateOf(false) }; var visit by remember { mutableStateOf(VisitFilter.ALL) }; var maxPrice by remember { mutableStateOf("") }
    val shown = items.filter { item -> val itemPrice = item.price.filter(Char::isDigit).toIntOrNull(); (region == "전체" || item.region == region) && (query.isBlank() || item.name.contains(query, true) || item.address.contains(query, true)) && (!pet || item.petFriendly) && (!fenced || item.fenced) && (!bathroom || item.privateBathroom) && (!electricity || item.electricity) && (!shower || item.shower) && (!parking || item.parking) && (!wifi || item.wifi) && (!tarp || item.hasTarp) && (visit == VisitFilter.ALL || (visit == VisitFilter.VISITED && item.visitCount > 0) || (visit == VisitFilter.NOT_VISITED && item.visitCount == 0)) && (maxPrice.toIntOrNull()?.let { itemPrice != null && itemPrice <= it } ?: true) }
    Scaffold(topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) { Text(appTitle, fontWeight = FontWeight.Bold); IconButton(onClick = { titleInput = appTitle; titleDialog = true }) { Icon(Icons.Default.Edit, "앱 이름 변경") } } }, actions = { TextButton(onClick = onBackup) { Icon(Icons.Default.FileDownload, "전체 백업"); Spacer(Modifier.width(3.dp)); Text("전체 백업") }; TextButton(onClick = onRestore) { Icon(Icons.Default.FileUpload, "백업 복원"); Spacer(Modifier.width(3.dp)); Text("백업 복원") } }) }, floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "캠핑장 추가") } }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(query, { query = it }, label = { Text("캠핑장명 또는 주소검색") }, singleLine = true, modifier = Modifier.weight(1.45f)); RegionTabs(region, { region = it }, Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { filterOpen = !filterOpen }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(6.dp)); Text(if (filterOpen) "필터 닫기" else "상세 필터") }
            if (filterOpen) FilterPanel(pet, { pet = it }, fenced, { fenced = it }, bathroom, { bathroom = it }, electricity, { electricity = it }, shower, { shower = it }, parking, { parking = it }, wifi, { wifi = it }, tarp, { tarp = it }, visit, { visit = it }, maxPrice, { maxPrice = it })
            Text("${shown.size}곳", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
            if (shown.isEmpty()) EmptyState(onAdd) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(shown, key = { it.id }) { CampsiteCard(it, { onEdit(it) }, { onFavorite(it) }) }; item { BuildInfoFooter() } }
        }
    }
    if (titleDialog) AlertDialog(onDismissRequest = { titleDialog = false }, title = { Text("앱 이름 변경") }, text = { OutlinedTextField(titleInput, { titleInput = it }, label = { Text("앱 이름") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val value = titleInput.trim(); if (value.isNotBlank()) onTitleChange(value); titleDialog = false }) { Text("저장") } }, dismissButton = { TextButton(onClick = { titleDialog = false }) { Text("취소") } })
}
@Composable private fun BuildInfoFooter() { Text("빌드 ${BuildConfig.VERSION_NAME} · 빌드번호 ${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_DATE}", modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable private fun FilterPanel(pet: Boolean, setPet: (Boolean) -> Unit, fenced: Boolean, setFenced: (Boolean) -> Unit, bathroom: Boolean, setBathroom: (Boolean) -> Unit, electricity: Boolean, setElectricity: (Boolean) -> Unit, shower: Boolean, setShower: (Boolean) -> Unit, parking: Boolean, setParking: (Boolean) -> Unit, wifi: Boolean, setWifi: (Boolean) -> Unit, tarp: Boolean, setTarp: (Boolean) -> Unit, visit: VisitFilter, setVisit: (VisitFilter) -> Unit, maxPrice: String, setMaxPrice: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) { Text("시설 조건", fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth()) { CheckWithModifier("반려동물 동반", pet, setPet, Modifier.weight(1f)); CheckWithModifier("울타리 있음", fenced, setFenced, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth()) { CheckWithModifier("개별 화장실", bathroom, setBathroom, Modifier.weight(1f)); CheckWithModifier("전기 사용 가능", electricity, setElectricity, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth()) { CheckWithModifier("샤워장 있음", shower, setShower, Modifier.weight(1f)); CheckWithModifier("주차 가능", parking, setParking, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth()) { CheckWithModifier("와이파이 있음", wifi, setWifi, Modifier.weight(1f)); CheckWithModifier("타프 있음", tarp, setTarp, Modifier.weight(1f)) }; Text("방문 여부", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(visit == VisitFilter.ALL, { setVisit(VisitFilter.ALL) }, label = { Text("모두") }); FilterChip(visit == VisitFilter.VISITED, { setVisit(VisitFilter.VISITED) }, label = { Text("방문함") }); FilterChip(visit == VisitFilter.NOT_VISITED, { setVisit(VisitFilter.NOT_VISITED) }, label = { Text("미방문") }) }; OutlinedTextField(maxPrice, setMaxPrice, label = { Text("최대 금액 (원)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
}

@Composable private fun Check(label: String, checked: Boolean, change: (Boolean) -> Unit) { CheckWithModifier(label, checked, change, Modifier) }
@Composable private fun CheckWithModifier(label: String, checked: Boolean, change: (Boolean) -> Unit, modifier: Modifier) { Row(modifier, verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, change); Text(label) } }
@Composable private fun RegionTabs(selected: String, select: (String) -> Unit, modifier: Modifier = Modifier) { RegionSelect(selected, Regions.all, select, modifier) }
@Composable private fun EmptyState(onAdd: () -> Unit) = Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("🏕️", style = MaterialTheme.typography.displayMedium); Spacer(Modifier.height(12.dp)); Text("조건에 맞는 캠핑장이 없습니다."); Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = onAdd) { Text("캠핑장 추가") } }
@Composable private fun CampingImageGallery(uris: List<String>, onClick: (String) -> Unit, onDelete: ((String) -> Unit)? = null) {
    if (uris.isEmpty()) Text("등록된 이미지 없음", color = MaterialTheme.colorScheme.onSurfaceVariant)
    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEach { uri ->
            Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AndroidView(factory = { ImageView(it) }, update = { view -> view.setImageURI(Uri.parse(uri)); view.scaleType = ImageView.ScaleType.CENTER_CROP }, modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium).clickable { onClick(uri) })
                onDelete?.let { delete -> TextButton(onClick = { delete(uri) }, contentPadding = PaddingValues(0.dp)) { Text("삭제") } }
            }
        }
    }
}
@Composable private fun ImagePreviewDialog(uri: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(20.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.End) {
                AndroidView(factory = { ImageView(it) }, update = { view -> view.setImageURI(Uri.parse(uri)); view.scaleType = ImageView.ScaleType.FIT_CENTER }, modifier = Modifier.fillMaxWidth().height(420.dp))
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    }
}
@Composable private fun CampsiteDetail(item: Campsite, onBack: () -> Unit, onEdit: () -> Unit, onLocation: () -> Unit, onDelete: () -> Unit, onFavorite: (Campsite) -> Unit) {
    var imagePreviewUri by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("캠핑장 정보") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { IconButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.Delete, "삭제") } }) }, bottomBar = { Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("이 캠핑장 수정하기") } }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
            item { Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            item { AssistChip(onClick = {}, label = { Text(item.region) }) }
            if (item.address.isNotBlank()) item { DetailRow("주소", item.address) }
            if (item.price.isNotBlank()) item { DetailRow("1박 가격", "${item.price}원") }
            if (item.phone.isNotBlank()) item { DetailRow("전화번호", item.phone) }
            if (item.checkInTime.isNotBlank() || item.checkOutTime.isNotBlank()) item { DetailRow("이용 시간", "입실 ${item.checkInTime.ifBlank { "-" }}  ·  퇴실 ${item.checkOutTime.ifBlank { "-" }}") }
            item { DetailRow("방문 횟수", "${item.visitCount}회") }
            item { DetailRow("조건", listOfNotNull(if (item.petFriendly) "반려동물 동반" else null, if (item.fenced) "울타리 있음" else null, if (item.privateBathroom) "개별 화장실" else null, if (item.electricity) "전기 사용 가능" else null, if (item.shower) "샤워장 있음" else null, if (item.parking) "주차 가능" else null, if (item.wifi) "와이파이 있음" else null, if (item.hasTarp) "타프 있음" else null).ifEmpty { listOf("등록된 조건 없음") }.joinToString(" · ")) }
            if (item.facilities.isNotBlank()) item { DetailRow("시설", item.facilities) }
            if (item.memo.isNotBlank()) item { DetailRow("메모", item.memo) }
            item { OutlinedButton(onClick = onLocation, modifier = Modifier.fillMaxWidth(), enabled = item.latitude != null && item.longitude != null) { Icon(Icons.Default.LocationOn, null); Spacer(Modifier.width(8.dp)); Text(if (item.latitude != null) "지도에서 위치 보기" else "저장된 위치 없음") } }
            item { OutlinedButton(onClick = { onFavorite(item) }, modifier = Modifier.fillMaxWidth()) { Icon(if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null); Spacer(Modifier.width(8.dp)); Text(if (item.favorite) "즐겨찾기 해제" else "즐겨찾기") } }
            item { CampingImageGallery(item.imageUris.ifEmpty { item.imageUri?.let { listOf(it) }.orEmpty() }, { uri -> imagePreviewUri = uri }) }
        }
    }
    imagePreviewUri?.let { uri -> ImagePreviewDialog(uri, { imagePreviewUri = null }) }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("캠핑장 삭제") }, text = { Text("${item.name}을(를) 삭제할까요?") }, confirmButton = { TextButton(onClick = { deleteConfirm = false; onDelete() }) { Text("삭제") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } })
}
@Composable private fun DetailRow(label: String, value: String) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.bodyLarge) } }
@Composable private fun TimePickerField(label: String, value: String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(if (value.isBlank()) label else "$label: $value") }
    if (open) {
        val state = rememberTimePickerState(value.substringBefore(":").toIntOrNull() ?: 12, value.substringAfter(":", "0").toIntOrNull() ?: 0, is24Hour = true)
        Dialog(onDismissRequest = { open = false }) { Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state = state); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton(onClick = { open = false }) { Text("취소") }; Button(onClick = { change("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text("확인") } } } } }
    }
}
@Composable private fun CampsiteMapScreen(item: Campsite, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("위치 보기") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) { MapPicker(item.latitude, item.longitude) }
    }
}
@Composable private fun CampsiteCard(item: Campsite, onClick: () -> Unit, onFavorite: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); AssistChip(onClick = {}, label = { Text(item.region) }) }; if (item.address.isNotBlank()) Text(item.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(buildString { if (item.price.isNotBlank()) append("${item.price}원  "); append("방문 ${item.visitCount}회") }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); if (item.checkInTime.isNotBlank() || item.checkOutTime.isNotBlank()) Text("입실 ${item.checkInTime.ifBlank { "-" }} · 퇴실 ${item.checkOutTime.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall); val tags = listOfNotNull(if (item.petFriendly) "반려동물" else null, if (item.fenced) "울타리" else null, if (item.privateBathroom) "개별화장실" else null, if (item.hasTarp) "타프" else null); if (tags.isNotEmpty()) Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = onFavorite) { Icon(if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "즐겨찾기", tint = if (item.favorite) Color(0xFFC64B5E) else MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun CampsiteForm(existing: Campsite?, onBack: () -> Unit, onSave: (Campsite) -> Unit, onDelete: (Campsite) -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }; var region by rememberSaveable { mutableStateOf(existing?.region ?: "경기") }; var address by rememberSaveable { mutableStateOf(existing?.address ?: "") }; var detailAddress by rememberSaveable { mutableStateOf("") }; var price by rememberSaveable { mutableStateOf(existing?.price ?: "") }; var phone by rememberSaveable { mutableStateOf(existing?.phone ?: "") }; var facilities by rememberSaveable { mutableStateOf(existing?.facilities ?: "") }; var memo by rememberSaveable { mutableStateOf(existing?.memo ?: "") }; var checkInTime by rememberSaveable { mutableStateOf(existing?.checkInTime ?: "") }; var checkOutTime by rememberSaveable { mutableStateOf(existing?.checkOutTime ?: "") }; var lat by rememberSaveable { mutableStateOf(existing?.latitude) }; var lng by rememberSaveable { mutableStateOf(existing?.longitude) }; var imageUris by rememberSaveable { mutableStateOf(existing?.imageUris?.ifEmpty { existing.imageUri?.let { listOf(it) }.orEmpty() } ?: emptyList()) }; var imagePreviewUri by rememberSaveable { mutableStateOf<String?>(null) }; var mapOpen by rememberSaveable { mutableStateOf(false) }; var addressDialog by rememberSaveable { mutableStateOf(false) }; var pet by rememberSaveable { mutableStateOf(existing?.petFriendly ?: false) }; var fenced by rememberSaveable { mutableStateOf(existing?.fenced ?: false) }; var bathroom by rememberSaveable { mutableStateOf(existing?.privateBathroom ?: false) }; var electricity by rememberSaveable { mutableStateOf(existing?.electricity ?: false) }; var shower by rememberSaveable { mutableStateOf(existing?.shower ?: false) }; var parking by rememberSaveable { mutableStateOf(existing?.parking ?: false) }; var wifi by rememberSaveable { mutableStateOf(existing?.wifi ?: false) }; var tarp by rememberSaveable { mutableStateOf(existing?.hasTarp ?: false) }; var visitCount by rememberSaveable { mutableStateOf(existing?.visitCount?.toString() ?: "0") }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> uris.forEach { selected -> runCatching { context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION) }; val value = selected.toString(); if (value !in imageUris) imageUris = imageUris + value } }
    BackHandler(enabled = mapOpen || addressDialog) { if (addressDialog) addressDialog = false else mapOpen = false }
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "캠핑장 추가" else "캠핑장 수정") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { if (existing != null) IconButton(onClick = { onDelete(existing) }) { Icon(Icons.Default.Delete, "삭제") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = { onSave(Campsite(id = existing?.id ?: System.currentTimeMillis(), name = name.trim().ifBlank { "새 캠핑장" }, region = region, address = listOf(address, detailAddress).filter { it.isNotBlank() }.joinToString(" "), price = price, phone = phone, facilities = facilities, memo = memo, latitude = lat, longitude = lng, favorite = existing?.favorite ?: false, petFriendly = pet, fenced = fenced, privateBathroom = bathroom, electricity = electricity, shower = shower, parking = parking, wifi = wifi, visitCount = visitCount.toIntOrNull()?.coerceAtLeast(0) ?: 0, imageUri = imageUris.firstOrNull(), imageUris = imageUris, hasTarp = tarp, checkInTime = checkInTime, checkOutTime = checkOutTime)) }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = true) { Text("저장하기") } } }) { padding ->
        if (mapOpen) Column(Modifier.padding(padding).padding(16.dp)) { MapPicker(lat, lng) } else LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)) {
            item { Text("기본 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }; item { Field("캠핑장 이름 *", name) { name = it } }; item { AddressSearch(address, { addressDialog = true }) }; item { Text("지역 자동 설정: $region", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }; item { Field("상세주소 (동/층/호수 또는 캠핑장 위치)", detailAddress) { detailAddress = it } }; item { Field("1박 가격 (원)", price, KeyboardType.Number) { price = it } }; item { Field("전화번호", phone, KeyboardType.Phone) { phone = it } }; item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Box(Modifier.weight(1f)) { TimePickerField("입실시간", checkInTime) { checkInTime = it } }; Box(Modifier.weight(1f)) { TimePickerField("퇴실시간", checkOutTime) { checkOutTime = it } } } }
            item { Text("캠핑장 조건", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }; item { Check("반려동물 동반 가능", pet) { pet = it } }; item { Check("울타리 있음", fenced) { fenced = it } }; item { Check("개별 화장실", bathroom) { bathroom = it } }; item { Check("전기 사용 가능", electricity) { electricity = it } }; item { Check("샤워장 있음", shower) { shower = it } }; item { Check("주차 가능", parking) { parking = it } }; item { Check("와이파이 있음", wifi) { wifi = it } }; item { Check("타프 있음", tarp) { tarp = it } }; item { Field("방문 횟수", visitCount, KeyboardType.Number) { visitCount = it.filter(Char::isDigit) } }
            item { Text("시설 및 메모", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp)) }; item { Field("시설 (예: 개수대, 샤워실)", facilities) { facilities = it } }; item { Field("메모", memo, singleLine = false) { memo = it } }; item { OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUris.isEmpty()) "캠핑장 이미지 추가" else "이미지 추가") } }; item { CampingImageGallery(imageUris, { uri -> imagePreviewUri = uri }, { uri -> imageUris = imageUris - uri }) }
        }
        if (addressDialog) AddressSearchDialog(onSelected = { selected -> address = selected.address; detectRegion(selected.address).takeIf { it.isNotBlank() }?.let { region = it }; if (selected.latitude != null && selected.longitude != null) { lat = selected.latitude; lng = selected.longitude; mapOpen = true }; addressDialog = false }, onDismiss = { addressDialog = false })
    }
    imagePreviewUri?.let { uri -> ImagePreviewDialog(uri, { imagePreviewUri = null }) }
}

@Composable private fun AddressSearch(value: String, open: () -> Unit) { OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text(if (value.isBlank()) "도로명주소 검색" else "주소: $value") } }

@Composable private fun AddressSearchDialog(onSelected: (AddressSelection) -> Unit, onDismiss: () -> Unit) {
    var placeQuery by remember { mutableStateOf("") }
    var places by remember { mutableStateOf(emptyList<PlaceSearchResult>()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun startPlaceSearch() {
        if (placeQuery.isBlank() || searching || BuildConfig.KAKAO_REST_KEY.isBlank()) return
        searching = true
        searchError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { searchPlaces(placeQuery) }
            searching = false
            result.fold({ places = it; if (it.isEmpty()) searchError = "검색 결과가 없습니다." }, { searchError = it.message ?: "장소 검색에 실패했습니다." })
        }
    }
    val html = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"/><style>html,body,#postcode{margin:0;width:100%;height:100%;}</style></head>
        <body><div id="postcode"></div><script src="https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js"></script><script>
        new daum.Postcode({oncomplete:function(data){AndroidBridge.postAddress(JSON.stringify({road:data.roadAddress,jibun:data.jibunAddress,building:data.buildingName,x:data.x,y:data.y}));}}).embed(document.getElementById('postcode'));
        </script></body></html>
    """.trimIndent()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("도로명주소 검색", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(placeQuery, { placeQuery = it }, label = { Text("상호명·아파트명·캠핑장명") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { startPlaceSearch() }))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { startPlaceSearch() }, enabled = placeQuery.isNotBlank() && !searching && BuildConfig.KAKAO_REST_KEY.isNotBlank()) { Text(if (searching) "검색 중" else "장소 검색") }
                }
                searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                if (places.isNotEmpty()) LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(places, key = { it.id }) { place -> Card(onClick = { onSelected(AddressSelection(place.address, place.latitude, place.longitude)); places = emptyList() }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(place.name, fontWeight = FontWeight.Bold); Text(place.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                } else AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun postAddress(payload: String) {
                                    val obj = JSONObject(payload)
                                    val road = obj.optString("road")
                                    val jibun = obj.optString("jibun")
                                    val building = obj.optString("building")
                                    val base = if (road.isNotBlank()) road else jibun
                                    val selectedAddress = if (building.isNotBlank() && !base.contains(building)) "$base ($building)" else base
                                    post { onSelected(AddressSelection(selectedAddress, obj.optString("y").toDoubleOrNull(), obj.optString("x").toDoubleOrNull())) }
                                }
                            }, "AndroidBridge")
                            loadDataWithBaseURL("https://postcode.map.daum.net", html, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}
private fun searchPlaces(query: String): Result<List<PlaceSearchResult>> = runCatching {
    val variants = listOf(query.trim(), query.replace(" ", "").trim(), query.replace("캠핑장", "").trim()).filter { it.isNotBlank() }.distinct()
    val found = linkedMapOf<String, PlaceSearchResult>()
    variants.forEach { variant ->
        val urls = listOf(
            "https://dapi.kakao.com/v2/local/search/keyword.json?query=${Uri.encode(variant)}&size=15",
            "https://dapi.kakao.com/v2/local/search/address.json?query=${Uri.encode(variant)}&size=15"
        )
        urls.forEach { endpoint ->
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 10000; readTimeout = 10000
                setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_KEY}")
            }
            try {
                if (connection.responseCode in 200..299) {
                    val documents = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("documents") ?: return@forEach
                    for (index in 0 until documents.length()) {
                        val item = documents.getJSONObject(index)
                        val lat = item.optString("y").toDoubleOrNull() ?: continue
                        val lng = item.optString("x").toDoubleOrNull() ?: continue
                        val id = item.optString("id").ifBlank { "${lat}_${lng}_${item.optString("address_name")}" }
                        val name = item.optString("place_name").ifBlank { item.optString("address_name") }
                        val address = item.optString("road_address_name").ifBlank { item.optString("address_name") }
                        found.putIfAbsent(id, PlaceSearchResult(id, name, address, lat, lng))
                    }
                }
            } finally { connection.disconnect() }
        }
    }
    found.values.take(30)
}
@Composable private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
@Composable private fun RegionSelect(value: String, options: List<String> = Regions.editable, change: (String) -> Unit, modifier: Modifier = Modifier) { var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = !expanded }, modifier = modifier) { OutlinedTextField(value, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("지역") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded, { expanded = false }) { options.forEach { DropdownMenuItem({ Text(it) }, { change(it); expanded = false }) } } } }
