package ru.akkordnik.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

data class DBtn(val label: String, val value: String?, val kind: BtnKind = BtnKind.Grad, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null)
class DialogReq(val title: String, val text: String, val buttons: List<DBtn>, val onResult: (String?) -> Unit)

class Dialogs {
    var cur by mutableStateOf<DialogReq?>(null)
    fun ask(title: String, text: String, buttons: List<DBtn>, onResult: (String?) -> Unit) { cur = DialogReq(title, text, buttons, onResult) }
}

@Composable
fun DialogHost(d: Dialogs) {
    val req = d.cur ?: return
    val p = LocalPal.current
    val motion = LocalMotion.current
    val anim = remember(req) { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(req) { anim.animateTo(1f, spring(dampingRatio = .6f, stiffness = 500f)) }
    var closing by remember(req) { mutableStateOf(false) }
    fun close(v: String?) {
        if (closing) return
        closing = true
        scope.launch {
            if (motion) anim.animateTo(0f, tween(140))
            d.cur = null
            req.onResult(v)
        }
    }
    Dialog(onDismissRequest = { close(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(22.dp)
        Column(
            Modifier.padding(20.dp).widthIn(max = 380.dp).fillMaxWidth()
                .graphicsLayer { val s = .9f + .1f * anim.value; scaleX = s; scaleY = s; alpha = anim.value.coerceIn(0f, 1f) }
                .clip(shape).background(p.surface).border(1.dp, p.line, shape).padding(22.dp)
        ) {
            T(req.title, display(19.sp, 700, 1.3f))
            Spacer(Modifier.height(6.dp))
            T(req.text, body(16.sp, 500, 1.5f), p.muted)
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                req.buttons.forEach { b -> Btn(b.label, { close(b.value) }, Modifier.fillMaxWidth(), icon = b.icon, kind = b.kind) }
            }
        }
    }
}
