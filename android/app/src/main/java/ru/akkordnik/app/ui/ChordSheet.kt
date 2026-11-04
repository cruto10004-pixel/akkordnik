package ru.akkordnik.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.akkordnik.app.data.ChordDef
import ru.akkordnik.app.music.canon
import ru.akkordnik.app.music.customFor
import ru.akkordnik.app.music.fmt
import ru.akkordnik.app.music.voicing

private var lastMode = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordSheet(app: AppState, name: String, all: List<String>, onClose: () -> Unit, onSwitch: (String) -> Unit, onEdit: (String) -> Unit) {
    val p = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun hide(then: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { then() } }
    val db = app.store.db
    val v = remember(name, db.chords) { voicing(name) }
    val photo = v?.photo ?: remember(name, db.chords) { customFor(name)?.photo }
    var mode by remember { mutableIntStateOf(lastMode) }
    if (mode == 2 && photo == null && v != null) mode = 0
    var dir by remember { mutableIntStateOf(1) }
    val i = all.indexOf(name)
    val pick = rememberPhotoPicker(app) { img ->
        val k = canon(name)
        val base = customFor(name)
        val def = if (base?.frets != null) base.copy(photo = img)
        else ChordDef(v?.frets, v != null && v.barre > 0, img)
        app.store.update { it.copy(chords = it.chords + (k to def)) }
        app.toast.show("Фото сохранено")
        mode = 2; lastMode = 2
    }
    ModalBottomSheet(
        onDismissRequest = onClose, sheetState = state, sheetMaxWidth = 500.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), containerColor = p.surface, contentColor = p.text,
        scrimColor = Color.Black.copy(alpha = .55f), tonalElevation = 0.dp,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(42.dp, 5.dp).clip(RoundedCornerShape(5.dp)).background(p.line)) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 22.dp)) {
            AnimatedContent(name, transitionSpec = {
                (slideInHorizontally(tween(340, easing = EaseApp)) { it / 3 * dir } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(340, easing = EaseApp)) { -it / 3 * dir } + fadeOut(tween(160)))
            }, label = "cname", modifier = Modifier.fillMaxWidth()) { n ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val vv = voicing(n)
                    GradText(fmt(n), display(38.sp, 800, 1.05f), align = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    val note = when {
                        vv == null -> "Аппликатура пока неизвестна — добавьте свою"
                        vv.custom -> "Ваша аппликатура"
                        vv.simplified -> "Упрощённо как " + fmt(vv.key)
                        vv.barre > 0 -> "Баррэ на ${vv.barre} ладу"
                        else -> "Открытая позиция"
                    }
                    T(note, body(14.sp, 500, 1.4f), p.muted, align = TextAlign.Center)
                }
            }
            if (v != null || photo != null) {
                if (v != null) {
                    Spacer(Modifier.height(16.dp))
                    Segmented(listOf("Схема", "Гриф", "Фото"), mode, { mode = it; lastMode = it })
                }
                Spacer(Modifier.height(8.dp))
                val maxPhoto = (LocalConfiguration.current.screenHeightDp * .52f).dp
                Box(Modifier.fillMaxWidth().heightIn(min = 290.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(name to (if (v == null) 2 else mode), transitionSpec = {
                        (fadeIn(tween(300, easing = EaseApp)) + scaleIn(tween(350, easing = EaseApp), .96f)) togetherWith fadeOut(tween(120))
                    }, label = "viz", contentAlignment = Alignment.Center) { (n, m) ->
                        val vv = remember(n, db.chords) { voicing(n) }
                        val ph = vv?.photo ?: customFor(n)?.photo
                        when {
                            m == 0 && vv != null -> Diagram(vv, Modifier.width(230.dp))
                            m == 1 && vv != null -> Neck(vv, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)))
                            ph != null -> {
                                val bmp by rememberPhoto(ph)
                                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = maxPhoto).clip(RoundedCornerShape(16.dp)).background(p.bg), contentAlignment = Alignment.Center) {
                                    bmp?.let { Image(it, "Фото: как ставить $n", Modifier.fillMaxWidth().heightIn(max = maxPhoto), contentScale = ContentScale.Fit) } ?: Spinner()
                                }
                            }
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Ic.camera, null, Modifier.size(28.dp), tint = p.muted)
                                Spacer(Modifier.height(10.dp))
                                T("Фото ещё нет.\nСфотографируйте, как вы ставите этот аккорд.", body(15.sp, 500, 1.5f), p.muted, align = TextAlign.Center)
                            }
                        }
                    }
                }
            } else Spacer(Modifier.height(18.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn(if (photo != null) "Новое фото" else "Своё фото", pick, Modifier.weight(1f), Ic.camera, BtnKind.Ghost, 46.dp, fontSize = 15, padH = 12.dp)
                Btn(if (v != null) "Изменить" else "Создать", { hide { onEdit(canon(name)) } }, Modifier.weight(1f), Ic.edit, BtnKind.Ghost, 46.dp, fontSize = 15, padH = 12.dp)
            }
            if (all.size > 1 && i >= 0) {
                val prev = all[(i - 1 + all.size) % all.size]
                val next = all[(i + 1) % all.size]
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NavBtn(fmt(prev), true, Modifier.weight(1f)) { dir = -1; onSwitch(prev) }
                    NavBtn(fmt(next), false, Modifier.weight(1f)) { dir = 1; onSwitch(next) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Btn("Закрыть", { hide(onClose) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NavBtn(label: String, left: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.tap(haptic = true, onClick = onClick).height(50.dp).clip(shape).background(p.surface).border(1.dp, p.line, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (left) { Icon(Ic.left, null, Modifier.size(22.dp), tint = p.text); Spacer(Modifier.width(8.dp)) }
        T(label, mono(16.sp, 800, 1.1f), p.accent, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
        if (!left) { Spacer(Modifier.width(8.dp)); Icon(Ic.right, null, Modifier.size(22.dp), tint = p.text) }
    }
}
