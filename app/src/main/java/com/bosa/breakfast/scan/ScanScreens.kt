package com.bosa.breakfast.scan

import com.bosa.breakfast.ui.MacroPart
import com.bosa.breakfast.ui.macroLetterParts
import com.bosa.breakfast.ui.components.MacroLine
import com.bosa.breakfast.ui.components.MealField
import com.bosa.breakfast.ui.components.NutrientFields
import com.bosa.breakfast.ui.components.NutrientField
import com.bosa.breakfast.ui.components.SubScreen
import com.bosa.breakfast.ui.components.ToastFloorMark
import com.bosa.breakfast.ui.screens.kcalLabel
import com.bosa.breakfast.domain.editableNumber
import com.bosa.breakfast.domain.sourceLetter
import com.bosa.breakfast.domain.parseDecimal
import com.bosa.breakfast.ui.theme.*
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.camera.core.UseCaseGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.bosa.breakfast.domain.FoodRecord
import com.bosa.breakfast.domain.asciiDigits
import com.bosa.breakfast.domain.displayLabel
import com.bosa.breakfast.domain.normalizeBarcode
import com.bosa.breakfast.domain.scannedProductBarcode
import com.bosa.breakfast.ui.label
import com.bosa.breakfast.ui.components.foodText
import com.bosa.breakfast.ui.components.ButtonKind
import com.bosa.breakfast.ui.components.Field
import com.bosa.breakfast.ui.components.Hairline
import com.bosa.breakfast.ui.components.LinearProgress
import com.bosa.breakfast.ui.components.rememberMotionReduced
import com.bosa.breakfast.ui.components.PrimaryButton
import com.bosa.breakfast.ui.components.SecondaryButton
import com.bosa.breakfast.ui.components.SectionHeading
import com.bosa.breakfast.ui.components.Sheet
import com.bosa.breakfast.ui.components.TertiaryButton
import com.bosa.breakfast.domain.NutritionValues
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import zxingcpp.BarcodeReader
import com.bosa.breakfast.ui.screens.productApproxLabel
import com.bosa.breakfast.domain.MeasureUnit
import com.bosa.breakfast.domain.isUsdaFoodId
import com.bosa.breakfast.ui.components.MeasureChoices
import com.bosa.breakfast.ui.screens.measureWeightDetail
import com.bosa.breakfast.ui.screens.basisLabel
import com.bosa.breakfast.ui.screens.formatKcal
import com.bosa.breakfast.ui.screens.labelFor
import com.bosa.breakfast.ui.screens.noValueLine
import com.bosa.breakfast.ui.screens.sourceLabel


