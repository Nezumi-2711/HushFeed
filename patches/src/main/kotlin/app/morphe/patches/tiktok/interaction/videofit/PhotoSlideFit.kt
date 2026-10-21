/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.interaction.videofit

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal const val SCALE_TYPE = "Landroid/widget/ImageView\$ScaleType;"
private const val VIEW = "Landroid/view/View;"
private const val CONSTRAINT_LAYOUT = "Landroidx/constraintlayout/widget/ConstraintLayout;"

/**
 * The photo post's choice of how a slide sits in its view. The photo feed cell asks one static
 * helper for each slide: the slide's width over its height and a layout variant go in, and a
 * scale type comes out, crop to cover for a slide taller than about 9:16 and fit for the rest.
 * The helper lives in an obfuscated utility class, so it is found by its shape, which is the
 * only static method in the app that takes a float, answers a scale type and picks between
 * exactly those two.
 */
internal object SlideScaleTypeFingerprint : Fingerprint(
    returnType = SCALE_TYPE,
    parameters = listOf("F", "L"),
    custom = { method, _ -> method.isSlideScaleChoice() },
)

/**
 * The same question asked again once the cell goes full screen, where a slide that is taller
 * than its container is cropped to cover it. An instance method on the cell's layout helper that
 * takes the slide's aspect and answers whether to crop: it reads the display's width and the
 * container's height and holds a ConstraintLayout, and nothing else in the app has that shape.
 */
internal object SlideContainerCropFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("F"),
    custom = { method, _ -> method.isSlideContainerCrop() },
)

internal fun Method.isSlideScaleChoice(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return false
    if (parameterTypes.size != 2 || parameterTypes[0] != "F") return false
    val chosen = implementation?.instructions
        ?.filter { it.opcode == Opcode.SGET_OBJECT }
        ?.mapNotNull { it.getReference<FieldReference>() }
        ?.filter { it.type == SCALE_TYPE }
        ?.map { it.name }
        ?.toSet() ?: return false
    return chosen == setOf("CENTER_CROP", "FIT_CENTER")
}

internal fun Method.isSlideContainerCrop(): Boolean {
    if (AccessFlags.STATIC.isSet(accessFlags)) return false
    if (parameterTypes.size != 1 || parameterTypes[0] != "F") return false
    val body = implementation ?: return false
    val calls = body.instructions.mapNotNull { it.getReference<MethodReference>() }.map { it.name }.toSet()
    if ("getDisplayMetrics" !in calls || "getHeight" !in calls) return false
    return body.instructions.any {
        it.opcode == Opcode.INSTANCE_OF && it.getReference<TypeReference>()?.type == CONSTRAINT_LAYOUT
    }
}

/**
 * The view field the crop question reads the container's height from: the one object field of
 * type View the method reads off its own instance. Null when there is not exactly one, which the
 * patch refuses rather than guess.
 */
internal fun Method.containerViewField(): FieldReference? {
    val reads = implementation?.instructions
        ?.filter { it.opcode == Opcode.IGET_OBJECT }
        ?.mapNotNull { it.getReference<FieldReference>() }
        ?.filter { it.definingClass == definingClass && it.type == VIEW }
        ?.distinctBy { it.name }
        ?: return null
    return reads.singleOrNull()
}
