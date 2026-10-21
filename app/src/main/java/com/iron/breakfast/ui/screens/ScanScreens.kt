package com.iron.breakfast.ui.screens

import com.iron.breakfast.ui.theme.*

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.iron.breakfast.BreakfastViewModel
import com.iron.breakfast.ProductReviewOrigin
import com.iron.breakfast.ScannerUnavailableReason
import com.iron.breakfast.domain.FoodRecord
import com.iron.breakfast.domain.MealSlot
import com.iron.breakfast.domain.asciiDigits
import com.iron.breakfast.domain.displayLabel
import com.iron.breakfast.domain.normalizeBarcode
import com.iron.breakfast.label
import com.iron.breakfast.canSaveProductToLibrary
import com.iron.breakfast.pretty
import com.iron.breakfast.ui.components.foodText
import com.iron.breakfast.ui.components.ButtonKind
import com.iron.breakfast.ui.components.Field
import com.iron.breakfast.ui.components.Hairline
import com.iron.breakfast.ui.components.LinearProgress
import com.iron.breakfast.ui.components.MealChoices
import com.iron.breakfast.ui.components.mealRowFits
import com.iron.breakfast.ui.components.PrimaryButton
import com.iron.breakfast.ui.components.SecondaryButton
import com.iron.breakfast.ui.components.SectionHeading
import com.iron.breakfast.ui.components.Sheet
import com.iron.breakfast.ui.components.TertiaryButton
import com.iron.breakfast.ui.components.TopBar
import com.iron.breakfast.domain.NutritionValues
import java.util.concurrent.atomic.AtomicBoolean

/** Real CameraX preview with the corner-bracket viewfinder, a torch toggle (when the camera has a flash unit) and manual entry. */
@Composable internal fun ScannerScreen(vm: BreakfastViewModel) {
    val colors = LocalBreakfastColors.current
    val context = LocalContext.current
    val session = remember { ScannerSession() }
    DisposableEffect(Unit) { onDispose { session.dispose() } }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchAvailable by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    val hasCameraHardware = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted; if (!granted) vm.scannerPermissionDenied() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasPermission = granted
                if (granted && vm.scannerUnavailableReason == ScannerUnavailableReason.PERMISSION_DENIED) vm.beginScan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (hasCameraHardware && !hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(hasCameraHardware) { if (!hasCameraHardware) vm.scannerUnavailable() }
    LaunchedEffect(torchOn, torchAvailable) { if (torchAvailable) session.camera?.cameraControl?.enableTorch(torchOn) }
    BackHandler { vm.endScan() }
    if (vm.scannerUnavailableState) {
        ScannerUnavailableScreen(
            noCamera = !hasCameraHardware || vm.scannerUnavailableReason == ScannerUnavailableReason.NO_USABLE_CAMERA,
            onBack = vm::endScan,
            onManual = { manualOpen = true },
            onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
        )
    } else Box(Modifier.fillMaxSize().background(colors.cameraGround)) {
        if (hasPermission && !vm.scannerUnavailableState) AndroidView(factory = { ctx -> PreviewView(ctx).also { view ->
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({ runCatching {
                if (session.disposed.get()) return@runCatching
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                val scanner = BarcodeScanning.getClient(); var delivered = false
                session.provider = provider; session.preview = preview; session.analysis = analysis; session.scanner = scanner
                analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    val image = proxy.image
                    if (image == null || delivered || session.disposed.get()) { proxy.close(); return@setAnalyzer }
                    scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                        codes.firstOrNull()?.rawValue?.let { code -> if (!delivered && !session.disposed.get()) { delivered = true; vm.onBarcode(code) } }
                    }.addOnCompleteListener { proxy.close() }
                }
                val camera = provider.bindToLifecycle(context as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                session.camera = camera
                torchAvailable = camera.cameraInfo.hasFlashUnit()
            }.onFailure { if (!session.disposed.get()) { session.dispose(); vm.scannerUnavailable() } } }, ContextCompat.getMainExecutor(ctx))
        } }, Modifier.fillMaxSize())
        // Overlay: the viewfinder centres in the space above a bottom block, so it scales with the screen and font.
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = vm::endScan, Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.cameraMark) }
                Box(Modifier.weight(1f))
                if (torchAvailable) IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }, Modifier.size(44.dp).semantics { role = Role.Switch; stateDescription = if (torchOn) "On" else "Off" }) {
                    Icon(if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, contentDescription = "Torch", tint = colors.cameraMark)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Viewfinder(Modifier.size(width = 260.dp, height = 140.dp), colors.cameraMark)
            }
            Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.scannerLookupBusy) LookupProgress(vm.scannerLookupCode, vm::cancelBarcodeLookup)
                else {
                    Text(
                        if (hasPermission) "Point the camera at a barcode" else "Camera permission is required to scan.",
                        color = colors.cameraMark.copy(alpha = 0.7f),
                        style = BreakfastType.Secondary,
                        textAlign = TextAlign.Center,
                    )
                    SecondaryButton("Manual barcode", { manualOpen = true }, Modifier.padding(top = 24.dp).widthIn(min = 220.dp), fillWidth = false, onDark = true)
                }
            }
        }
    }
    if (manualOpen) ManualBarcodeSheet(onDismiss = { manualOpen = false }, onLookup = { code -> manualOpen = false; vm.onBarcode(code) })
}