/** Real CameraX preview with the corner-bracket viewfinder, a torch toggle (when the camera has a flash unit) and manual entry. */
@Composable internal fun ScannerScreen(scan: ScanSession, flashlightAtStart: Boolean) {
    val colors = LocalBreakfastColors.current
    val context = LocalContext.current
    val session = remember { ScannerSession() }
    DisposableEffect(Unit) { onDispose { session.dispose() } }
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var torchAvailable by remember { mutableStateOf(false) }
    // "Flashlight at start": the torch comes on as soon as the camera reports a flash unit.
    var torchOn by remember { mutableStateOf(flashlightAtStart) }
    var manualOpen by rememberSaveable { mutableStateOf(false) }
    // Asked once per scan session: a configuration change after a denial must not bring the system dialog back.
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val hasCameraHardware = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted; if (!granted) scan.permissionDenied() }
    val feather = with(LocalDensity.current) { ScanFeather.toPx() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasPermission = granted
                if (granted && scan.unavailableReason == ScannerUnavailableReason.PERMISSION_DENIED) scan.begin(scan.forLog, scan.correcting)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (hasCameraHardware && !hasPermission && !permissionAsked) { permissionAsked = true; launcher.launch(Manifest.permission.CAMERA) }
    }
    LaunchedEffect(hasCameraHardware) { if (!hasCameraHardware) scan.cameraUnavailable() }
    LaunchedEffect(torchOn, torchAvailable) { if (torchAvailable) session.camera?.cameraControl?.enableTorch(torchOn) }
    if (scan.unavailable) {
        ScannerUnavailableScreen(
            noCamera = !hasCameraHardware || scan.unavailableReason == ScannerUnavailableReason.NO_USABLE_CAMERA,
            onBack = scan::end,
            onManual = { manualOpen = true },
            onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
        )
    } else Box(Modifier.fillMaxSize().background(colors.cameraGround)) {
        if (hasPermission && !scan.unavailable) AndroidView(factory = { ctx -> PreviewView(ctx).also { view ->
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({ runCatching {
                if (session.disposed.get()) return@runCatching
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                // 1280x960 keeps the preview's default 4:3, so both see the same field of view; the decoder reads the shared crop of it.
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                    .build()
                val analysisThread = Executors.newSingleThreadExecutor()
                session.provider = provider; session.preview = preview; session.analysis = analysis; session.analysisThread = analysisThread
                val main = ContextCompat.getMainExecutor(ctx)
                // The reader loads its native library when made, so that too waits for the analysis thread. Only that thread touches these two.
                val reader by lazy(LazyThreadSafetyMode.NONE) { BarcodeReader(productReaderOptions()) }
                var delivered = false
                analysis.setAnalyzer(analysisThread) { proxy ->
                    proxy.use {
                        if (delivered || session.disposed.get()) return@use
                        val code = try {
                            readProductBarcode(reader, proxy, feather, session)
                        } catch (_: LinkageError) {
                            // The decoder's native library cannot load, so no frame can ever be read: stop once, without trying the reader again.
                            delivered = true
                            main.execute { if (!session.disposed.get()) { session.dispose(); scan.cameraUnavailable() } }
                            return@use
                        } catch (_: Exception) {
                            // A frame that fails to decode is ignored, as one with no code is, and the next one is tried.
                            return@use
                        } ?: return@use
                        delivered = true
                        main.execute { if (!session.disposed.get()) scan.onBarcode(code) }
                    }
                }
                // A shared view port crops the analysis frame to what the preview shows, so a box maps straight onto the screen.
                val owner = context as ComponentActivity
                val camera = view.viewPort?.let { port ->
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, UseCaseGroup.Builder().setViewPort(port).addUseCase(preview).addUseCase(analysis).build())
                } ?: provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                session.camera = camera
                torchAvailable = camera.cameraInfo.hasFlashUnit()
            }.onFailure { if (!session.disposed.get()) { session.dispose(); scan.cameraUnavailable() } } }, ContextCompat.getMainExecutor(ctx))
        } }, Modifier.fillMaxSize().onGloballyPositioned { session.previewBounds = it.boundsInRoot() })
        ScannerFrame(
            top = {
                IconButton(onClick = scan::end, Modifier.size(IconTarget)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.cameraMark) }
                Box(Modifier.weight(1f))
                if (torchAvailable) IconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }, Modifier.size(IconTarget).semantics { role = Role.Switch; stateDescription = if (torchOn) "On" else "Off" }) {
                    Icon(if (torchOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, contentDescription = "Torch", tint = colors.cameraMark)
                }
            },
            viewfinderModifier = Modifier.onGloballyPositioned { session.viewfinderBounds = it.boundsInRoot() },
        ) {
            if (scan.lookupBusy) LookupProgress(scan.lookupCode, scan::end)
            else ScannerHint(hasPermission, onManual = { manualOpen = true })
        }
    }
    if (manualOpen) ManualBarcodeSheet(onDismiss = { manualOpen = false }, onLookup = { code -> manualOpen = false; scan.onBarcode(code) })
}

