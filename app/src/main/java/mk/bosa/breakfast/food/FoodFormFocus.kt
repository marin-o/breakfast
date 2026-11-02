package mk.bosa.breakfast.food


/** A request to focus [field]; [serial] makes a repeated refusal of the same field a new request. */
internal data class FoodFormFocus(val field: FoodFormField, val serial: Long)