@Composable
private fun ScannerUnavailableScreen(noCamera: Boolean, onBack: () -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().padding(horizontal = Gutter)) {
        TopBar("Scan", onBack, Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth().padding(top = 56.dp)) {
            SectionHeading(if (noCamera) "No camera" else "No camera access")
            Text(if (noCamera) "This phone has no usable camera." else "Allow the camera in Android settings.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 6.dp))
        }
        Box(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (noCamera) PrimaryButton("Manual barcode", onManual, kind = ButtonKind.Neutral)
            else {
                PrimaryButton("Open settings", onSettings, kind = ButtonKind.Neutral)
                SecondaryButton("Manual barcode", onManual)
            }
        }
    }
}

/** Lookup progress drawn in white over the camera ground, with a Cancel under it. */
@Composable
private fun LookupProgress(code: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        LinearProgress("Looking up $code", onDark = true)
        TertiaryButton("Cancel", onCancel, Modifier.fillMaxWidth().padding(top = 14.dp), onDark = true)
    }
}

/** Four 2dp white corner brackets with 10dp rounded corners and 30dp arms, inside the 260x140 frame. */
@Composable private fun Viewfinder(modifier: Modifier, color: Color) {
    Canvas(modifier.semantics { contentDescription = "Barcode viewfinder" }) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt)
        val arm = 30.dp.toPx()
        val radius = 10.dp.toPx()
        val inset = 1.dp.toPx()
        val w = size.width - inset
        val h = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) = drawPath(
            Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y + dy * radius)
                quadraticBezierTo(x, y, x + dx * radius, y)
                lineTo(x + dx * arm, y)
            },
            color = color,
            style = stroke,
        )
        corner(inset, inset, 1f, 1f)
        corner(w, inset, -1f, 1f)
        corner(inset, h, 1f, -1f)
        corner(w, h, -1f, -1f)
    }
}

/**
 * Static scanner ground used after a barcode has been delivered to the product review sheet.
 * CameraX is intentionally stopped at that point, so this keeps the scanner context visible
 * without implying that another barcode can still be decoded behind the sheet.
 */
@Composable internal fun ScanReviewBackdrop(onBack: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Box(Modifier.fillMaxSize().background(colors.cameraGround)) {
        Viewfinder(Modifier.align(Alignment.TopCenter).padding(top = 96.dp).size(width = 260.dp, height = 140.dp), colors.cameraMark)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.cameraMark) }
            Box(Modifier.weight(1f))
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FlashlightOff, contentDescription = null, tint = colors.cameraMark)
            }
        }
    }
}