@Composable
private fun ScannerUnavailableScreen(noCamera: Boolean, onBack: () -> Unit, onManual: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalBreakfastColors.current
    SubScreen(
        "Scan", onBack, scroll = false,
        bottom = {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (noCamera) PrimaryButton("Manual barcode", onManual, kind = ButtonKind.Neutral)
                else {
                    PrimaryButton("Open settings", onSettings, kind = ButtonKind.Neutral)
                    SecondaryButton("Manual barcode", onManual)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 56.dp)) {
            SectionHeading(if (noCamera) "No camera" else "No camera access")
            Text(if (noCamera) "This phone has no usable camera." else "Allow the camera in Android settings.", color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 6.dp))
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

/** The scanner's layout: the top bar, then the viewfinder centred in the space above [bottom], so it scales with the screen and font. */
@Composable private fun ScannerFrame(
    top: @Composable RowScope.() -> Unit,
    viewfinderModifier: Modifier = Modifier,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalBreakfastColors.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, content = top)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Viewfinder(viewfinderModifier.size(width = 260.dp, height = 140.dp), colors.cameraMark)
        }
        ToastFloorMark()
        Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, content = bottom)
    }
}

@Composable private fun ScannerHint(hasPermission: Boolean, onManual: () -> Unit) {
    val colors = LocalBreakfastColors.current
    Text(
        if (hasPermission) "Point the camera at a barcode" else "Camera permission is required to scan.",
        color = colors.cameraMark.copy(alpha = 0.7f),
        style = BreakfastType.Secondary,
        textAlign = TextAlign.Center,
    )
    SecondaryButton("Manual barcode", onManual, Modifier.padding(top = 24.dp).widthIn(min = 220.dp), fillWidth = false, onDark = true)
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
    // The brackets start where the scanner drew them and slide up above the sheet, as drawn, instead of jumping.
    val target = with(LocalDensity.current) { ReviewViewfinderTop.toPx() }
    var start by remember { mutableStateOf<Float?>(null) }
    val slide = remember { Animatable(0f) }
    // With reduced motion the brackets are simply in their place above the sheet.
    val reduced = rememberMotionReduced()
    LaunchedEffect(start != null) {
        if (start != null) { if (reduced) slide.snapTo(1f) else slide.animateTo(1f, tween(durationMillis = 280)) }
    }
    Box(Modifier.fillMaxSize().background(colors.cameraGround)) {
        ScannerFrame(
            top = {
                IconButton(onClick = onBack, Modifier.size(IconTarget)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.cameraMark) }
                Box(Modifier.weight(1f))
                Box(Modifier.size(IconTarget), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.FlashlightOff, contentDescription = null, tint = colors.cameraMark)
                }
            },
            viewfinderModifier = Modifier
                .onGloballyPositioned { if (start == null) start = it.boundsInRoot().top }
                .graphicsLayer { translationY = start?.let { (target - it) * slide.value } ?: 0f },
        ) {
            // The scanner's bottom block, unseen, so the brackets stay exactly where they were while scanning.
            Column(Modifier.alpha(0f).clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) { ScannerHint(hasPermission = true, onManual = {}) }
        }
    }
}

