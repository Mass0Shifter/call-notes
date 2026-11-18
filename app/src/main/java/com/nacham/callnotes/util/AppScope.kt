package com.nacham.callnotes.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-lifetime scope for fire-and-forget work that must outlive a Service (e.g. auto-process). */
object AppScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
