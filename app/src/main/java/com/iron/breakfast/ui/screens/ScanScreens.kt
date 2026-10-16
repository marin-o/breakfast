package com.iron.breakfast.ui.screens

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import com.iron.breakfast.domain.NutritionValues
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
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
import com.iron.breakfast.ui.components.ApproxTag
import com.iron.breakfast.ui.components.ReceiptBottomSheet
import com.iron.breakfast.ui.components.ReceiptField
import com.iron.breakfast.ui.components.ReceiptMealChips
import com.iron.breakfast.ui.components.ReceiptPrimaryButton
import com.iron.breakfast.ui.components.ReceiptOutlinedButton
import com.iron.breakfast.ui.components.PrintedProgress
import com.iron.breakfast.ui.components.ReceiptSheet
import com.iron.breakfast.ui.components.TertiaryLink
import com.iron.breakfast.ui.components.ReceiptTonalButton
import com.iron.breakfast.ui.components.receiptDashedRule
import com.iron.breakfast.ui.components.sourceLabel
import com.iron.breakfast.ui.components.receiptFoodName
import com.iron.breakfast.ui.theme.LocalReceiptColors
import java.util.concurrent.atomic.AtomicBoolean

/** Real CameraX preview with the corner-bracket viewfinder, a torch toggle (when the camera has a flash unit) and manual entry. */
@Composable internal fun ScannerScreen(vm: BreakfastViewModel) {
    val colors = LocalReceiptColors.current
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
            color = androidx.compose.ui.graphics.Color(0xFFE2E3DE),
        )
        Viewfinder(Modifier.align(Alignment.Center).offset(y = (-80).dp).size(width = 260.dp, height = 160.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::endScan, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color(0xFFE2E3DE)) }
            Box(Modifier.weight(1f))
            if (torchAvailable) IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }, Modifier.size(48.dp).semantics { role = Role.Switch; stateDescription = if (torchOn) "On" else "Off" }) {
                Icon(if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, contentDescription = "Torch", tint = androidx.compose.ui.graphics.Color(0xFFE2E3DE))
            }
        }
        if (vm.scannerLookupBusy) LookupSheet(vm.scannerLookupCode, vm::cancelBarcodeLookup, Modifier.align(Alignment.BottomCenter))
        else ReceiptOutlinedButton("Manual barcode", { manualOpen = true }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 28.dp))
    }
    if (manualOpen) ManualBarcodeSheet(onDismiss = { manualOpen = false }, onLookup = { code -> manualOpen = false; vm.onBarcode(code) })
}

@Composable
private fun ScannerUnavailableScreen(noCamera: Boolean, onBack: () -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalReceiptColors.current
    Surface(Modifier.fillMaxSize(), color = colors.paper) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = colors.ink) }
                Text("SCAN", color = colors.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (noCamera) "NO CAMERA" else "NO CAMERA ACCESS", color = colors.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                Text(if (noCamera) "This phone has no usable camera." else "Allow the camera in Android settings.", color = colors.soft, modifier = Modifier.padding(top = 4.dp))
                ReceiptPrimaryButton(if (noCamera) "Manual barcode" else "Open settings", if (noCamera) onManual else onSettings, Modifier.padding(top = 18.dp))
                if (!noCamera) ReceiptOutlinedButton("Manual barcode", onManual, Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun LookupSheet(code: String, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    ReceiptSheet(modifier.fillMaxWidth().navigationBarsPadding()) {
        PrintedProgress("Looking up", code, progressSteps = 5, modifier = Modifier.padding(top = 6.dp))
        TertiaryLink("Cancel", onCancel, Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Four 3dp corner brackets, drawn square-capped so they read as printed registration marks. */
@Composable private fun Viewfinder(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Barcode viewfinder" }) {
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Square)
        val arm = 27.dp.toPx()
        val inset = 1.5.dp.toPx()
        val w = size.width - inset
        val h = size.height - inset
        fun corner(x: Float, y: Float, dx: Float, dy: Float) = drawPath(
            Path().apply { moveTo(x, y + dy * arm); lineTo(x, y); lineTo(x + dx * arm, y) },
            color = androidx.compose.ui.graphics.Color(0xFFE2E3DE),
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
    val colors = LocalReceiptColors.current
    Box(Modifier.fillMaxSize().background(colors.cameraGround)) {
        Text(
            "camera",
            Modifier.align(Alignment.Center).offset(y = (-262).dp),
            color = androidx.compose.ui.graphics.Color(0xFFE2E3DE),
            style = MaterialTheme.typography.bodySmall,
        )
        Viewfinder(Modifier.align(Alignment.Center).offset(y = (-80).dp).size(width = 260.dp, height = 160.dp))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color(0xFFE2E3DE)) }
            Box(Modifier.weight(1f))
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FlashlightOff, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFE2E3DE))
            }
        }
    }
}

/** Manual entry on the one shared barcode rule: 8, 12, 13 or 14 ASCII digits. */
@Composable private fun ManualBarcodeSheet(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    val colors = LocalReceiptColors.current
    var text by remember { mutableStateOf("") }
    val barcode = normalizeBarcode(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ReceiptBottomSheet(onDismissRequest = onDismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReceiptField(
                label = "Barcode",
                fieldModifier = Modifier.focusRequester(focus),
                value = text,
                onValueChange = { text = it.asciiDigits() },
                labelColor = colors.soft,
                container = colors.paper,
                outline = colors.soft,
                error = barcode == null && text.length > 7,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                keyboardActions = KeyboardActions(onDone = { barcode?.let(onLookup) }),
            )
            if (barcode == null && text.length > 7) Text("Enter an 8, 12, 13 or 14 digit barcode.", color = colors.red, style = MaterialTheme.typography.bodySmall)
            if (barcode != null) ReceiptPrimaryButton("Look up", { barcode.let(onLookup) })
            else ReceiptOutlinedButton("Look up", {}, enabled = false)
        }
    }
}