/** Manual entry: 8, 12, 13 or 14 ASCII digits. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable private fun ManualBarcodeSheet(onDismiss: () -> Unit, onLookup: (String) -> Unit) {
    val colors = LocalBreakfastColors.current
    var text by rememberSaveable { mutableStateOf("") }
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

private fun amountFieldText(review: ProductReview): String =
    "${parseDecimal(review.amount)?.let(::editableNumber) ?: review.amount} ${review.unit}".trim()

/**
 * Review of a scanned (or Open Food Facts) product before anything is written. Add logs it to the chosen
 * meal on the selected day with the typed amount; closing the sheet writes nothing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable internal fun ProductReviewSheet(review: ProductReview, food: FoodRecord) {
    val colors = LocalBreakfastColors.current
    val letter = sourceLetter(food)
    val basis = food.nutrition
    var amountText by rememberSaveable(food.id) { mutableStateOf(amountFieldText(review)) }
    // While a resolve queue is active the meal is the queue's own: shown as a plain line, not a choice.
    val queueMeal = review.queueMeal()
    val fromLibrary = review.origin == ProductReviewOrigin.LIBRARY
    val forLog = review.origin.takesNoMeal
    var meal by rememberSaveable(food.id) { mutableStateOf(review.initialMeal()) }
    var editing by rememberSaveable(food.id) { mutableStateOf(false) }
    // A bare number counts the chosen serving while one is chosen, else the basis unit.
    val bareUnit = if (review.servingName != null) MeasureUnit.SERVING else basis.unit
    val parsed = parseProductAmount(amountText, bareUnit)
    val calculation = if (parsed != null) review.calculation(food) else null
    val approx = productApproxLabel(calculation, parsed?.unit, basis.unit)
    val canAdd = parsed != null && calculation?.supported == true
    // The numbers on screen follow any corrections made under "Edit details".
    val values = NutritionValues(
        parseDecimal(review.calories) ?: basis.values.calories,
        parseDecimal(review.protein),
        parseDecimal(review.carbs),
        parseDecimal(review.fat),
    )
    val per100 = listOf(MacroPart("${basisLabel(basis.amount, basis.unit)}: ${kcalLabel(values.calories)}")) + macroLetterParts(values)
    Sheet(onDismissRequest = review::dismiss, skipPartiallyExpanded = true) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(foodText(review.name.ifBlank { food.name }), color = colors.ink, style = BreakfastType.Section, modifier = Modifier.weight(1f))
                if (queueMeal != null) Text(review.resolveQueue()?.position().orEmpty(), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
            }
            if (!food.brand.isNullOrBlank()) Text(food.brand.orEmpty(), color = colors.soft, style = BreakfastType.Secondary, modifier = Modifier.padding(top = 2.dp))
            Text(sourceLabel(letter), color = colors.soft, style = BreakfastType.Meta, modifier = Modifier.padding(top = 2.dp))
            if (!editing) MacroLine(per100, BreakfastType.Meta, Modifier.padding(top = 10.dp))
            Hairline(Modifier.padding(top = 16.dp))
            if (editing) Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Name", review.name, review::onName)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(basisLabel(basis.amount, basis.unit), color = colors.soft, style = BreakfastType.Meta)
                    NutrientFields(
                        listOf(
                            NutrientField("kcal", review.calories, review::onCalories),
                            NutrientField("Protein, g", review.protein, review::onProtein, macro = Macro.PROTEIN),
                            NutrientField("Carbs, g", review.carbs, review::onCarbs, macro = Macro.CARBS),
                            NutrientField("Fat, g", review.fat, review::onFat, macro = Macro.FAT),
                        ),
                        gap = 8.dp,
                        oneRowWhenItFits = true,
                    )
                }
            }
            Field(
                label = "Amount",
                value = amountText,
                onValueChange = {
                    amountText = it
                    val next = parseProductAmount(it, bareUnit)
                    review.onAmount(editableNumber(next?.amount))
                    if (next != null && next.unit != MeasureUnit.SERVING) review.onUnit(next.unit.displayLabel())
                },
                modifier = Modifier.padding(top = 16.dp),
                error = amountText.isNotBlank() && calculation == null,
                errorMessage = review.noValue(food)?.takeIf { parsed != null }?.let { noValueLine(it.servingName ?: it.unit.labelFor(it.amount)) },
                errorDescription = "Enter a positive amount and a supported unit, such as 100 g or 1 tbsp.",
                hint = approx?.let { "≈$it" },
            )
            if (isUsdaFoodId(food.id)) MeasureChoices(
                food,
                selected = review.measure(),
                onSelect = { review.onMeasure(it); amountText = amountFieldText(review) },
                detail = { measureWeightDetail(food, it) },
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!fromLibrary) {
                if (queueMeal != null) Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Meal", color = colors.soft, style = BreakfastType.Meta)
                    Text(queueMeal.label(), color = colors.ink, style = BreakfastType.BodyMedium)
                }
                else if (!forLog) MealField(meal, { meal = it }, Modifier.padding(top = 16.dp), compact = true)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text("Result", color = colors.soft, style = BreakfastType.Meta)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(calculation?.takeIf { it.supported }?.let { formatKcal(it.nutrition.calories) } ?: "—", color = colors.ink, style = BreakfastType.MacroValue)
                        Text(" kcal", color = colors.soft, style = BreakfastType.Secondary)
                    }
                }
                if (forLog) PrimaryButton("Add", review::addToLog, Modifier.padding(top = 14.dp), enabled = canAdd)
                else PrimaryButton("Add to ${(queueMeal ?: meal).label()}", { review.add(queueMeal ?: meal) }, Modifier.padding(top = 14.dp), enabled = canAdd)
                if (queueMeal != null) review.resolveQueue()?.next?.let { next ->
                    Text("Next: $next", color = colors.soft, style = BreakfastType.Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            } else {
                PrimaryButton("Save to Library", review::saveToLibrary, Modifier.padding(top = 14.dp), enabled = canAdd)
            }
            val actions = listOf(
                (if (editing) "Hide details" else "Edit details") to { editing = !editing },
                "Find better match" to { review.findBetterMatch() },
                "Rescan" to { review.rescan() },
            )
            // One row when every label fits on one line in a third; otherwise stacked, never wrapped or cut.
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                val measurer = rememberTextMeasurer()
                val third = with(LocalDensity.current) { (maxWidth / 3 - 28.dp).roundToPx() }
                val fits = actions.all { (label, _) -> measurer.measure(label, BreakfastType.Button).size.width <= third }
                val editState = Modifier.semantics { stateDescription = if (editing) "Expanded" else "Collapsed" }
                if (fits) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    actions.forEachIndexed { index, (label, action) -> TertiaryButton(label, action, Modifier.weight(1f).then(if (index == 0) editState else Modifier)) }
                } else Column(Modifier.fillMaxWidth()) {
                    actions.forEachIndexed { index, (label, action) -> TertiaryButton(label, action, Modifier.fillMaxWidth().then(if (index == 0) editState else Modifier)) }
                }
            }
        }
    }
}

private class ScannerSession {
    val disposed = AtomicBoolean(false)
    @Volatile var provider: ProcessCameraProvider? = null
    @Volatile var preview: Preview? = null
    @Volatile var analysis: ImageAnalysis? = null
    @Volatile var analysisThread: ExecutorService? = null
    @Volatile var camera: Camera? = null
    @Volatile var previewBounds: Rect? = null
    @Volatile var viewfinderBounds: Rect? = null

    /** True when [box] lies inside the brackets grown by [feather]. Before the layout is known every code counts. */
    fun accepts(box: Rect, frame: ScanFrame, feather: Float): Boolean {
        val preview = previewBounds ?: return true
        val finder = viewfinderBounds ?: return true
        return barcodeInViewfinder(box, frame, preview.size, finder.translate(-preview.left, -preview.top), feather)
    }

    fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        analysis?.clearAnalyzer()
        val currentPreview = preview
        val currentAnalysis = analysis
        if (currentPreview != null && currentAnalysis != null) provider?.unbind(currentPreview, currentAnalysis)
        analysisThread?.shutdown()
    }
}

