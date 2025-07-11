package com.example.foodtracker

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.foodtracker.data.AppDatabase
import com.example.foodtracker.data.FoodRepository
import com.example.foodtracker.ui.theme.FoodTrackerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.animation.ExperimentalAnimationApi

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Load saved preference for Hebrew mode
        val prefs = getPreferences(MODE_PRIVATE)
        val initialHebrew = prefs.getBoolean("hebrew_mode", false)

        setContent {
            val isHebrew = remember { mutableStateOf(initialHebrew) }

            CompositionLocalProvider(LocalLayoutDirection provides if (isHebrew.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                FoodTrackerTheme {
                    val context = LocalContext.current
                    val keyboardController = LocalSoftwareKeyboardController.current
                    val focusManager = LocalFocusManager.current
                    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                    LaunchedEffect(imeVisible) {
                        if (!imeVisible) {
                            focusManager.clearFocus(force = true)
                        }
                    }
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
                    val layoutDirection = LocalLayoutDirection.current
                    val isRtlLayout = layoutDirection == LayoutDirection.Rtl

                    val exportLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                            uri?.let {
                                scope.launch {
                                    val csv = vm.exportCsv()
                                    context.contentResolver.openOutputStream(it)?.bufferedWriter()
                                        ?.use { writer -> writer.write(csv) }
                                    Toast.makeText(context, "Exported CSV", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }

                    val importLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                            uri?.let {
                                scope.launch {
                                    val csv = context.contentResolver.openInputStream(it)
                                        ?.bufferedReader()?.readText() ?: ""
                                    vm.importCsv(csv)
                                    Toast.makeText(context, "Imported CSV", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        }

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        drawerContent = {
                            ModalDrawerSheet {
                                Text(
                                    if (isHebrew.value) "תפריט" else "Menu",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                HorizontalDivider()
                                TextButton(
                                    onClick = { exportLauncher.launch("food_log.csv") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isHebrew.value) "יצוא CSV" else "Export CSV")
                                }
                                TextButton(
                                    onClick = { importLauncher.launch(arrayOf("text/*")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isHebrew.value) "יבוא CSV" else "Import CSV")
                                }

                                // RTL toggle
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("עברית", modifier = Modifier.weight(1f))
                                    Switch(checked = isHebrew.value, onCheckedChange = {
                                        isHebrew.value = it
                                        prefs.edit().putBoolean("hebrew_mode", it).apply()
                                    })
                                }
                            }
                        }
                    ) {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            FoodScreen(
                                vm,
                                Modifier.padding(innerPadding),
                                openDrawer = { scope.launch { drawerState.open() } },
                                isHebrew = isHebrew.value
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun FoodScreen(
    vm: FoodViewModel,
    modifier: Modifier = Modifier,
    openDrawer: () -> Unit,
    isHebrew: Boolean = false
) {
    val entries by vm.dayEntries.collectAsState()
    val recentNames by vm.recentNames.collectAsState()
    val popularNames by vm.popularNames.collectAsState()
    val date by vm.currentDate.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd") }

    var newFood by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    val ctx = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // whenever the IME (software keyboard) is dismissed manually, also clear focus so the cursor disappears
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    // horizontal swipe offset for pager-like animation
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val isRtlLayout = LocalLayoutDirection.current == LayoutDirection.Rtl

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val widthPx = size.width.toFloat()
                var totalDx = 0f

                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        totalDx += dragAmount
                        // follow finger
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        val threshold = widthPx * 0.25f
                        when {
                            totalDx > threshold -> {
                                // swipe right – prev in LTR, next in RTL
                                val dir = 1f // right swipe
                                scope.launch {
                                    // slide current page fully out
                                    offsetX.animateTo(dir * widthPx, tween(150))
                                    // jump to opposite side while still showing same chips (off-screen)
                                    offsetX.snapTo(-dir * widthPx)
                                    // NOW switch date so old chips are never visible again on screen
                                    if (isRtlLayout) vm.nextDay() else vm.prevDay()
                                    // give Compose a moment to load the new list before revealing
                                    kotlinx.coroutines.delay(40)
                                    // instantly bring the new page into view without sliding
                                    offsetX.snapTo(0f)
                                }
                            }

                            totalDx < -threshold -> {
                                // swipe left – next in LTR, prev in RTL
                                val dir = -1f // left swipe
                                scope.launch {
                                    // slide current page fully out
                                    offsetX.animateTo(dir * widthPx, tween(150))
                                    // jump to opposite side while still showing same chips (off-screen)
                                    offsetX.snapTo(-dir * widthPx)
                                    // NOW switch date so old chips are never visible again on screen
                                    if (isRtlLayout) vm.prevDay() else vm.nextDay()
                                    // give Compose a moment to load the new list before revealing
                                    kotlinx.coroutines.delay(40)
                                    // instantly bring the new page into view without sliding
                                    offsetX.snapTo(0f)
                                }
                            }

                            else -> {
                                // not enough, spring back
                                scope.launch { offsetX.animateTo(0f, tween(150)) }
                            }
                        }
                        totalDx = 0f
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f, tween(150)) }
                        totalDx = 0f
                    }
                )
            }
            .padding(16.dp)
    ) {
        // reset to today when app resumes
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        // Remember the last day the app was resumed to decide when to auto-switch to today
        val lastResumeDay = rememberSaveable { mutableStateOf(LocalDate.now()) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val today = LocalDate.now()
                    // Only reset if we've moved to a new calendar day since the last resume
                    if (today != lastResumeDay.value) {
                        vm.setDate(today)
                        lastResumeDay.value = today
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Top control row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = openDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = if (isHebrew) "תפריט" else "Menu"
                )
            }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = formatter.format(date))
                    if (date == LocalDate.now()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = "Today",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { vm.setDate(LocalDate.now()) }) {
                Icon(Icons.Default.Today, contentDescription = if (isHebrew) "היום" else "Today")
            }
        }
        Spacer(Modifier.height(8.dp))
        // Top half list
        val aggregated = remember(entries) {
            entries.groupBy { it.name }.mapValues { it.value.sumOf { e -> e.quantity } }
        }
        AnimatedContent(
            targetState = aggregated,
            transitionSpec = { fadeIn(tween(150)) with fadeOut(tween(0)) },
            label = "entries",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .animateContentSize()
                .offset {
                    IntOffset(
                        (if (isRtlLayout) -offsetX.value else offsetX.value).toInt(),
                        0
                    )
                }
        ) { animAggregated ->
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                animAggregated.entries.forEach { (name, qty) ->
                    AssistChip(
                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.heightIn(min = 56.dp),
                        onClick = { confirmDelete = name },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                if (qty > 1) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            "$qty",
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 2.dp
                                            ),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Bottom input area
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newFood,
                onValueChange = { newFood = it },
                label = { Text(if (isHebrew) "שם מאכל" else "Food name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newFood.isNotBlank()) {
                        vm.addFood(newFood.trim())
                        newFood = ""
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                }),
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
            ) { Text(if (isHebrew) "הוסף" else "Add") }
        }
        Spacer(Modifier.height(8.dp))

        // Quick-add lists – title on its own line, chips up to 2 lines

        Text(if (isHebrew) "אחרונים" else "Recent", style = MaterialTheme.typography.labelSmall)
        FlowRow(
            Modifier.fillMaxWidth(),
            maxLines = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            recentNames.forEach { food ->
                AssistChip(onClick = { vm.addFood(food) }, label = { Text(food) })
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(if (isHebrew) "נפוצים" else "Common", style = MaterialTheme.typography.labelSmall)
        FlowRow(
            Modifier.fillMaxWidth(),
            maxLines = 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            popularNames.forEach { food ->
                AssistChip(
                    colors = AssistChipDefaults.assistChipColors(),
                    onClick = { vm.addFood(food) },
                    label = { Text(food) })
            }
        }

        // confirmation dialog
        confirmDelete?.let { delName ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text(if (isHebrew) "מחיקה" else "Delete") },
                text = { Text(if (isHebrew) "להסיר \"$delName\"?" else "Remove \"$delName\"?") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.removeOne(delName)
                        confirmDelete = null
                    }) {
                        Text(if (isHebrew) "כן" else "Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) {
                        Text(if (isHebrew) "לא" else "No")
                    }
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    FoodTrackerTheme {
        FoodScreen(
            FoodViewModel(FoodRepository.create(AppDatabase.getInstance(LocalContext.current))),
            openDrawer = {},
            isHebrew = false
        )
    }
}