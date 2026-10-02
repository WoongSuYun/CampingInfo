@file:OptIn(ExperimentalMaterial3Api::class)

package com.campinginfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CampingApp(CampsiteStore(applicationContext)) }
    }
}

@Composable
private fun CampingApp(store: CampsiteStore) {
    var campsites by remember { mutableStateOf(store.load()) }
    var page by remember { mutableStateOf<Page>(Page.List) }
    var editing by remember { mutableStateOf<Campsite?>(null) }
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF356859), secondary = Color(0xFFC08B5C))) {
        Surface(Modifier.fillMaxSize()) {
            when (page) {
                Page.List -> CampsiteList(campsites, onAdd = { editing = null; page = Page.Form }, onEdit = { editing = it; page = Page.Form }, onFavorite = { target ->
                    campsites = campsites.map { if (it.id == target.id) it.copy(favorite = !it.favorite) else it }; store.save(campsites)
                })
                Page.Form -> CampsiteForm(editing, onBack = { page = Page.List }, onSave = { item ->
                    campsites = if (editing == null) campsites + item else campsites.map { if (it.id == item.id) item else it }
                    store.save(campsites); page = Page.List
                }, onDelete = { item -> campsites = campsites.filterNot { it.id == item.id }; store.save(campsites); page = Page.List })
            }
        }
    }
}

private sealed interface Page { data object List : Page; data object Form : Page }

@Composable
private fun CampsiteList(items: List<Campsite>, onAdd: () -> Unit, onEdit: (Campsite) -> Unit, onFavorite: (Campsite) -> Unit) {
    var region by remember { mutableStateOf("전체") }
    var query by remember { mutableStateOf("") }
    val shown = items.filter { (region == "전체" || it.region == region) && (it.name.contains(query, true) || it.address.contains(query, true)) }
    Scaffold(topBar = { TopAppBar(title = { Text("캠핑 노트", fontWeight = FontWeight.Bold) }) }, floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "캠핑장 추가") } }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Text("내가 저장한 캠핑장", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text("지역별로 정리하고, 다음 여행을 준비하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, { query = it }, label = { Text("이름 또는 주소 검색") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            RegionTabs(region, { region = it })
            Spacer(Modifier.height(12.dp))
            if (shown.isEmpty()) EmptyState(onAdd) else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 90.dp)) {
                items(shown, key = { it.id }) { CampsiteCard(it, { onEdit(it) }, { onFavorite(it) }) }
            }
        }
    }
}

@Composable private fun RegionTabs(selected: String, select: (String) -> Unit) {
    ScrollableTabRow(selectedTabIndex = Regions.all.indexOf(selected), edgePadding = 0.dp, divider = {}) {
        Regions.all.forEach { label -> Tab(selected = label == selected, onClick = { select(label) }, text = { Text(label) }) }
    }
}

@Composable private fun EmptyState(onAdd: () -> Unit) = Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text("🏕️", style = MaterialTheme.typography.displayMedium)
    Spacer(Modifier.height(12.dp)); Text("아직 저장한 캠핑장이 없어요", style = MaterialTheme.typography.titleMedium)
    Text("첫 번째 캠핑장을 기록해 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(16.dp)); OutlinedButton(onClick = onAdd) { Text("캠핑장 추가") }
}

@Composable private fun CampsiteCard(item: Campsite, onClick: () -> Unit, onFavorite: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); AssistChip(onClick = {}, label = { Text(item.region) }) }
            if (item.address.isNotBlank()) Text(item.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row { if (item.price.isNotBlank()) Text("₩ ${item.price}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); if (item.latitude != null) Text("  ·  지도 저장됨", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
        IconButton(onClick = onFavorite) { Icon(if (item.favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "즐겨찾기", tint = if (item.favorite) Color(0xFFC64B5E) else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun CampsiteForm(existing: Campsite?, onBack: () -> Unit, onSave: (Campsite) -> Unit, onDelete: (Campsite) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }; var region by remember { mutableStateOf(existing?.region ?: "경기") }
    var address by remember { mutableStateOf(existing?.address ?: "") }; var price by remember { mutableStateOf(existing?.price ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }; var facilities by remember { mutableStateOf(existing?.facilities ?: "") }; var memo by remember { mutableStateOf(existing?.memo ?: "") }
    var lat by remember { mutableStateOf(existing?.latitude) }; var lng by remember { mutableStateOf(existing?.longitude) }; var mapOpen by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (existing == null) "캠핑장 추가" else "캠핑장 수정") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "뒤로") } }, actions = { if (existing != null) IconButton(onClick = { onDelete(existing) }) { Icon(Icons.Default.Delete, "삭제") } }) }, bottomBar = { Surface(shadowElevation = 8.dp) { Button(onClick = { if (name.isNotBlank()) onSave(Campsite(existing?.id ?: System.currentTimeMillis(), name, region, address, price, phone, facilities, memo, lat, lng, existing?.favorite ?: false)) }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = name.isNotBlank()) { Text("저장하기") } } }) { padding ->
        if (mapOpen) Column(Modifier.padding(padding).padding(16.dp)) { MapPicker(lat, lng) { a, b -> lat = a; lng = b; mapOpen = false } }
        else LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)) {
            item { Text("기본 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Field("캠핑장 이름 *", name) { name = it } }
            item { RegionSelect(region) { region = it } }
            item { Field("주소", address) { address = it } }
            item { Field("1박 가격 (원)", price, KeyboardType.Number) { price = it } }
            item { Field("전화번호", phone, KeyboardType.Phone) { phone = it } }
            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("시설 및 메모", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { Field("시설 (예: 전기, 샤워실, 반려동물)", facilities) { facilities = it } }
            item { Field("메모", memo, singleLine = false) { memo = it } }
            item { OutlinedButton(onClick = { mapOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(if (lat == null) "카카오맵에서 위치 선택" else "위치 변경  (${"%.4f".format(lat)}, ${"%.4f".format(lng)})") } }
        }
    }
}

@Composable private fun Field(label: String, value: String, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, change: (String) -> Unit) = OutlinedTextField(value, change, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, minLines = if (singleLine) 1 else 3, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
@Composable private fun RegionSelect(value: String, change: (String) -> Unit) { var expanded by remember { mutableStateOf(false) }; ExposedDropdownMenuBox(expanded, { expanded = !expanded }) { OutlinedTextField(value, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("지역") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded, { expanded = false }) { Regions.editable.forEach { DropdownMenuItem({ Text(it) }, { change(it); expanded = false }) } } } }
