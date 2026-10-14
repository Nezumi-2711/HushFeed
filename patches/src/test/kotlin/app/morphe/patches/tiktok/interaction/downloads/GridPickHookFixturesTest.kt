/*
 * Copyright 2026 Hushfeed contributors
 * https://github.com/SysAdminDoc/hushfeed
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.Fixtures
import app.morphe.patches.tiktok.interaction.engagement.ProfileGridHolderBindFingerprint
import app.morphe.takes
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The grid picker (#27) hooks the profile grid cell holder's bind at its top, reading the
 * holder's itemView. Held to each declared build: the bind the count hook resolves takes the
 * picker's shape checks, its holder reaches RecyclerView.ViewHolder, and that class names the
 * itemView field the injected iget reads. And the checks refuse what would crash: a method
 * without the post first, or a holder that isn't a ViewHolder.
 */
class GridPickHookFixturesTest {
    @Test
    fun `the grid holder bind takes the picker hook on every declared build`() {
        Fixtures.forEachDeclared { apk ->
            val version = Fixtures.versionOf(apk)
            val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
            val classes = container.dexEntryNames.asSequence()
                .flatMap { container.getEntry(it)!!.dexFile.classes.asSequence() }
                .associateBy { it.type }
            val binds = classes.values.flatMap { cls ->
                cls.methods.filter { ProfileGridHolderBindFingerprint.takes(it, cls) }
            }
            assertEquals("$version: grid holder binds", 1, binds.size)
            val bind = binds.single()
            assertNull("$version: ${bind.definingClass}->${bind.name}",
                bind.gridPickProblem { classes[it]?.superclass })

            // TikTok's own code reads the same field off the same holder, so it is there to read.
            val holder = classes.getValue(VIEW_HOLDER)
            assertNotNull("$version: ViewHolder has no itemView field",
                holder.fields.singleOrNull { it.name == "itemView" && it.type == "Landroid/view/View;" })
            val reads = classes.getValue(bind.definingClass).methods.flatMap { method ->
                method.implementation?.instructions.orEmpty().mapNotNull {
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)
                        ?.takeIf { field -> field.definingClass == VIEW_HOLDER && field.name == "itemView" }
                }
            }
            assertEquals("$version: the holder never reads itemView itself", true, reads.isNotEmpty())
        }
    }

    @Test
    fun `the shape checks refuse a method without the post and a holder that isn't one`() {
        Fixtures.forEachDeclared { apk ->
            val version = Fixtures.versionOf(apk)
            val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
            val classes = container.dexEntryNames.asSequence()
                .flatMap { container.getEntry(it)!!.dexFile.classes.asSequence() }
                .associateBy { it.type }
            val bind = classes.values.flatMap { cls ->
                cls.methods.filter { ProfileGridHolderBindFingerprint.takes(it, cls) }
            }.single()
            // A chain that never reaches ViewHolder.
            assertEquals("$version", "the grid holder isn't a RecyclerView.ViewHolder",
                bind.gridPickProblem { type -> if (type == bind.definingClass) "Ljava/lang/Object;" else null })
            // A method of the right class that isn't the bind: no post first.
            val other = classes.getValue(bind.definingClass).methods.first {
                it.implementation != null && it.parameterTypes.firstOrNull()?.toString() != "Lcom/ss/android/ugc/aweme/feed/model/Aweme;" &&
                    it.accessFlags and com.android.tools.smali.dexlib2.AccessFlags.STATIC.value == 0
            }
            assertEquals("$version", "the grid holder's bind doesn't take the cell's post first",
                other.gridPickProblem { classes[it]?.superclass })
        }
    }
}
