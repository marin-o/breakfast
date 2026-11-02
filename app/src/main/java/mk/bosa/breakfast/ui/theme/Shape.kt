package mk.bosa.breakfast.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FieldShape = RoundedCornerShape(12.dp)
val ChoiceShape = RoundedCornerShape(12.dp)
val SecondaryShape = RoundedCornerShape(14.dp)
val ToastShape = RoundedCornerShape(14.dp)
val PrimaryShape = RoundedCornerShape(16.dp)
val ComposerShape = RoundedCornerShape(16.dp)
val SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
val DialogShape = RoundedCornerShape(20.dp)
val AddCircleShape = CircleShape

val BreakfastShapes = Shapes(
    extraSmall = FieldShape, small = FieldShape, medium = SecondaryShape,
    large = PrimaryShape, extraLarge = DialogShape,
)