/**
 * Product symbologies only: a QR code (Wi-Fi details, a link with a token) is never decoded or sent.
 * Rotation and downscaling are tried, as a phone sees codes at any angle and up close. Inversion is not: it reads each
 * frame twice, for the rare food barcode printed in negative.
 */
private fun productReaderOptions() = BarcodeReader.Options(
    formats = setOf(BarcodeReader.Format.EAN_13, BarcodeReader.Format.EAN_8, BarcodeReader.Format.UPC_A, BarcodeReader.Format.UPC_E),
    tryHarder = true,
    tryRotate = true,
    tryInvert = false,
    tryDownscale = true,
)

/**
 * The one call into the decoder, on the analysis thread: the first product code inside the brackets (plus the feathering)
 * in [proxy], or null, which leaves the frame ignored and the scanner looking. Any other code in view does not count.
 */
private fun readProductBarcode(reader: BarcodeReader, proxy: ImageProxy, feather: Float, session: ScannerSession): String? {
    val frame = ScanFrame(proxy.width, proxy.height, proxy.imageInfo.rotationDegrees, proxy.cropRect.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) })
    return reader.read(proxy).firstNotNullOfOrNull { result ->
        val raw = result.text ?: return@firstNotNullOfOrNull null
        val corners = result.position.run { listOf(topLeft, topRight, bottomRight, bottomLeft) }.map { Offset(it.x.toFloat(), it.y.toFloat()) }
        if (!session.accepts(uprightBox(corners, frame), frame, feather)) null
        else scannedProductBarcode(raw, upcE = result.format == BarcodeReader.Format.UPC_E)
    }
}