/** Manual entry: 8, 12, 13 or 14 ASCII digits. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun ManualBarcodeSheet(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    val colors = LocalBreakfastColors.current
    var text by remember { mutableStateOf("") }
    val barcode = normalizeBarcode(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Sheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(
                label = "Barcode",
                value = text,
                onValueChange = { text = it.asciiDigits() },
                fieldModifier = Modifier.focusRequester(focus),
                error = barcode == null && text.length > 7,
                errorMessage = if (barcode == null && text.length > 7) "Enter an 8, 12, 13 or 14 digit barcode." else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { barcode?.let(onLookup) }),
            )
            if (barcode != null) PrimaryButton("Look up", { barcode.let(onLookup) }, kind = ButtonKind.Neutral)
            else SecondaryButton("Look up", {}, enabled = false)
        }
    }
}

/** One nutrient field in the review sheet's editing grid. */
private data class NutrientInput(val label: String, val value: String, val onValueChange: (String) -> Unit)

/**
 * Whether four nutrient fields fit one row of [availableDp]: each needs about 76dp (scaled with the font) for its
 * label and number; otherwise they stack as a 2x2 grid.
 */
internal fun nutrientFieldsFitRow(availableDp: Float, fontScale: Float): Boolean =
    (availableDp - 3 * 8f) / 4f >= 76f * fontScale.coerceAtLeast(1f)

/** The four decimal nutrient fields: one row when they fit, otherwise a 2x2 grid. */
@Composable private fun NutrientInputs(inputs: List<NutrientInput>) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (nutrientFieldsFitRow(maxWidth.value, LocalDensity.current.fontScale)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                inputs.forEach { Field(it.label, it.value, it.onValueChange, Modifier.weight(1f), keyboardOptions = decimal) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                inputs.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { Field(it.label, it.value, it.onValueChange, Modifier.weight(1f), keyboardOptions = decimal) }
                    }
                }
            }
        }
    }
}

private fun amountFieldText(vm: BreakfastViewModel): String =
    "${vm.productAmount.toDoubleOrNull()?.pretty() ?: vm.productAmount} ${vm.productUnit}".trim()

