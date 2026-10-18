package app.morphe.patches.tiktok.interaction.searchsuggestions

import app.morphe.Fixtures
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHintObserverTest {
    /**
     * The observer that puts the trending word in the search box is one method on every declared
     * build, with a local beyond its parameters for the guard's own answer.
     */
    @Test
    fun `the search hint observer stays one method on every declared build`() = Fixtures.forEachDeclared { apk ->
        val container = Fixtures.dexContainer(apk, Opcodes.getDefault())
        val observers = container.dexEntryNames.asSequence()
            .flatMap { container.getEntry(it)!!.dexFile.classes.asSequence() }
            .flatMap { it.methods.asSequence() }
            .filter(::isSearchHintObserver).toList()
        assertEquals("search hint observers ${observers.map { "${it.definingClass}->${it.name}" }}", 1, observers.size)
        val observer = observers.single()
        val parameterRegisters = observer.parameterTypes.sumOf { if (it.toString() == "J" || it.toString() == "D") 2 else 1 } +
            if (AccessFlags.STATIC.isSet(observer.accessFlags)) 0 else 1
        assertTrue("${observer.definingClass}->${observer.name} has no local for the guard",
            observer.implementation!!.registerCount - parameterRegisters >= 1)
    }
}
