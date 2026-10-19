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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
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
        else Text(
            if (vm.scannerUnavailableState) "Camera unavailable. Enter a barcode manually."
            else "Camera permission is required to scan.",
            Modifier.align(Alignment.TopCenter).padding(top = 150.dp),
            color = colors.cameraMark,
            style = BreakfastType.Secondary,
        )
        Viewfinder(Modifier.align(Alignment.Center).offset(y = (-80).dp).size(width = 260.dp, height = 160.dp), colors.cameraMark)
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::endScan, Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.cameraMark) }
            Box(Modifier.weight(1f))
            if (torchAvailable) IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }, Modifier.size(44.dp).semantics { role = Role.Switch; stateDescription = if (torchOn) "On" else "Off" }) {
                Icon(if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, contentDescription = "Torch", tint = colors.cameraMark)
            }
        }
        if (vm.scannerLookupBusy) LookupSheet(vm.scannerLookupCode, vm::cancelBarcodeLookup, Modifier.align(Alignment.BottomCenter))
        else SecondaryButton(
            "Manual barcode", { manualOpen = true }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = Gutter, end = Gutter, bottom = 28.dp),
            onDark = true,
        )
    }
    if (manualOpen) ManualBarcodeSheet(onDismiss = { manualOpen = false }, onLookup = { code -> manualOpen = false; vm.onBarcode(code) })
}

@Composable
private fun ScannerUnavailableScreen(noCamera: Boolean, onBack: () -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().padding(horizontal = Gutter)) {
        TopBar("Scan", onBack, Modifier.padding(top = 8.dp))
        Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            SectionHeading(if (noCamera) "No camera" else "No camera access")
            Text(if (noCamera) "This phone has no usable camera." else "Allow the camera in Android settings.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(if (noCamera) "Manual barcode" else "Open settings", if (noCamera) onManual else onSettings, kind = ButtonKind.Neutral)
            if (!noCamera) SecondaryButton("Manual barcode", onManual)
        }
    }
}

/** Progress over the camera ground, on a `bg` sheet that hugs the bottom edge. */
@Composable
private fun LookupSheet(code: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBreakfastColors.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bg, SheetShape)
            .navigationBarsPadding()
            .padding(horizontal = Gutter, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinearProgress("Looking up $code")
        TertiaryButton("Cancel", onCancel, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Four corner brackets, drawn square-capped so they read as registration marks. */
@Composable private fun Viewfinder(modifier: Modifier, color: Color) {
    Canvas(modifier.semantics { contentDescription = "Barcode viewfinder" }) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Square)
        val arm = 27.dp.toPx()
        val inset = 1.5.dp.toPx()
        val w = size.width - inset
        val h = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) = drawPath(
            Path().apply { moveTo(x, y + dy * arm); lineTo(x, y); lineTo(x + dx * arm, y) },
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
        Text("camera", Modifier.align(Alignment.Center).offset(y = (-262).dp), color = colors.cameraMark, style = BreakfastType.Meta)
        Viewfinder(Modifier.align(Alignment.Center).offset(y = (-80).dp).size(width = 260.dp, height = 160.dp), colors.cameraMark)
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
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
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
    fun macro(value: Double?) = buildAnnotatedString {
        if (value == null) append("—") else { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.pretty()) }; append(" g") }
    }
    Sheet(onDismissRequest = vm::clearSelectedProduct, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())) {
            Text(foodText(vm.productName.ifBlank { food.name }), color = colors.ink, style = BreakfastType.Section)
            if (!food.brand.isNullOrBlank()) Text(food.brand.orEmpty(), color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 2.dp))
            Text(sourceLabel(letter), color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 2.dp))
            if (queueMeal != null) Text(resolveQueuePosition(vm), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 2.dp))
            if (!editing) {
                Hairline(Modifier.padding(top = 16.dp))
                Text(
                    buildAnnotatedString {
                        append("${basisLabel(basis.amount, basis.unit)}: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(receiptKcal(values.calories)) }
                        append(" kcal, P "); append(macro(values.protein))
                        append(", C "); append(macro(values.carbohydrates))
                        append(", F "); append(macro(values.fat))
                    },
                    color = colors.ink,
                    style = BreakfastType.Secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (editing) Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Name", vm.productName, vm::onProductName)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("kcal", vm.productCalories, { vm.onProductCalories(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), keyboardOptions = decimal)
                    Field("Protein, g", vm.productProtein, { vm.onProductProtein(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), keyboardOptions = decimal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Carbs, g", vm.productCarbs, { vm.onProductCarbs(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), keyboardOptions = decimal)
                    Field("Fat, g", vm.productFat, { vm.onProductFat(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), keyboardOptions = decimal)
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
                hint = approx?.let { "≈ $it" },
            )
            if (!fromLibrary) {
                if (queueMeal != null) Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Meal", color = colors.soft, style = BreakfastType.Meta)
                    Text(queueMeal.label(), color = colors.ink, style = BreakfastType.BodyMedium)
                }
                else MealChoices(meal, { meal = it }, Modifier.padding(top = 16.dp))
                Hairline(Modifier.padding(top = 16.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Result", color = colors.ink, style = BreakfastType.Section)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(calculation?.takeIf { it.supported }?.let { receiptKcal(it.nutrition.calories) } ?: "—", color = colors.ink, style = BreakfastType.Hero)
                        Text(" kcal", color = colors.soft, style = BreakfastType.Secondary)
                    }
                }
                PrimaryButton("Add to ${(queueMeal ?: meal).label()}", { vm.addSelected(queueMeal ?: meal) }, Modifier.padding(top = 16.dp), enabled = canAdd)
            } else {
                PrimaryButton("Save to library", vm::saveSelectedToLibrary, Modifier.padding(top = 16.dp), enabled = canAdd)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TertiaryButton(if (editing) "Hide details" else "Edit details", { editing = !editing }, Modifier.weight(1f).semantics { stateDescription = if (editing) "Expanded" else "Collapsed" })
                TertiaryButton("Find better match", { vm.clearSelectedProduct(); vm.openSearch(fromResolver = vm.resolutionContinuation) }, Modifier.weight(1f))
                TertiaryButton("Rescan", { vm.clearSelectedProduct(); vm.beginScan() }, Modifier.weight(1f))
            }
            if (queueMeal != null) {
                vm.unknownQueue.drop(1).firstOrNull()?.let { next ->
                    Text("Next: $next", color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
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