/**
 * Review of a scanned (or Open Food Facts) product before anything is written. Add logs it to the chosen
 * meal on the selected day with the typed amount; closing the sheet writes nothing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun ProductReviewSheet(vm: BreakfastViewModel, food: FoodRecord) {
    val colors = LocalBreakfastColors.current
    val letter = sourceLetter(food)
    val basis = food.nutrition
    var amountText by remember(food.id) { mutableStateOf(amountFieldText(vm)) }
    // While a resolve queue is active the meal is the queue's own: shown as a plain line, not a choice.
    val queueMeal = vm.unknownQueueTarget.takeIf { vm.selectedProductOrigin == ProductReviewOrigin.QUEUE && vm.unknownQueue.isNotEmpty() }
    val fromLibrary = vm.selectedProductOrigin == ProductReviewOrigin.LIBRARY
    var meal by remember(food.id) { mutableStateOf(queueMeal ?: vm.slot ?: MealSlot.LUNCH) }
    var editing by remember(food.id) { mutableStateOf(false) }
    val parsed = parseProductAmount(amountText, basis.unit)
    val calculation = if (parsed != null) vm.productCalculation(food) else null
    val approx = productApproxLabel(calculation, parsed?.unit, basis.unit)
    val canAdd = !food.archived && parsed != null && calculation?.supported == true
    // The numbers on screen follow any corrections made under "Edit details".
    val values = NutritionValues(
        vm.productCalories.toDoubleOrNull() ?: basis.values.calories,
        vm.productProtein.toDoubleOrNull(),
        vm.productCarbs.toDoubleOrNull(),
        vm.productFat.toDoubleOrNull(),
    )
    val per100 = "${basisLabel(basis.amount, basis.unit)}: ${formatKcal(values.calories)} kcal, " +
        "P ${values.protein?.pretty() ?: "—"}, C ${values.carbohydrates?.pretty() ?: "—"}, F ${values.fat?.pretty() ?: "—"}"
    Sheet(onDismissRequest = vm::clearSelectedProduct, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(foodText(vm.productName.ifBlank { food.name }), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.weight(1f))
                if (queueMeal != null) Text(resolveQueuePosition(vm), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
            }
            if (!food.brand.isNullOrBlank()) Text(food.brand.orEmpty(), color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 2.dp))
            Text(sourceLabel(letter), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 2.dp))
            if (!editing) Text(per100, color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 10.dp))
            Hairline(Modifier.padding(top = 16.dp))
            if (editing) Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Name", vm.productName, vm::onProductName)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(basisLabel(basis.amount, basis.unit), color = colors.soft, style = BreakfastType.Meta)
                    NutrientInputs(
                        listOf(
                            NutrientInput("kcal", vm.productCalories) { vm.onProductCalories(it.asciiDigits(allowDecimalPoint = true)) },
                            NutrientInput("Protein, g", vm.productProtein) { vm.onProductProtein(it.asciiDigits(allowDecimalPoint = true)) },
                            NutrientInput("Carbs, g", vm.productCarbs) { vm.onProductCarbs(it.asciiDigits(allowDecimalPoint = true)) },
                            NutrientInput("Fat, g", vm.productFat) { vm.onProductFat(it.asciiDigits(allowDecimalPoint = true)) },
                        ),
                    )
                }
            }
            Field(
                label = "Amount",
                value = amountText,
                onValueChange = {
                    amountText = it
                    val next = parseProductAmount(it, basis.unit)
                    vm.onProductAmount(next?.amount?.toString().orEmpty())
                    if (next != null) vm.onProductUnit(next.unit.displayLabel())
                },
                modifier = Modifier.padding(top = 16.dp),
                error = parsed == null && amountText.isNotBlank(),
                hint = approx?.let { "≈$it" },
            )
            if (!fromLibrary) {
                if (queueMeal != null) Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Meal", color = colors.soft, style = BreakfastType.Meta)
                    Text(queueMeal.label(), color = colors.ink, style = BreakfastType.BodyMedium)
                }
                else {
                    Text("Meal", color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 16.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        MealChoices(meal, { meal = it }, singleRow = mealRowFits(maxWidth.value, LocalDensity.current.fontScale), compact = true)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Result", color = colors.soft, style = BreakfastType.Meta)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(calculation?.takeIf { it.supported }?.let { formatKcal(it.nutrition.calories) } ?: "—", color = colors.ink, style = BreakfastType.MacroValue)
                        Text(" kcal", color = colors.soft, style = BreakfastType.Secondary)
                    }
                }
                PrimaryButton("Add to ${(queueMeal ?: meal).label()}", { vm.addSelected(queueMeal ?: meal) }, Modifier.padding(top = 14.dp), enabled = canAdd)
                if (queueMeal != null) vm.unknownQueue.drop(1).firstOrNull()?.let { next ->
                    Text("Next: $next", color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            } else {
                PrimaryButton("Save to Library", vm::saveSelectedToLibrary, Modifier.padding(top = 14.dp), enabled = canAdd)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TertiaryButton(if (editing) "Hide details" else "Edit details", { editing = !editing }, Modifier.weight(1f).semantics { stateDescription = if (editing) "Expanded" else "Collapsed" })
                TertiaryButton("Find better match", { vm.clearSelectedProduct(); vm.openSearch(fromResolver = vm.resolutionContinuation) }, Modifier.weight(1f))
                TertiaryButton("Rescan", { vm.clearSelectedProduct(); vm.beginScan() }, Modifier.weight(1f))
            }
        }
    }
}

private fun resolveQueuePosition(vm: BreakfastViewModel): String {
    val shown = maxOf(vm.unknownQueueTotal, vm.unknownQueue.size)
    return "${(shown - vm.unknownQueue.size + 1).coerceIn(1, maxOf(1, shown))} of $shown"
}

private class ScannerSession {
    val disposed = AtomicBoolean(false)
    @Volatile var provider: ProcessCameraProvider? = null
    @Volatile var preview: Preview? = null
    @Volatile var analysis: ImageAnalysis? = null
    @Volatile var scanner: BarcodeScanner? = null
    @Volatile var camera: Camera? = null

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        analysis?.clearAnalyzer()
        scanner?.close()
        val currentPreview = preview
        val currentAnalysis = analysis
        if (currentPreview != null && currentAnalysis != null) provider?.unbind(currentPreview, currentAnalysis)
    }
}