/** Where the brackets rest above the review sheet: 96dp from the top, as M-Scan-Review draws them. */
private val ReviewViewfinderTop = 96.dp

/** The 32dp a barcode may reach past the brackets and still count. */
private val ScanFeather = 32.dp

/** One analysis frame: its buffer size, how far it is rotated to stand upright, and the part the preview shows (unrotated). */
internal data class ScanFrame(val width: Int, val height: Int, val rotation: Int, val crop: Rect)

/**
 * True when [box], a box in the upright frame, maps onto the preview inside [viewfinder] grown by [feather].
 * The preview fills its [previewSize] centre-cropped, as PreviewView does; [viewfinder] is in preview pixels.
 * The decoder's box for a linear code is as long as its bars but only as tall as the rows it read, so the box's long
 * side, the bars, must lie wholly inside; across them only the centre of the rows it read must.
 */
internal fun barcodeInViewfinder(box: Rect, frame: ScanFrame, previewSize: Size, viewfinder: Rect, feather: Float): Boolean {
    val shown = uprightRect(frame.crop, frame.width, frame.height, frame.rotation)
    if (shown.width <= 0f || shown.height <= 0f) return true
    val scale = maxOf(previewSize.width / shown.width, previewSize.height / shown.height)
    val dx = (previewSize.width - shown.width * scale) / 2f - shown.left * scale
    val dy = (previewSize.height - shown.height * scale) / 2f - shown.top * scale
    val mapped = Rect(box.left * scale + dx, box.top * scale + dy, box.right * scale + dx, box.bottom * scale + dy)
    val allowed = viewfinder.inflate(feather)
    val barsLieAcross = mapped.width >= mapped.height
    return if (barsLieAcross) mapped.left >= allowed.left && mapped.right <= allowed.right && mapped.center.y in allowed.top..allowed.bottom
    else mapped.top >= allowed.top && mapped.bottom <= allowed.bottom && mapped.center.x in allowed.left..allowed.right
}

/**
 * The box around a code's [corners] in the upright frame. The decoder reads only the crop, turned upright, so its
 * corners count from the corner of the crop; [barcodeInViewfinder] counts from the corner of the whole upright frame.
 */
internal fun uprightBox(corners: List<Offset>, frame: ScanFrame): Rect {
    val shown = uprightRect(frame.crop, frame.width, frame.height, frame.rotation)
    return Rect(corners.minOf { it.x } + shown.left, corners.minOf { it.y } + shown.top, corners.maxOf { it.x } + shown.left, corners.maxOf { it.y } + shown.top)
}

/** [rect] in a [width] x [height] buffer, turned by [rotation] degrees clockwise into the upright frame. */
internal fun uprightRect(rect: Rect, width: Int, height: Int, rotation: Int): Rect = when (rotation) {
    90 -> Rect(height - rect.bottom, rect.left, height - rect.top, rect.right)
    180 -> Rect(width - rect.right, height - rect.bottom, width - rect.left, height - rect.top)
    270 -> Rect(rect.top, width - rect.right, rect.bottom, width - rect.left)
    else -> rect
}