private fun amountFieldText(vm: BreakfastViewModel): String =
    "${vm.productAmount.toDoubleOrNull()?.pretty() ?: vm.productAmount} ${vm.productUnit}".trim()

/**
 * Review of a scanned (or Open Food Facts) product before anything is written. Add logs it to the chosen
 * meal on the selected day with the typed amount; closing the sheet writes nothing.
 */
@Composable internal fun ProductReviewSheet(vm: BreakfastViewModel, food: FoodRecord) {
    val colors = LocalReceiptColors.current
    val letter = sourceLetter(food)
    val basis = food.nutrition
    var amountText by remember(food.id) { mutableStateOf(amountFieldText(vm)) }
    // While a resolve queue is active the meal is the queue's own: shown as a printed line, not a choice.
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
    ReceiptBottomSheet(onDismissRequest = vm::clearSelectedProduct, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(receiptFoodName(vm.productName.ifBlank { food.name }), color = colors.ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    if (!food.brand.isNullOrBlank()) Text(food.brand.orEmpty(), color = colors.soft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                    Text("${letter}  ${sourceLabel(letter)}", color = colors.soft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
                }
                if (queueMeal != null) Text("${resolveQueuePosition(vm)}", color = colors.soft, modifier = Modifier.padding(start = 4.dp))
            }
            Text(
                buildAnnotatedString {
                    append("${basisLabel(basis.amount, basis.unit)}: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(receiptKcal(values.calories)) }
                    append(" kcal, P "); append(macro(values.protein))
                    append(", C "); append(macro(values.carbohydrates))
                    append(", F "); append(macro(values.fat))
                },
                color = colors.ink,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).receiptDashedRule().padding(top = 12.dp),
            )
            if (editing) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReceiptField("Name", vm.productName, vm::onProductName, labelColor = colors.soft, container = colors.paper, outline = colors.soft)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptField("kcal", vm.productCalories, { vm.onProductCalories(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), labelColor = colors.soft, container = colors.paper, outline = colors.soft, keyboardOptions = decimal)
                    ReceiptField("Protein, g", vm.productProtein, { vm.onProductProtein(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), labelColor = colors.soft, container = colors.paper, outline = colors.soft, keyboardOptions = decimal)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ReceiptField("Carbs, g", vm.productCarbs, { vm.onProductCarbs(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), labelColor = colors.soft, container = colors.paper, outline = colors.soft, keyboardOptions = decimal)
                    ReceiptField("Fat, g", vm.productFat, { vm.onProductFat(it.asciiDigits(allowDecimalPoint = true)) }, Modifier.weight(1f), labelColor = colors.soft, container = colors.paper, outline = colors.soft, keyboardOptions = decimal)
                }
            }
            ReceiptField(
                label = "Amount",
                value = amountText,
                onValueChange = {
                    amountText = it
                    val next = parseProductAmount(it, basis.unit)
                    vm.onProductAmount(next?.amount?.toString().orEmpty())
                    if (next != null) vm.onProductUnit(next.unit.displayLabel())
                },
                modifier = Modifier.padding(top = 16.dp),
                labelColor = colors.soft,
                container = colors.paper,
                outline = colors.soft,
                error = parsed == null && amountText.isNotBlank(),
                trailing = approx?.let { label -> { ApproxTag(label) } },
            )
            if (!fromLibrary) {
                if (queueMeal != null) Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Meal", color = colors.soft); Text(queueMeal.label().uppercase(), color = colors.ink, fontWeight = FontWeight.Bold) }
                else ReceiptMealChips(meal, { meal = it }, Modifier.padding(top = 14.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).receiptDashedRule().padding(top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("RESULT", color = colors.ink, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(calculation?.takeIf { it.supported }?.let { receiptKcal(it.nutrition.calories) } ?: "—", color = colors.ink, style = MaterialTheme.typography.displayLarge)
                        Text(" kcal", color = colors.soft, style = MaterialTheme.typography.labelMedium)
                    }
                }
                ReceiptPrimaryButton("Add to ${(queueMeal ?: meal).label()}", { vm.addSelected(queueMeal ?: meal) }, enabled = canAdd)
            } else {
                ReceiptPrimaryButton("Save to library", vm::saveSelectedToLibrary, enabled = canAdd)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TertiaryLink("EDIT DETAILS", { editing = !editing }, Modifier.weight(1f).semantics { stateDescription = if (editing) "Expanded" else "Collapsed" })
                TertiaryLink("FIND BETTER MATCH", { vm.clearSelectedProduct(); vm.openSearch(fromResolver = vm.resolutionContinuation) }, Modifier.weight(1f))
                TertiaryLink("RESCAN", { vm.clearSelectedProduct(); vm.beginScan() }, Modifier.weight(1f))
            }
            if (queueMeal != null) {
                vm.unknownQueue.drop(1).firstOrNull()?.let { next ->
                    Text("Next: $next", color = colors.soft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
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
