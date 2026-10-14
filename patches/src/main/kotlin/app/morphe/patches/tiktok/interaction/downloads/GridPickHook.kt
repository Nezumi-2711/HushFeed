/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructions
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method

internal const val VIEW_HOLDER = "Landroidx/recyclerview/widget/RecyclerView\$ViewHolder;"
private const val GRID_PICKER = "Lapp/morphe/extension/tiktok/download/GridPicker;"
private const val GRID_AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"

/** How far up the holder's superclasses RecyclerView.ViewHolder is looked for. */
private const val HOLDER_DEPTH = 8

/**
 * Why the profile grid cell holder's bind can't take the picker's hook, or null when it can. The
 * bind is ProfileGridHolderBindFingerprint's method (X/0Oec.r6 on 47.1.4), which every profile,
 * Liked and Favorites cell runs through with the post it is about to draw first.
 *
 * <p>The hook reads the holder's `itemView` at the very top, so the bind has to be an instance
 * method of a RecyclerView.ViewHolder (the field is named, androidx keeps it), it has to take the
 * post as its first parameter, and it needs two locals of its own for the view and the post.
 */
internal fun Method.gridPickProblem(superclassOf: (String) -> String?): String? {
    if (AccessFlags.STATIC.isSet(accessFlags)) return "the grid holder's bind is static"
    if (parameterTypes.firstOrNull()?.toString() != GRID_AWEME) {
        return "the grid holder's bind doesn't take the cell's post first"
    }
    val body = implementation ?: return "the grid holder's bind has no code"
    // `this` plus each parameter, a long or a double taking two.
    val parameterRegisters = parameterTypes.fold(1) { total, type ->
        total + if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    if (body.registerCount - parameterRegisters < 2) {
        return "the grid holder's bind has fewer than two local registers"
    }
    var type: String? = definingClass
    repeat(HOLDER_DEPTH) {
        if (type == VIEW_HOLDER) return null
        type = type?.let(superclassOf) ?: return "the grid holder isn't a RecyclerView.ViewHolder"
    }
    return if (type == VIEW_HOLDER) null else "the grid holder isn't a RecyclerView.ViewHolder"
}

/**
 * Hands each grid cell's root view and post to the picker before TikTok draws the post (#27). It
 * adds two views of its own over the cell when the switch is on and never touches TikTok's
 * listeners, so off or paused the cell is TikTok's as it was.
 */
internal fun MutableMethod.hookGridPick(superclassOf: (String) -> String?) {
    gridPickProblem(superclassOf)?.let { throw PatchException("Advanced downloads: $it.") }
    addInstructions(
        0,
        """
            iget-object v0, p0, $VIEW_HOLDER->itemView:Landroid/view/View;
            move-object/from16 v1, p1
            invoke-static { v0, v1 }, $GRID_PICKER->bind(Landroid/view/View;Ljava/lang/Object;)V
        """,
    )
}
