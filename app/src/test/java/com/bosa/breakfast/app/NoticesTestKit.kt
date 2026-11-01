package com.bosa.breakfast.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation

/** [Notices] whose time never ends, for the owners' tests: a message stays until it is dismissed or replaced. */
internal fun Notices(): Notices = Notices(CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), sleep = { awaitCancellation() })
