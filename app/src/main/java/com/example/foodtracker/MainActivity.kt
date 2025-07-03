package com.example.foodtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.example.foodtracker.data.AppDatabase
import com.example.foodtracker.data.FoodRepository
import java.time.format.DateTimeFormatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import android.app.DatePickerDialog
import java.time.LocalDate
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.rememberDrawerState
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.material3.Divider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FoodTrackerTheme {
                val context = LocalContext.current
                val vm: FoodViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val db = AppDatabase.getInstance(context)
                        val repo = FoodRepository.create(db)
                        @Suppress("UNCHECKED_CAST")
                        return FoodViewModel(repo) as T
                    }
                })
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                    uri?.let {
                        scope.launch {
                            val csv = vm.exportCsv()
                            context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(csv) }
                            Toast.makeText(context, "Exported CSV", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let {
                        scope.launch {
                            val csv = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                            vm.importCsv(csv)
                            Toast.makeText(context, "Imported CSV", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                            Divider()
                            TextButton(onClick = { exportLauncher.launch("food_log.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
                            TextButton(onClick = { importLauncher.launch(arrayOf("text/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Import CSV") }
                        }
                    }
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        FoodScreen(vm, Modifier.padding(innerPadding), openDrawer = { scope.launch { drawerState.open() } })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FoodScreen(vm: FoodViewModel, modifier: Modifier = Modifier, openDrawer: () -> Unit) {
    val entries by vm.dayEntries.collectAsState()
    val recentNames by vm.recentNames.collectAsState()
    val popularNames by vm.popularNames.collectAsState()
    val date by vm.currentDate.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd") }

    var newFood by remember { mutableStateOf("") }

    val ctx = LocalContext.current

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDx = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDx += dragAmount
                    },
                    onDragEnd = {
                        val threshold = 50
                        if (totalDx > threshold) vm.prevDay() else if (totalDx < -threshold) vm.nextDay()
                        totalDx = 0f
                    },
                    onDragCancel = { totalDx = 0f }
                )
            }
            .padding(16.dp)
    ) {
        // reset to today when app resumes
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    vm.setDate(LocalDate.now())
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Top control row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, contentDescription = "Menu") }

            TextButton(onClick = {
                val c = date
                DatePickerDialog(
                    ctx,
                    { _, y, m, d -> vm.setDate(LocalDate.of(y, m + 1, d)) },
                    c.year,
                    c.monthValue - 1,
                    c.dayOfMonth
                ).show()
            }) {
                Text(text = formatter.format(date))
            }

            IconButton(onClick = { vm.setDate(LocalDate.now()) }) {
                Icon(Icons.Default.Today, contentDescription = "Today")
            }
        }
        Spacer(Modifier.height(8.dp))
        // Top half list
        val aggregated = remember(entries) {
            entries.groupBy { it.name }.mapValues { it.value.sumOf { e -> e.quantity } }
        }
        FlowRow(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            aggregated.entries.forEach { (name, qty) ->
                AssistChip(
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .heightIn(min = 56.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { vm.removeOne(name) }
                        ),
                    onClick = {},
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            if (qty > 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text("$qty", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Bottom input area
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newFood,
                onValueChange = { newFood = it },
                label = { Text("Food name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newFood.isNotBlank()) {
                        vm.addFood(newFood.trim())
                        newFood = ""
                    }
                },
                enabled = newFood.isNotBlank()
            ) { Text("Add") }
        }
        Spacer(Modifier.height(8.dp))
        // Quick add rows with labels
        Text("Recent", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentNames.forEach { food ->
                AssistChip(onClick = { vm.addFood(food) }, label = { Text(food) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Common", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            popularNames.forEach { food ->
                AssistChip(colors = AssistChipDefaults.assistChipColors(), onClick = { vm.addFood(food) }, label = { Text(food) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FoodTrackerTheme {
        FoodScreen(FoodViewModel(FoodRepository.create(AppDatabase.getInstance(LocalContext.current))), openDrawer = {})
    }
}